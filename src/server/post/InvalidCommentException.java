package server.post;

/**
 * Exception to be thrown when an attempt is made to submit an invalid comment.
 * @author Giacomo Trapani.
 */
public class InvalidCommentException extends Exception
{
	public InvalidCommentException(final String s) { super(s); }

	public InvalidCommentException() { super(); }
}