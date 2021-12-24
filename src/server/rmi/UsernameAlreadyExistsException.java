package server.rmi;

public class UsernameAlreadyExistsException extends Exception
{
	public UsernameAlreadyExistsException(final String s) { super(s); }

	public UsernameAlreadyExistsException() { super(); }
}