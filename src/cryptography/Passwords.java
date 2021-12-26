package cryptography;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Passwords
{

	private static SecureRandom RANDOM = new SecureRandom();
	private static final int SALT_LEN = 16;

	private Passwords() { }

	private static String bytesToString(final byte[] bytes)
	{
		StringBuilder sb = new StringBuilder();
		for (byte b: bytes)
			sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
		return sb.toString();
	}

	public static String hashPassword(final byte[] password, final byte[] salt)
	{
		String s = null;
		MessageDigest md = null;
		try { md = MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException neverThrown) { throw new RuntimeException("Unexpected error occurred. " + neverThrown.getMessage()); }
		md.update(salt);
		s = bytesToString(md.digest(password));
		return s;
	}

	public static byte[] generateSalt()
	{
		byte[] salt = new byte[SALT_LEN];
		synchronized(RANDOM) { RANDOM.nextBytes(salt); }
		return salt;
	}
}
