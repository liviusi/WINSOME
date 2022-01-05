package user;

public class WrongCredentialsException extends Exception
{
	public WrongCredentialsException(final String s) { super(s); }

	public WrongCredentialsException() { super(); }
}