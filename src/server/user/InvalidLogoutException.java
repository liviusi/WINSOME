package server.user;

public class InvalidLogoutException extends Exception
{
	public InvalidLogoutException(final String s) { super(s); }

	public InvalidLogoutException() { super(); }
}