package user;

/**
 * @brief Exception to be thrown when a tag name does not contain any alphanumeric character.
 * @author Giacomo Trapani.
 */
public class InvalidTagException extends Exception
{
	public InvalidTagException(final String s) { super(s); }

	public InvalidTagException() { super(); }
}