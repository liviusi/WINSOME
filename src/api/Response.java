package api;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class Response<T>
{
	public final ResponseCode code;
	public final T body;

	private Response(ResponseCode code, T body)
	{
		this.code = code;
		this.body = body;
	}

	public static Response<String> parseAnswer(String str)
	{
		int code = -1;
		String[] parts = null;

		parts = str.split("\r\n");
		if (parts.length != 2) return null;
		try { code = Integer.parseInt(parts[0]); }
		catch (NumberFormatException e) { return null; }
		return new Response<String>(ResponseCode.fromCode(code), parts[1]);
	}

	public static Response<Set<String>> parseAnswer(byte[] bytes)
	{
		int i = 0;
		int code = -1;
		int strlen = -1;
		byte[] tmp = new byte[Integer.BYTES];
		ByteBuffer converter = ByteBuffer.allocate(Integer.BYTES);
		String s = null;
		StringBuilder sb = new StringBuilder();
		Set<String> body = new HashSet<>();

		while (true)
		{
			char c = (char) bytes[i];
			sb.append(c);
			i++;
			if (sb.toString().contains("\r\n"))
			{
				s = sb.toString();
				s = s.substring(0, s.indexOf("\r\n")).trim();
				break;
			}
		}
		try { code = Integer.parseInt(s); }
		catch (NumberFormatException e) { e.printStackTrace(); return null; }
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
				body.add(str);
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
		return new Response<Set<String>>(ResponseCode.fromCode(code), body);
	}
}