package server.storage;

/**
 * @brief Exception to be thrown whenever an attempt is made to handle a request for an unregistered user.
 * @author Giacomo Trapani.
 */
public class NoSuchUserException extends Exception
{
	public NoSuchUserException(final String s) { super(s); }

	public NoSuchUserException() { super(); }
}