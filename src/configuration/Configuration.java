package configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * Class used to parse client-side configuration file.
 * @author Giacomo Trapani.
 */
public class Configuration
{
	private static final String SERVERADDRESS_STRING = "SERVERADDRESS";
	private static final String PORTNOTCP_STRING = "TCPPORT";
	private static final String PORTNOUDP_STRING = "UDPPORT";
	private static final String REGISTRYADDRESS_STRING = "REGISTRYHOST";
	private static final String PORTNOREGISTRY_STRING = "REGISTRYPORT";
	private static final String REGISTERSERVICENAME_STRING = "REGISTERSERVICENAME";
	private static final String CALLBACKSERVICENAME_STRING = "CALLBACKSERVICENAME";

	/** Server address. */
	public final InetAddress serverAddress;
	/** Port number for TCP connections. */
	public final int portNoTCP;
	/** Port number for UDP connections. */
	public final int portNoUDP;
	/** Host the registry is located in. */
	public final String registryAddressName;
	/** Port number for RMI registry. */
	public final int portNoRegistry;
	/** Name of the service handling register operation. */
	public final String registerServiceName;
	/** Name of the service handling callbacks. */
	public final String callbackServiceName;
	
	public Configuration(final File configurationFile)
	throws NullPointerException, FileNotFoundException, IOException, InvalidConfigException
	{
		if (configurationFile == null) throw new NullPointerException("Configuration file cannot be null.");
		final Properties properties = new Properties();
		final FileInputStream fis = new FileInputStream(configurationFile);
		properties.load(fis);
		fis.close();

		if (properties.containsKey(SERVERADDRESS_STRING) && properties.containsKey(PORTNOTCP_STRING) && 
			properties.containsKey(PORTNOUDP_STRING) &&
			properties.containsKey(REGISTRYADDRESS_STRING) && properties.containsKey(PORTNOREGISTRY_STRING) &&
			properties.containsKey(REGISTERSERVICENAME_STRING) && properties.containsKey(CALLBACKSERVICENAME_STRING))
		{
			// getting port numbers:
			try
			{
				portNoTCP = parsePortNo(properties.getProperty(PORTNOTCP_STRING));
				portNoUDP = parsePortNo(properties.getProperty(PORTNOUDP_STRING));
				portNoRegistry = parsePortNo(properties.getProperty(PORTNOREGISTRY_STRING));

				if (portNoTCP == portNoUDP || portNoTCP == portNoRegistry || portNoUDP == portNoRegistry)
					throw new InvalidConfigException("The same address can be used only once.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException("Specified port number is not a proper int."); }
			catch (IllegalArgumentException e) { throw new InvalidConfigException("Specified port number is not valid."); }

			// validating network addresses:
			try { serverAddress = InetAddress.getByName(properties.getProperty(SERVERADDRESS_STRING)); }
			catch (UnknownHostException e) { throw new InvalidConfigException(e.getMessage()); }

			registryAddressName = properties.getProperty(REGISTRYADDRESS_STRING);
			registerServiceName = properties.getProperty(REGISTERSERVICENAME_STRING);
			callbackServiceName = properties.getProperty(CALLBACKSERVICENAME_STRING);

		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}

	static int parsePortNo(final String portNoStr)
	throws NullPointerException, NumberFormatException, IllegalArgumentException
	{
		if (portNoStr == null) throw new NullPointerException();
		final int portNoInt = Integer.parseInt(portNoStr);
		if (portNoInt < 1024 || portNoInt > 65535) throw new IllegalArgumentException("Given port is not in the valid range: [1024; 65535].");
		return portNoInt;
	}
}
