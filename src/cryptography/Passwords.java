package cryptography;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * @brief Utility class used to handle cryptography on WINSOME both by clients and servers.
 * @author Giacomo Trapani.
 */
public class Passwords
{
	/** Used to generate random values. */
	private static SecureRandom RANDOM = new SecureRandom();
	/** Salt length. */
	private static final int SALT_LEN = 16;

	private Passwords() { }

	/** Converts a byte array to its string rappresentation. */
	private static String bytesToString(final byte[] bytes)
	{
		StringBuilder sb = new StringBuilder();
		for (byte b: bytes)
			sb.append(Integer.toString((b & 0xff) + 0x100, SALT_LEN).substring(1));
		return sb.toString();
	}

	/**
	 * @brief Computes the hash of a password combined with a salt.
	 * @param password cannot be null.
	 * @param salt cannot be null.
	 * @return the hash of the password and the salt converted to its String rappresentation.
	 */
	public static String hashPassword(final byte[] password, final byte[] salt)
	{
		Objects.requireNonNull(password, "Password cannot be null.");
		Objects.requireNonNull(salt, "Salt cannot be null.");
		String s = null;
		MessageDigest md = null;
		try { md = MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException neverThrown) { throw new IllegalStateException("Unexpected error occurred. " + neverThrown.getMessage()); }
		md.update(salt);
		s = bytesToString(md.digest(password));
		return s;
	}

	/** Generates a new random salt. */
	public static byte[] generateSalt()
	{
		byte[] salt = new byte[SALT_LEN];
		synchronized(RANDOM) { RANDOM.nextBytes(salt); }
		return salt;
	}

	/** Decodes a salt i.e. it transforms a salt from its String rappresentation to a byte array. */
	public static byte[] decodeSalt(final String saltEncoded)
	{
		return Base64.getDecoder().decode(saltEncoded);
	}

	/** Encodes a salt i.e. it transforms a salt from byte array to String. */
	public static String encodeSalt(final byte[] saltDecoded)
	{
		return Base64.getEncoder().encodeToString(saltDecoded);
	}
}
