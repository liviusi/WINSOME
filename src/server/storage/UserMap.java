package server.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import cryptography.Passwords;
import user.*;

/**
 * @brief User storage backed up by a hashmap. This class is thread-safe.
 * @author Giacomo Trapani
 */
public class UserMap implements UserRMIStorage, UserStorage
{
	/** Users already stored inside backup file. */
	private Map<String, User> usersBackedUp = null; // already backed up
	/** Users yet to be stored. */
	private Map<String, User> usersToBeBackedUp = null;
	/** Toggled on if this is the first backup and the storage has been recovered from a JSON file. */
	private boolean flag = false;
	/** Maps a tag to the set of the users currently interested in it. */
	private Map<Tag, Set<User>> interestsMap = null;
	/** Maps a user to the set of users currently following it. */
	private Map<User, Set<User>> followersMap = null;

	/** Empty string. */
	private static final String EMPTY_STRING = "";
	/** Default ByteBuffer size. */
	private static final int BUFFERSIZE = 1024;
	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_PARAM_ERROR = " cannot be null.";

	/** Default constructor. */
	public UserMap()
	{
		usersBackedUp = new ConcurrentHashMap<>();
		usersToBeBackedUp = new ConcurrentHashMap<>();
		flag = false;
		interestsMap = new ConcurrentHashMap<>();
		followersMap = new ConcurrentHashMap<>();
	}

	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(password, "Password" + NULL_PARAM_ERROR);
		Objects.requireNonNull(tags, "Tags" + NULL_PARAM_ERROR);
		Objects.requireNonNull(salt, "Salt" + NULL_PARAM_ERROR);

		final String EMPTY_ERROR = " cannot be empty.";
		String emptyStringHashed = null;
		final User u;

