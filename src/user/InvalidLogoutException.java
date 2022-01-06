package user;

/**
 * @brief Exception to be thrown when an attempt is made to have an already logged out
 * user logout.
 * @author Giacomo Trapani
 */
public class InvalidLogoutException extends Exception
{
	public InvalidLogoutException(final String s) { super(s); }

	public InvalidLogoutException() { super(); }
}