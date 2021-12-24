package server.rmi;

public class UsernameNotValidException extends Exception
{
	public UsernameNotValidException(final String s) { super(s); }

	public UsernameNotValidException() { super(); }
}