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
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import cryptography.Passwords;
import user.*;

public class UserMap implements UserRMIStorage, UserStorage
{
	private Map<String, User> usersBackedUp = null; // already backed up
	private Map<String, User> usersToBeBackedUp = null;
	private ReadWriteLock lock = null;
	private boolean flag = false;
	private static final String EMPTY_STRING = "";
	private static final int BUFFERSIZE = 1024;

	private static final String NULL_PARAM_ERROR = " cannot be null.";

	public UserMap()
	{
		usersBackedUp = new HashMap<>();
		usersToBeBackedUp = new HashMap<>();
		flag = false;
		lock = new ReentrantReadWriteLock();
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
		User u = null;

		if (username.isEmpty()) throw new UsernameNotValidException("Username" + EMPTY_ERROR);
		emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.US_ASCII), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password" + EMPTY_ERROR);
		u = new User(username, password, tags, salt);
		try
		{
			lock.writeLock().lock();
			if (usersToBeBackedUp.containsKey(username) || usersBackedUp.containsKey(username)) // username already exists
				throw new UsernameAlreadyExistsException("Username has already been taken.");
			usersToBeBackedUp.put(username, u);
		}
		finally { lock.writeLock().unlock(); }
	}

	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		String saltDecoded = null;
		User u = null;

		try
		{
			lock.readLock().lock();
			u = usersBackedUp.get(username);
			if (u == null) u = usersToBeBackedUp.get(username);
			if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
			saltDecoded = u.saltDecoded;
		}
		finally { lock.readLock().unlock(); }
		return saltDecoded;
	}

	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_PARAM_ERROR);
		Objects.requireNonNull(hashPassword, "Hashed password" + NULL_PARAM_ERROR);

		User u = null;

		try
		{
			lock.writeLock().lock();
			u = usersBackedUp.get(username);
			if (u == null) u = usersToBeBackedUp.get(username);
			if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
			u.login(clientID, hashPassword);
		}
		finally { lock.writeLock().unlock(); }
	}

	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_PARAM_ERROR);

		User u = null;

		try
		{
			lock.writeLock().lock();
			u = usersBackedUp.get(username);
			if (u == null) u = usersToBeBackedUp.get(username);
			if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
			u.logout(clientID);
		}
		finally { lock.writeLock().unlock(); }
	}

	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		User u = null;
		Set<Tag> uTags = null;
		User tmp = null;
		Set<Tag> tmpTags = null;
		int size = -1;

		try
		{
			lock.readLock().lock();
			u = usersBackedUp.get(username);
			if (u == null) u = usersToBeBackedUp.get(username);
			if (u == null) throw new NoSuchUserException(username + " has yet to sign up.");
			uTags = u.getTags();
			for (Entry<String, User> entry: usersBackedUp.entrySet())
			{
				if (entry.getKey().equals(username)) continue;
				tmp = entry.getValue();
				tmpTags = tmp.getTags();
				size = tmpTags.size();
				tmpTags.removeAll(uTags);
				if (size != tmpTags.size()) // there was at least a common tag
				{
					Set<String> tmpTagsNames = new HashSet<>();
					tmp.getTags().forEach(t -> tmpTagsNames.add(t.name));
					r.add(tmp.username + "\r\n" + String.join(", ", tmpTagsNames));
				}
			}
		}
		finally { lock.readLock().unlock(); }
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
		
		try
		{
			lock.writeLock().lock();
			followerUser = usersBackedUp.get(followerUsername);
			followedUser = usersBackedUp.get(followedUsername);
			if (followerUser == null)
				throw new NoSuchUserException(NO_USER_ERROR + followerUsername);
			if (followedUser == null)
				throw new NoSuchUserException(NO_USER_ERROR + followedUsername);
			result = followerUser.follow(followedUser);
		}
		finally { lock.writeLock().unlock(); }
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
				return f.getDeclaringClass() == User.class && f.getName().equals("following");
			}

			@Override
			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
		}).create();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		File tmp = new File("tmp-users.json");
		byte[] data = null;
		int i = 0;
		Path from = null;
		Path to = null;
		try
		{
			lock.writeLock().lock();
			if (usersToBeBackedUp.isEmpty()) return;
			if (!(usersBackedUp.isEmpty()) || flag)
			{
				flag = false;
				// delete last character
				try
				(
					final Scanner scanner = new Scanner(usersFile);
					final FileOutputStream fos = new FileOutputStream(tmp);
					final FileChannel c = fos.getChannel()
				)
				{
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
				}
				// replace file:
				from = tmp.toPath();
				to = usersFile.toPath();
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
				Files.delete(from);
			}
			try
			(
				final FileOutputStream fos = new FileOutputStream(usersFile, true);
				final FileChannel c = fos.getChannel()
			)
			{
				if (usersBackedUp.isEmpty()) writeChar(c, '[');
				else writeChar(c, ',');
				for (Iterator<User> it = usersToBeBackedUp.values().iterator(); it.hasNext(); i++)
				{
					User u = it.next();
					usersBackedUp.put(u.username, u);
					data = gson.toJson(u).getBytes();
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
			}
			usersToBeBackedUp = new HashMap<>();
		}
		finally { lock.writeLock().unlock(); }
	}

	public void backupFollowing(File followingFile)
	throws FileNotFoundException, IOException
	{
		Gson gson = new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(new ExclusionStrategy()
		{
			@Override
			public boolean shouldSkipField(FieldAttributes f)
			{
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
		try
		{
			lock.readLock().lock();
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
		finally { lock.readLock().unlock(); }
	}

	/**
	 *  Metodo per scrivere un singolo carattere sul canale.
	 *  @param channel riferimento al canale su cui scrivere
	 *  @param c il carattere da scrivere
	 */
	private static void writeChar(FileChannel channel, char c)
	throws IOException
	{
		CharBuffer charBuffer = CharBuffer.wrap(new char[]{c});
		ByteBuffer byteBuffer = StandardCharsets.US_ASCII.encode(charBuffer);
		// Leggo il contenuto del buffer e lo scrivo sul canale.
		channel.write(byteBuffer);
	}

	public static UserMap fromJSON(final File usersFile, final File followingFile)
	throws FileNotFoundException, IOException, IllegalArchiveException
	{
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
			try { map.usersBackedUp.put(username, new User(username, hashPassword, tags, saltDecoded)); }
			catch (InvalidTagException | TagListTooLongException doNotAddUser) { }
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
					try { map.handleFollowUser(username, s); }
					catch (NullPointerException | NoSuchUserException illegalJSON) { throw new IllegalArchiveException("The files to be parsed are not a valid storage."); }
				}
			}
			reader.endObject();
		}
		reader.endArray();
		reader.close();
		is.close();
		return map;
	}
}
