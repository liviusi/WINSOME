package server.rmi;

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
import server.user.*;

public class UserMap implements UserRMIStorage, UserStorage
{
	private Map<String, User> usersBackedUp = null; // already backed up
	private Map<String, User> usersToBeBackedUp = null;
	private ReadWriteLock lock = null;
	private boolean flag = false;
	private static final String EMPTY_STRING = "";
	private static final int BUFFERSIZE = 1024;

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
		if (username == null || password == null || tags == null || salt == null) throw new NullPointerException("Parameters cannot be null");
		if (username.isEmpty()) throw new UsernameNotValidException("Username cannot be empty.");
		String emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.US_ASCII), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password cannot be empty.");
		User u = new User(username, password, tags, salt);
		try
		{
			lock.writeLock().lock();
			if (usersToBeBackedUp.containsKey(username) || usersBackedUp.containsKey(username)) // username already exists
				throw new UsernameAlreadyExistsException("Username has already been taken.");
			usersToBeBackedUp.put(username, u);
		}
		finally { lock.writeLock().unlock(); }
	}

	public static UserMap fromJSON(final File usersFile, final File followingFile)
	throws FileNotFoundException, IOException
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
					map.addFollower(username, s);
			}
			reader.endObject();
		}
		reader.endArray();
		reader.close();
		is.close();
		return map;
	}

	public User getUser(final String username)
	{
		User u = null;
		try
		{
			lock.readLock().lock();
			u = usersBackedUp.get(username);
			if (u == null)
				u = usersToBeBackedUp.get(username);
		}
		finally { lock.readLock().unlock(); }
		return u;
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

	public Set<String> getAllUsersWithSameInterestsAs(final String username)
	{
		Set<String> r = new HashSet<>();
		User u = null;
		try
		{
			lock.readLock().lock();
			u = usersBackedUp.get(username);
			if (u == null) return r;
			Set<Tag> uTags = u.getTags();
			for (Entry<String, User> entry: usersBackedUp.entrySet())
			{
				if (entry.getKey().equals(username)) continue;
				User tmp = entry.getValue();
				Set<Tag> tmpTags = tmp.getTags();
				int size = tmpTags.size();
				tmpTags.removeAll(uTags);
				if (size != tmpTags.size()) // there was at least a common tag
				{
					Set<String> tmpSet = new HashSet<>();
					tmp.getTags().forEach(t -> tmpSet.add(t.name));
					r.add(tmp.username + "\r\n" + String.join(", ", tmpSet));
				}
			}
		}
		finally { lock.readLock().unlock(); }
		return r;
	}

	public boolean addFollower(final String followerUsername, final String followedUsername)
	{
		final String NULL_PARAM_ERROR = " user's username cannot be null.";
		final String NO_USER_ERROR = "No user could be found for given name: ";
		User followerUser = null;
		User followedUser = null;
		boolean result = false;
		
		try
		{
			lock.writeLock().lock();
			followerUser = usersBackedUp.get(Objects.requireNonNull(followerUsername, "Follower" + NULL_PARAM_ERROR));
			followedUser = usersBackedUp.get(Objects.requireNonNull(followedUsername, "Followed" + NULL_PARAM_ERROR));
			if (followerUser == null)
				throw new IllegalArgumentException(NO_USER_ERROR + followerUsername);
			if (followedUser == null)
				throw new IllegalArgumentException(NO_USER_ERROR + followedUsername);
			result = followerUser.follow(followedUser);
		}
		finally { lock.writeLock().unlock(); }
		return result;
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
}
