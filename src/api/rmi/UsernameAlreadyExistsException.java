package api.rmi;

/**
 * Exception to be thrown when a username is already taken.
 * @author Giacomo Trapani.
 */
public class UsernameAlreadyExistsException extends Exception
{
	public UsernameAlreadyExistsException(final String s) { super(s); }

	public UsernameAlreadyExistsException() { super(); }
}