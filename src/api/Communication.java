package api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class Communication
{
	private Communication() { }

	public static int receive(SocketChannel src, ByteBuffer buffer, StringBuilder dst)
	throws IOException
	{
		int r = 0;
		int nRead = 0;
		int size = -1;
		do
		{
			r = src.read(buffer);
			if (r == -1) return r;
			buffer.flip();
			if (size == -1)
				size = buffer.getInt();
			nRead += r;
			dst.append(StandardCharsets.UTF_8.decode(buffer).toString());
			buffer.clear();
		} while (nRead < size);
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
