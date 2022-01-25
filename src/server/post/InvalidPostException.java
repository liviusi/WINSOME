package server.post;

/**
 * Exception to be thrown when an attempt is made to instantiate a post with invalid parameters.
 * @author Giacomo Trapani.
 */
public class InvalidPostException extends Exception
{
	public InvalidPostException(final String s) { super(s); }

	public InvalidPostException() { super(); }
}