		if (username.isEmpty()) throw new UsernameNotValidException("Username" + EMPTY_ERROR);
		emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.US_ASCII), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password" + EMPTY_ERROR);
		u = new User(username, password, tags, salt);
		if (usersToBeBackedUp.containsKey(username) || usersBackedUp.containsKey(username)) // username already exists
			throw new UsernameAlreadyExistsException("Username has already been taken.");
		usersToBeBackedUp.put(username, u);
		u.getTags().forEach(t -> new HashSet<>(interestsMap.get(t)).add(u));
		followersMap.put(u, new HashSet<>());
	}

	public Set<String> recoverFollowers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
		
		return followersMap.get(u).stream().map(follower ->
				follower.username + "\r\n" +
				String.join(", ", follower.getTags().stream()
					.map(t -> t.name).collect(Collectors.toSet()))
			).collect(Collectors.toSet());
	}

	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		String saltDecoded = null;
		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
		saltDecoded = u.saltDecoded;

		return saltDecoded;
	}

	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_PARAM_ERROR);
		Objects.requireNonNull(hashPassword, "Hashed password" + NULL_PARAM_ERROR);

		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
		u.login(clientID, hashPassword);
	}

	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_PARAM_ERROR);

		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
		u.logout(clientID);
	}

	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
		u.getTags().forEach(t ->
			interestsMap.get(t).forEach(tUser ->
			{
				if (!tUser.username.equals(username))
				{
					r.add(tUser.username + "\r\n" +
						String.join(", ", tUser.getTags().stream().map(uTag -> uTag.name).collect(Collectors.toSet())));
				}
			})
		);

		return r;
	}

	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(followerUsername, "Follower user's username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(followedUsername, "Followed user's username" + NULL_PARAM_ERROR);

		final String NO_USER_ERROR = "No user could be found for given name: ";
		User followerUser = null;
		User followedUser = null;
		boolean result = false;

		followerUser = usersBackedUp.get(followerUsername);
		followedUser = usersBackedUp.get(followedUsername);
		if (followerUser == null)
			throw new NoSuchUserException(NO_USER_ERROR + followerUsername);
		if (followedUser == null)
			throw new NoSuchUserException(NO_USER_ERROR + followedUsername);
		result = followerUser.follow(followedUser);
		if (result)
		{
			if (followersMap.get(followedUser) == null)
				followersMap.put(followedUser, new HashSet<>());
			followersMap.get(followedUser).add(followerUser);
		}

		return result;
	}

	public void backupUsers(final File usersFile)
	throws FileNotFoundException, IOException
	{
		Gson gson = new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(new ExclusionStrategy()
		{
			@Override
			public boolean shouldSkipField(FieldAttributes f)
			{
				// skips "following" field specified inside User class.
				return f.getDeclaringClass() == User.class && f.getName().equals("following");
			}

			@Override
			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
		}).create();

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] data = null;
		Path from = null;
		Path to = null;
		Scanner scanner = null;
		FileOutputStream fos = null;
		FileChannel c = null;

		if (usersToBeBackedUp.isEmpty()) return;

		// the file is non-empty:
		// the closing square bracket is to be deleted.
		if (!(usersBackedUp.isEmpty()) || flag)
		{
			File copy = new File("copy-users.json");
			scanner = new Scanner(usersFile);
			fos = new FileOutputStream(copy);
			c = fos.getChannel();
			flag = false;
			while(scanner.hasNextLine())
			{
				String line = scanner.nextLine();
				if(!scanner.hasNextLine())
					line = line.substring(0, line.length() - 1) + "\n";
				else
					line = line + "\n";
				buffer.clear();
				data = line.getBytes(StandardCharsets.US_ASCII);
				buffer.put(data);
				buffer.flip();
				while (buffer.hasRemaining()) c.write(buffer);
			}
			scanner.close();
			// replace file:
			from = copy.toPath();
			to = usersFile.toPath();
			Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(from);
			c.close();
			fos.close();
		}

		usersFile.mkdirs();
		fos = new FileOutputStream(usersFile, true);
		c = fos.getChannel();

		if (usersBackedUp.isEmpty()) writeChar(c, '[');
		else writeChar(c, ',');

		int i = 0;
		for (Iterator<User> it = usersToBeBackedUp.values().iterator(); it.hasNext(); i++)
		{
			User u = it.next();
			usersBackedUp.put(u.username, u);
			data = gson.toJson(u).getBytes();
			buffer.flip(); buffer.clear();
			for (int offset = 0; offset < data.length; offset += BUFFERSIZE)
			{
				buffer.clear();
				buffer.put(data, offset, Math.min(BUFFERSIZE, data.length - offset));
				buffer.flip();
				while (buffer.hasRemaining()) c.write(buffer);
			}
			if (i < usersToBeBackedUp.size() - 1) writeChar(c, ',');
		}
		writeChar(c, ']');
		usersToBeBackedUp = new HashMap<>();

		c.close();
		fos.close();
	}

	public void backupFollowing(File followingFile)
	throws FileNotFoundException, IOException
	{
		Gson gson = new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(new ExclusionStrategy()
		{
			@Override
			public boolean shouldSkipField(FieldAttributes f)
			{
				// skips everything except username and following field
				return f.getDeclaringClass() == User.class && !f.getName().equals("following") &&
						!f.getName().equals("username");
			}

			@Override
			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
			
		}).create();

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		int i = 0;

		followingFile.mkdirs();
		try
		(
			final FileOutputStream fos = new FileOutputStream(followingFile, false);
			final FileChannel c = fos.getChannel()
		)
		{
			writeChar(c, '[');
			for (Iterator<User> it = usersBackedUp.values().iterator(); it.hasNext(); i++)
			{
				User u = it.next();
				final byte[] data = gson.toJson(u).getBytes();
				for (int offset = 0; offset < data.length; offset += BUFFERSIZE)
				{
					buffer.clear();
					buffer.put(data, offset, Math.min(BUFFERSIZE, data.length - offset));
					buffer.flip();
					while (buffer.hasRemaining()) c.write(buffer);
				}
				if (i < usersBackedUp.size() - 1) writeChar(c, ',');
			}
			writeChar(c, ']');
		}
	}

	/**
	 * @brief Instantiates UserMap given a backup of its users and one of their follows both written according to json syntax.
	 * @param usersFile cannot be null, must be a valid user backup.
	 * @param followingFile cannot be null, must be a valid backup of users' follows.
	 * @return Instantiated map on success.
	 * @throws FileNotFoundException FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws IllegalArchiveException if either usersFile or followingFile is not a valid archive (i.e. it does not follow json syntax or it does not
	 * this class' IR).
	 */
	public static UserMap fromJSON(final File usersFile, final File followingFile)
	throws FileNotFoundException, IOException, IllegalArchiveException
	{
		final String INVALID_STORAGE = "The files to be parsed are not a valid storage.";

		UserMap map = new UserMap();
		map.flag = true;

		InputStream is = new FileInputStream(usersFile);
		JsonReader reader = new JsonReader(new InputStreamReader(is));

		reader.setLenient(true);
		reader.beginArray();
		while (reader.hasNext())
		{
			reader.beginObject();
			String name = null;
			String username = null;
			String hashPassword = null;
			byte[] saltDecoded = null;
			Set<String> tags = null;
			User u = null;
			for (int i = 0; i < 4; i++)
			{
				name = reader.nextName();
				switch (name)
				{
					case "username":
						username = reader.nextString();
						break;

					case "hashPassword":
						hashPassword = reader.nextString();
						break;

					case "saltDecoded":
						saltDecoded = Base64.getDecoder().decode(reader.nextString());
						break;

					case "tags":
						reader.beginArray();
						tags = new HashSet<>();
						while (reader.hasNext())
						{
							reader.beginObject();
							String tmp = reader.nextName();
							if (!tmp.equals("name")) break;
							tags.add(reader.nextString());
							reader.endObject();
						}
						reader.endArray();
						break;

					default:
						reader.skipValue();
						break;
				}
			}
			reader.endObject();
			try { u = new User(username, hashPassword, tags, saltDecoded); }
			catch (InvalidTagException | TagListTooLongException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
			map.usersBackedUp.put(username, u);
			map.followersMap.put(u, new HashSet<>());
		}
		reader.endArray();
		reader.close();
		is.close();

		is = new FileInputStream(followingFile);
		reader = new JsonReader(new InputStreamReader(is));

		reader.setLenient(true);
		reader.beginArray();
		while (reader.hasNext())
		{
			reader.beginObject();
			String name = null;
			String username = null;
			Set<String> following = new HashSet<>();
			for (int i = 0; i < 2; i++)
			{
				name = reader.nextName();
				if (name.equals("username"))
					username = reader.nextString();
				else if (name.equals("following"))
				{
					reader.beginArray();
					while (reader.hasNext())
						following.add(reader.nextString());
					reader.endArray();
				}
				for (String s: following)
				{
					try
					{
						if (!map.handleFollowUser(username, s))
							throw new IllegalArchiveException(INVALID_STORAGE);
					}
					catch (NullPointerException | NoSuchUserException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
				}
			}
			reader.endObject();
		}
		reader.endArray();
		reader.close();
		is.close();

		for (User u: map.usersBackedUp.values())
		{
			for (Tag t: u.getTags())
			{
				if (map.interestsMap.get(t) == null)
					map.interestsMap.put(t, new HashSet<>());
				map.interestsMap.get(t).add(u);
			}
			for (String s: u.getFollowing())
			{
				User followed = map.usersBackedUp.get(s);
				if (map.followersMap.get(followed) == null)
					map.followersMap.put(followed, new HashSet<>());
				map.followersMap.get(followed).add(u);
			}
		}
		return map;
	}

	/**
	 * @brief Writes a single character onto the channel.
	 * @param channel pointer to the channel to write on
	 * @param c character to write
	 * @throws IOException if I/O error(s) occur.
	 * @author Matteo Loporchio
	 */
	private static void writeChar(FileChannel channel, char c)
	throws IOException
	{
		CharBuffer charBuffer = CharBuffer.wrap(new char[]{c});
		ByteBuffer byteBuffer = StandardCharsets.US_ASCII.encode(charBuffer);
		// Leggo il contenuto del buffer e lo scrivo sul canale.
		channel.write(byteBuffer);
	}
}
