package user;

/**
 * @brief Exception to be thrown when an attempt is made to login with wrong credentials.
 * @author Giacomo Trapani
 */
public class WrongCredentialsException extends Exception
{
	public WrongCredentialsException(final String s) { super(s); }

	public WrongCredentialsException() { super(); }
}