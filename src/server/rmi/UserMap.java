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
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import cryptography.Passwords;
import server.user.*;

public class UserMap implements UserRMIStorage, UserStorage
{
	private Map<String, User> users = null; // already backed up
	private Map<String, User> toBeBackedUp = null;
	private ReadWriteLock lock = null;
	private boolean flag = false;
	private final static String EMPTY_STRING = "";
	private final static int BUFFERSIZE = 1024;

	public UserMap()
	{
		users = new HashMap<>();
		toBeBackedUp = new HashMap<>();
		flag = false;
		lock = new ReentrantReadWriteLock();
	}

	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		if (username == null || password == null || tags == null || salt == null) throw new NullPointerException("Parameters cannot be null");
		if (username.isEmpty()) throw new UsernameNotValidException("Username cannot be empty.");
		String emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.UTF_8), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password cannot be empty.");
		User u = new User(username, password, tags, salt);
		try
		{
			lock.writeLock().lock();
			if (toBeBackedUp.containsKey(username) || users.containsKey(username)) // username already exists
				throw new UsernameAlreadyExistsException("Username has already been taken.");
			toBeBackedUp.put(username, u);
		}
		finally { lock.writeLock().unlock(); }
	}

	public static UserMap fromJSON(File file) throws FileNotFoundException, IOException
	{
		UserMap map = new UserMap();
		map.flag = true;
		InputStream is = new FileInputStream(file);
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
			try { map.users.put(username, new User(username, hashPassword, tags, saltDecoded)); }
			catch (InvalidTagException | TagListTooLongException doNotAddUser) { }
		}
		reader.endArray();
		reader.close();
		return map;
	}

	public User getUser(String username)
	{
		User u = null;
		try
		{
			lock.readLock().lock();
			u = users.get(username);
			if (u == null)
				u = toBeBackedUp.get(username);
		}
		finally { lock.readLock().unlock(); }
		return u;
	}

	public void backupUsers(File file)
	throws FileNotFoundException, IOException
	{
		try
		{
			lock.writeLock().lock();
			if (toBeBackedUp.isEmpty()) return;
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
			if (!(users.isEmpty()) || flag)
			{
				flag = false;
				// delete last character
				File tmp = new File("tmp-users.json");
				try
				(
					final Scanner scanner = new Scanner(file);
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
						final byte[] data = line.getBytes(StandardCharsets.UTF_8);
						buffer.put(data);
						buffer.flip();
						while (buffer.hasRemaining()) c.write(buffer);
					}
				}
				// replace file:
				Path from = tmp.toPath();
				Path to = file.toPath();
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
				Files.delete(from);
			}
			try
			(
				final FileOutputStream fos = new FileOutputStream(file, true);
				final FileChannel c = fos.getChannel()
			)
			{
				int i = 0;
				if (users.isEmpty()) writeChar(c, '[');
				else writeChar(c, ',');
				for (Iterator<User> it = toBeBackedUp.values().iterator(); it.hasNext(); i++)
				{
					User u = it.next();
					System.out.println(u + " username: " + u.username);
					users.put(u.username, u);
					final byte[] data = gson.toJson(u).getBytes();
					for (int offset = 0; offset < data.length; offset += BUFFERSIZE)
					{
						buffer.clear();
						buffer.put(data, offset, Math.min(BUFFERSIZE, data.length - offset));
						buffer.flip();
						while (buffer.hasRemaining()) c.write(buffer);
					}
					if (i < toBeBackedUp.size() - 1) writeChar(c, ',');
				}
				writeChar(c, ']');
			}
			toBeBackedUp = new HashMap<>();
		}
		finally { lock.writeLock().unlock(); }
	}

	public Set<String> getAllUsersWithSameInterestsAs(String username)
	{
		Set<String> r = new HashSet<>();
		User u = null;
		try
		{
			lock.readLock().lock();
			u = users.get(username);
			if (u == null) return r;
			Set<String> uTags = u.getTags();
			for (Entry<String, User> entry: users.entrySet())
			{
				if (entry.getKey().equals(username)) continue;
				User tmp = entry.getValue();
				Set<String> tmpTags = tmp.getTags();
				int size = tmpTags.size();
				tmpTags.removeAll(uTags);
				if (size != tmpTags.size()) // there was at least a common tag
					r.add(tmp.username);
			}
		}
		finally { lock.readLock().unlock(); }
		return r;
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
		ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
		// Leggo il contenuto del buffer e lo scrivo sul canale.
		channel.write(byteBuffer);
	}
}
