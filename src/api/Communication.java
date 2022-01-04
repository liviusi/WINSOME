package api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Communication
{
	private Communication() { }

	public static int receiveMessage(SocketChannel src, ByteBuffer buffer, StringBuilder dst)
	throws IOException
	{
		Objects.requireNonNull(src, "SocketChannel cannot be null.");
		Objects.requireNonNull(buffer, "ByteBuffer cannot be null.");
		Objects.requireNonNull(dst, "StringBuilder cannot be null.");
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

	public static int receiveBytes(SocketChannel src, ByteBuffer buffer, ByteArrayOutputStream dst)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(src, "SocketChannel cannot be null.");
		Objects.requireNonNull(buffer, "ByteBuffer cannot be null.");
		Objects.requireNonNull(dst, "Set cannot be null.");
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
