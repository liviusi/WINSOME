package configuration;

/**
 * @brief Exception to be thrown when a configuration file is not valid.
 * @author Giacomo Trapani.
*/
public class InvalidConfigException extends Exception
{
	public InvalidConfigException(final String s) { super(s); }
	
	public InvalidConfigException() { super(); }
}
