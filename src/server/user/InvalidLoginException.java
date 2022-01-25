package server.user;

/**
 * Exception to be thrown when an attempt is made to have an already logged in user login.
 * @author Giacomo Trapani
 */
public class InvalidLoginException extends Exception
{
	public InvalidLoginException(final String s) { super(s); }

	public InvalidLoginException() { super(); }
}