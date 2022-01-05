package api;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * @brief Utility class used when parsing server's responses. Every response has a structure of this kind:
 * "CODE\r\nBODY" with CODE representing the response code and BODY its body (refer to the documentation for
 * further explanations of this syntax).
 * @author Giacomo Trapani
 */
public class Response<T>
{
	/** Holds response's code value. */
	public final ResponseCode code;
	/** Holds response's body. */
	public final T body;

	/** Constructor is made private to have its caller use the static methods provided. */
	private Response(ResponseCode code, T body)
	{
		this.code = code;
		this.body = body;
	}

	/**
	 * @brief Static factory method used to parse input string into a valid Response of which the body is a String.
	 * @param str String to be parsed, its format must be CODE\r\nBODY with CODE representing the response code
	 * and BODY its body.
	 * @return A new instantiated Response based on input string, null if the parsing fails.
	 */
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

	/**
	 * @brief Static factory method used to parse input bytes into a valid Response of which the body is a Set of Strings.
	 * @param bytes bytes to be parsed, its format must be CODE\r\nLENGTH_{1}STRING_{1}...LENGTH_{n}STRING{n}.
	 * @return A new instantiated Response based on input bytes, null if the parsing fails.
	 */
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

		// parsing response code:
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

		// parsing all the strings remaining
		while (i < bytes.length)
		{
			// build the string
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
				// convert bytes to following string's length
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