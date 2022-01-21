package user;

public class SameUserException extends Exception
{
	public SameUserException(final String s) { super(s); }

	public SameUserException() { super(); }
}