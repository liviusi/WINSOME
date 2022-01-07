package user;

/**
 * @brief Exception to be thrown whenever an attempt is made to instantiate a new user with an invalid username.
 * @author Giacomo Trapani
 */
public class InvalidUsernameException extends Exception
{
	public InvalidUsernameException(final String s) { super(s); }

	public InvalidUsernameException() { super(); }
}