package server.storage;

/**
 * @brief Exception to be thrown when an attempt is made to look for a non-existing post.
 * @author Giacomo Trapani
 */
public class NoSuchPostException extends Exception
{
	public NoSuchPostException(final String s) { super(s); }

	public NoSuchPostException() { super(); }
}