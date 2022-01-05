package server.storage;

/**
 * @brief Exception to be thrown when a username does not contain any alphanumeric character.
 * @author Giacomo Trapani
 */
public class UsernameNotValidException extends Exception
{
	public UsernameNotValidException(final String s) { super(s); }

	public UsernameNotValidException() { super(); }
}