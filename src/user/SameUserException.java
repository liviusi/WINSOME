package user;

/**
 * @brief Exception to be thrown when an attempt is made to have a user follow themselves.
 * @author Giacomo Trapani.
 */
public class SameUserException extends Exception
{
	public SameUserException(final String s) { super(s); }

	public SameUserException() { super(); }
}