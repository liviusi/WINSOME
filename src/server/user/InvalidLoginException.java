package server.user;

public class InvalidLoginException extends Exception
{
	public InvalidLoginException(final String s) { super(s); }

	public InvalidLoginException() { super(); }
}