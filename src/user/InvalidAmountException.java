package user;

/**
 * @brief Exception to be thrown when an amount is negative.
 * @author Giacomo Trapani.
 */
public class InvalidAmountException extends Exception
{
	public InvalidAmountException(final String s) { super(s); }

	public InvalidAmountException() { super(); }
}