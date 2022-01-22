package api;

public class Colors
{
	public static final String ANSI_RED = "\033[0;31m"; // fatal error
	public static final String ANSI_GREEN = "\033[0;32m"; // ok
	public static final String ANSI_YELLOW = "\033[0;33m"; // warning
	public static final String ANSI_CYAN = "\033[0;36m"; // info
	public static final String ANSI_RESET = "\u001B[0m";

	private Colors() { }
}
