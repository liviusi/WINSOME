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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @brief Abstract class with utility methods for storage classes.
 * @author Giacomo Trapani.
 */
abstract class Storage
{
	private static final int BUFFERSIZE = 1024;
	private static final String NULL_ERROR = " cannot be null";

	/**
	 * @brief Creates a backup of a data structure and stores it inside given file which will be overwritten.
	 * @param <K> type of the keys of the data Map.
	 * @param <V> type of the values of the data Map.
	 * @param strategy cannot be null. It is used to specify whichever fields are not to be stored.
	 * @param fileToBeStoredIn cannot be null.
	 * @param data cannot be null.
	 * @throws IOException if I/O error(s) occur(s).
	 * @throws NullPointerException if any parameter is null.
	 */
	public static <K, V> void backupNonCached(final ExclusionStrategy strategy, final File fileToBeStoredIn, final Map<K, V> data)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(strategy, "Exclusion strategy" + NULL_ERROR);
		Objects.requireNonNull(fileToBeStoredIn, "File" + NULL_ERROR);
		Objects.requireNonNull(data, "Map" + NULL_ERROR);

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

	/**
	 * @brief Creates a backup of a data structure and stores it inside given file which will be overwritten.
	 * @param <T> type of the items of the data Collection.
	 * @param strategy cannot be null. It is used to specify whichever fields are not to be stored.
	 * @param fileToBeStoredIn cannot be null.
	 * @param data cannot be null.
	 * @throws IOException if I/O error(s) occur(s).
	 * @throws NullPointerException if any parameter is null.
	 */
	public static <T> void backupNonCached(final ExclusionStrategy strategy, final File fileToBeStoredIn, final Collection<T> data)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(strategy, "Exclusion strategy" + NULL_ERROR);
		Objects.requireNonNull(fileToBeStoredIn, "File" + NULL_ERROR);
		Objects.requireNonNull(data, "Map" + NULL_ERROR);

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

	/**
	 * @brief Creates a backup of a data structure and appends it to given file.
	 * @param <K> type of the keys of the data Maps.
	 * @param <V> type of the values of the data Maps.
	 * @param strategy cannot be null. It is used to specify whichever fields are not to be stored.
	 * @param fileToBeStoredIn cannot be null.
	 * @param backedUpData cannot be null.
	 * @param toBeBackedUpData cannot be null.
	 * @param firstBackupAndNonEmptyStorage should be toggled on if and only if this is the first backup and the storage is non-empty.
	 * @throws IOException if I/O error(s) occur(s).
	 * @throws NullPointerException if any parameter is null.
	 * @modifies
	 *  	backedUpData: POST(backedUpData) = PREV(backedUpData) U toBeBackedUpData.
	 *  	toBeBackedUpData: POST(toBeBackedUpData) = EMPTY_MAP with EMPTY_MAP denoting an empty map.
	 */
	public static <K, V> void backupCached(final ExclusionStrategy strategy, final File fileToBeStoredIn, final Map<K, V> backedUpData,
			Map<K,V> toBeBackedUpData, boolean firstBackupAndNonEmptyStorage)
	throws IOException
	{
		Objects.requireNonNull(strategy, "Exclusion strategy" + NULL_ERROR);
		Objects.requireNonNull(fileToBeStoredIn, "File" + NULL_ERROR);
		Objects.requireNonNull(backedUpData, "Map" + NULL_ERROR);
		Objects.requireNonNull(toBeBackedUpData, "Map" + NULL_ERROR);

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
	 * @param channel pointer to the channel to write on.
	 * @param c character to write.
	 * @throws IOException if I/O error(s) occur.
	 * @author Matteo Loporchio.
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
