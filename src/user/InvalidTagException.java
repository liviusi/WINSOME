package user;

public class InvalidTagException extends Exception
{
	public InvalidTagException(final String s) { super(s); }

	public InvalidTagException() { super(); }
}