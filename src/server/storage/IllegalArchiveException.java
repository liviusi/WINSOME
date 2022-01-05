package server.storage;

/**
 * @brief Exception to be thrown whenever an archive to be parsed on startup is not valid.
 * @author Giacomo Trapani
 */
public class IllegalArchiveException extends Exception
{
	public IllegalArchiveException(final String s) { super(s); }

	public IllegalArchiveException() { super(); }
}