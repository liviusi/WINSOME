package server.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class Storage
{
	static final int BUFFERSIZE = 1024;

	public static <K, V> void backupNonCached(final ExclusionStrategy strategy, final File fileToBeStoredIn, final Map<K, V> data)
	throws IOException
	{
		Gson gson = new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(strategy).create();

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		int i = 0;

		fileToBeStoredIn.getParentFile().mkdirs();

		try
		(
			final FileOutputStream fos = new FileOutputStream(fileToBeStoredIn, false);
			final FileChannel c = fos.getChannel()
		)
		{
			writeChar(c, '[');
			for (Iterator<V> it = data.values().iterator(); it.hasNext(); i++)
			{
				V v = it.next();
				final byte[] bytes = gson.toJson(v).getBytes();
				for (int offset = 0; offset < bytes.length; offset += BUFFERSIZE)
				{
					buffer.clear();
					buffer.put(bytes, offset, Math.min(BUFFERSIZE, bytes.length - offset));
					buffer.flip();
					while (buffer.hasRemaining()) c.write(buffer);
				}
				if (i < data.size() - 1) writeChar(c, ',');
			}
			writeChar(c, ']');
		}
	}

	public static <T> void backupNonCached(final ExclusionStrategy strategy, final File fileToBeStoredIn, final Set<T> data)
	throws IOException
	{
		if (data.isEmpty()) return;

		Gson gson = new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(strategy).create();

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		int i = 0;

		fileToBeStoredIn.getParentFile().mkdirs();

		try
		(
			final FileOutputStream fos = new FileOutputStream(fileToBeStoredIn, false);
			final FileChannel c = fos.getChannel()
		)
		{
			writeChar(c, '[');
			for (Iterator<T> it = data.iterator(); it.hasNext(); i++)
			{
				T t = it.next();
				final byte[] bytes = gson.toJson(t).getBytes();
				for (int offset = 0; offset < bytes.length; offset += BUFFERSIZE)
				{
					buffer.clear();
					buffer.put(bytes, offset, Math.min(BUFFERSIZE, bytes.length - offset));
					buffer.flip();
					while (buffer.hasRemaining()) c.write(buffer);
				}
				if (i < data.size() - 1) writeChar(c, ',');
			}
			writeChar(c, ']');
		}
	}

	public static <K, V> void backupCached(final ExclusionStrategy strategy, final File fileToBeStoredIn, final Map<K, V> backedUpData,
			Map<K,V> toBeBackedUpData, boolean firstBackupAndNonEmptyStorage)
	throws IOException
	{
		if (toBeBackedUpData.isEmpty()) return;

		Gson gson = new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(strategy).create();
		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] data = null;
		Path from = null;
		Path to = null;
		Scanner scanner = null;
		FileOutputStream fos = null;
		FileChannel c = null;

		// the file is non-empty:
		// the closing square bracket is to be deleted.
		if (!(backedUpData.isEmpty()) || firstBackupAndNonEmptyStorage)
		{
			File copy = new File("copy-map.json");
			scanner = new Scanner(fileToBeStoredIn);
			fos = new FileOutputStream(copy);
			c = fos.getChannel();
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
			to = fileToBeStoredIn.toPath();
			Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(from);
			c.close();
			fos.close();
		}

		fileToBeStoredIn.getParentFile().mkdirs();
		fos = new FileOutputStream(fileToBeStoredIn, true);
		c = fos.getChannel();

		if (backedUpData.isEmpty()) writeChar(c, '[');
		else writeChar(c, ',');

		int i = 0;
		for (Iterator<V> it = toBeBackedUpData.values().iterator(); it.hasNext(); i++)
		{
			V v = it.next();
			data = gson.toJson(v).getBytes();
			buffer.flip(); buffer.clear();
			for (int offset = 0; offset < data.length; offset += BUFFERSIZE)
			{
				buffer.clear();
				buffer.put(data, offset, Math.min(BUFFERSIZE, data.length - offset));
				buffer.flip();
				while (buffer.hasRemaining()) c.write(buffer);
			}
			if (i < toBeBackedUpData.size() - 1) writeChar(c, ',');
		}
		writeChar(c, ']');
		backedUpData.putAll(toBeBackedUpData);

		c.close();
		fos.close();
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
