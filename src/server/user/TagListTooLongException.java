package server.user;

public class TagListTooLongException extends Exception
{
	public TagListTooLongException(final String s) { super(s); }

	public TagListTooLongException() { super(); }
}