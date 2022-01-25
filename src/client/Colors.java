package client;

/**
 * Utility class used to print out colored strings on console.
 * @author Giacomo Trapani.
 */
public class Colors
{
	/** Sets color to red. */
	public static final String ANSI_RED = "\033[0;31m"; // fatal error
	/** Sets color to green. */
	public static final String ANSI_GREEN = "\033[0;32m"; // ok
	/** Sets color to yellow. */
	public static final String ANSI_YELLOW = "\033[0;33m"; // warning
	/** Sets color to cyan. */
	public static final String ANSI_CYAN = "\033[0;36m"; // info
	/** Resets color, it is needed after printing out a colored string. */
	public static final String ANSI_RESET = "\u001B[0m";

	private Colors() { }
}
