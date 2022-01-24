package user;

/**
 * @brief Exception to be thrown when the list of tags specified for a given user is too long (i.e. it has more than 5 elements).
 * @author Giacomo Trapani.
 */
public class TagListTooLongException extends Exception
{
	public TagListTooLongException(final String s) { super(s); }

	public TagListTooLongException() { super(); }
}