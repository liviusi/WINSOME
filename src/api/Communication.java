package api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Utility class used to handle every communication by the server and from the server.
 * @author Giacomo Trapani.
 */
public class Communication
{
	private Communication() { }

	/**
	 * Utility function used to receive and parse a valid message into a String. It is to be called if and only if the message expected follows the
	 * following syntax: CONCAT(LENGTH, STRING) with LENGTH representing the number of bytes to be read, STRING the message (to be read) and the both of them
	 * are expected to be properly encoded (STRING is to be encoded with US ASCII).
	 * @param src channel to read from.
	 * @param buffer used to read from the channel.
	 * @param dst used to store the message, it will be appended.
	 * @return amount of bytes read on success, -1 on failure.
	 * @throws IOException Refer to ReadableByteChannel read function.
	 * @throws NullPointerException if any of the parameters are null.
	 */
	public static int receiveMessage(SocketChannel src, ByteBuffer buffer, StringBuilder dst)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(src, "Source cannot be null.");
		Objects.requireNonNull(buffer, "Buffer cannot be null.");
		Objects.requireNonNull(dst, "Destination cannot be null.");
		int r = 0;
		int nRead = 0;
		int size = -1;
		do
		{
			r = src.read(buffer);
			if (r == -1) return r;
			nRead += r;
			if (size == -1 && nRead < Integer.BYTES) continue;
			buffer.flip();
			if (size == -1)
				size = buffer.getInt();
			dst.append(StandardCharsets.US_ASCII.decode(buffer).toString());
			buffer.clear();
		} while (nRead < size || size < 0);
		return nRead;
	}

	/**
	 * Utility function used to receive a valid message. It is to be called if and only if the message expected follows the following syntax:
	 * CONCAT(LENGTH, BYTES) with LENGTH representing the number of bytes to be read (which are expected to be properly encoded), BYTES the message (to be read).
	 * @param src channel to read from.
	 * @param buffer used when reading from the channel.
	 * @param dst used to store the message, it will be appended.
	 * @return amount of bytes read on success, -1 on failure.
	 * @throws IOException Refer to ReadableByteChannel read function.
	 * @throws NullPointerException if any of the parameters are null.
	 */
	public static int receiveBytes(SocketChannel src, ByteBuffer buffer, ByteArrayOutputStream dst)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(src, "Source cannot be null.");
		Objects.requireNonNull(buffer, "Buffer cannot be null.");
		Objects.requireNonNull(dst, "Destination cannot be null.");
		int r = 0;
		int nRead = 0;
		int size = -1;
		do
		{
			r = src.read(buffer);
			if (r == -1) return r;
			nRead += r;
			if (size == -1 && nRead < Integer.BYTES) continue;
			buffer.flip();
			if (size == -1)
				size = buffer.getInt();
			while (buffer.hasRemaining())
				dst.write(buffer.get());
			buffer.clear();
		} while (nRead < size || size < 0);
		return nRead;
	}

	/**
	 * Utility function used to send a valid message. Its structure will be CONCAT(LENGTH, BYTES) with LENGTH representing the number of bytes written
	 * (which are properly encoded), BYTES the message (to be read).
	 * @param dst channel to write to.
	 * @param buffer used to write to the channel.
	 * @param src bytes to be written on the channel.
	 * @throws IOException Refer to WritableByteChannel write.
	 */
	public static void send(SocketChannel dst, ByteBuffer buffer, byte[] src)
	throws IOException
	{
		buffer.putInt(src.length);
		buffer.put(src);
		buffer.flip();
		while (buffer.hasRemaining())
			dst.write(buffer);
	}
}
