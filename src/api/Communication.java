package api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

public class Communication
{
	private Communication() { }

	public static int receive(SocketChannel src, ByteBuffer buffer, StringBuilder dst)
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

	public static int receiveSet(SocketChannel src, ByteBuffer buffer, Set<String> dst)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(src, "SocketChannel cannot be null.");
		Objects.requireNonNull(buffer, "ByteBuffer cannot be null.");
		Objects.requireNonNull(dst, "Set cannot be null.");
		int r = 0;
		int nRead = 0;
		int size = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		do
		{
			r = src.read(buffer);
			if (r == -1) return r;
			nRead += r;
			if (size == -1 && nRead < Integer.BYTES) continue;
			buffer.flip();
			if (size == -1)
				size = buffer.getInt();
			baos.write(buffer.array());
			buffer.clear();
		} while (nRead < size || size < 0);
		byte[] bytes = baos.toByteArray();
		byte[] tmp = null;
		ByteBuffer converter = ByteBuffer.allocate(Integer.BYTES);
		StringBuilder sb = null;
		int strlen = -1;
		int i = 0;
		while (i < bytes.length)
		{
			if (strlen > 0)
			{
				sb = new StringBuilder();
				for (int j = 0; j < strlen; j++)
				{
					char c = (char) bytes[i + j];
					sb.append(c);
				}
				String str = sb.toString();
				dst.add(str);
				System.out.println(str);
				i += strlen;
				strlen = -1;
			}
			else
			{
				tmp = new byte[Integer.BYTES];
				for (int j = 0; j < Integer.BYTES; j++)
					tmp[j] = bytes[i + j];
				converter.put(tmp);
				converter.flip();
				strlen = converter.getInt();
				converter.flip();
				converter.clear();
				i += Integer.BYTES;
			}
		}
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
