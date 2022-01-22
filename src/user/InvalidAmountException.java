package user;

public class InvalidAmountException extends Exception
{
	public InvalidAmountException(final String s) { super(s); }

	public InvalidAmountException() { super(); }
}