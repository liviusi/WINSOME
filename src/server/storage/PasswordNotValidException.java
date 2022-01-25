package server.storage;

/**
 * Exception to be thrown when a password is not valid (i.e. is an empty string).
 * @author Giacomo Trapani.
 */
public class PasswordNotValidException extends Exception
{
	public PasswordNotValidException(final String s) { super(s); }

	public PasswordNotValidException() { super(); }
}