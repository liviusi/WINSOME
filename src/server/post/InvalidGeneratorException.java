package server.post;

/**
 * @brief Exception to be thrown when new posts' ID's generator is not
 * in a consistent state.
 * @author Giacomo Trapani
 */
public class InvalidGeneratorException extends Exception
{
	public InvalidGeneratorException(final String s) { super(s); }

	public InvalidGeneratorException() { super(); }
}