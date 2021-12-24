package server.rmi;

public class PasswordNotValidException extends Exception
{
	public PasswordNotValidException(final String s) { super(s); }

	public PasswordNotValidException() { super(); }
}