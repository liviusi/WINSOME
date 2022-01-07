package server.post;

/**
 * @brief Exception to be thrown when an attempt is made to cast an invalid vote.
 * @author Giacomo Trapani
 */
public class InvalidVoteException extends Exception
{
	public InvalidVoteException(final String s) { super(s); }

	public InvalidVoteException() { super(); }
}