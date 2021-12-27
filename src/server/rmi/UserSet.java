package server.rmi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cryptography.Passwords;
import server.user.*;

public class UserSet implements UserStorage
{
	private Set<User> users = null;
	private final static String EMPTY_STRING = "";
	private final static int BUFFERSIZE = 1024;

	public UserSet()
	{
		users = ConcurrentHashMap.newKeySet();
	}

	public boolean register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		if (username == null || password == null || tags == null || salt == null) throw new NullPointerException("Parameters cannot be null");
		if (username.isEmpty()) throw new UsernameNotValidException("Username cannot be empty.");
		String emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.UTF_8), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password cannot be empty.");
		User u = new User(username, password, tags, salt);
		if (!users.add(u))
			throw new UsernameAlreadyExistsException("Username has already been taken.");
		return true;
	}

	public static UserSet fromJSON(File file)
	{
		return null;
	}

	public void backupUsers(File file)
	throws FileNotFoundException, IOException
	{
		if (users.isEmpty()) return;
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		try
		(
			final FileOutputStream fos = new FileOutputStream(file);
			final FileChannel c = fos.getChannel()
		)
		{
			int i = 0;
			for (Iterator<User> it = users.iterator(); it.hasNext(); i++)
			{
				User u = it.next();
				writeChar(c, '[');
				final byte[] data = gson.toJson(u).getBytes();
				for (int offset = 0; offset < data.length; offset += BUFFERSIZE)
				{
					buffer.clear();
					buffer.put(data, offset, Math.min(BUFFERSIZE, data.length - offset));
					buffer.flip();
					while (buffer.hasRemaining()) c.write(buffer);
				}
				if (i < users.size() - 1) writeChar(c, ',');
			}
			writeChar(c, ']');
		}
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
