package configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class Configuration
{
	private static final String SERVERADDRESS_STRING = "SERVERADDRESS";
	private static final String PORTNOTCP_STRING = "TCPPORT";
	private static final String PORTNOUDP_STRING = "UDPPORT";
	private static final String MULTICASTADDRESS_STRING = "MULTICASTADDRESS";
	private static final String PORTNOMULTICAST_STRING = "MULTICASTPORT";
	private static final String REGISTRYADDRESS_STRING = "REGISTRYHOST";
	private static final String PORTNOREGISTRY_STRING = "REGISTRYPORT";
	private static final String REGISTERSERVICENAME_STRING = "REGISTERSERVICENAME";

	public final InetAddress serverAddress;
	public final int portNoTCP;
	public final int portNoUDP;
	public final InetAddress multicastAddress;
	public final int portNoMulticast;
	public final InetAddress registryAddress;
	public final int portNoRegistry;
	public final String registerServiceName;
	
	public Configuration(final File configurationFile)
	throws NullPointerException, FileNotFoundException, IOException, InvalidConfigException
	{
		if (configurationFile == null) throw new NullPointerException("Configuration file cannot be null.");
		final Properties properties = new Properties();
		final FileInputStream fis = new FileInputStream(configurationFile);
		properties.load(fis);
		fis.close();

		if (properties.containsKey(SERVERADDRESS_STRING) && properties.containsKey(PORTNOTCP_STRING) && 
			properties.containsKey(PORTNOUDP_STRING) && properties.containsKey(MULTICASTADDRESS_STRING) &&
			properties.containsKey(REGISTRYADDRESS_STRING) && properties.containsKey(PORTNOREGISTRY_STRING) &&
			properties.containsKey(REGISTERSERVICENAME_STRING))
		{
			// getting port numbers:
			try
			{
				portNoTCP = parsePortNo(properties.getProperty(PORTNOTCP_STRING));
				portNoUDP = parsePortNo(properties.getProperty(PORTNOUDP_STRING));
				portNoRegistry = parsePortNo(properties.getProperty(PORTNOREGISTRY_STRING));
				portNoMulticast = parsePortNo(properties.getProperty(PORTNOMULTICAST_STRING));

				if (portNoTCP == portNoUDP || portNoTCP == portNoRegistry || portNoTCP == portNoMulticast ||
					portNoUDP == portNoRegistry || portNoUDP == portNoMulticast || portNoRegistry == portNoMulticast)
					throw new InvalidConfigException("The same address can be used only once.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException("Specified port number is not a proper int."); }
			catch (IllegalArgumentException e) { throw new InvalidConfigException("Specified port number is not valid."); }

			// validating network addresses:
			try
			{
				serverAddress = InetAddress.getByName(properties.getProperty(SERVERADDRESS_STRING));
				multicastAddress = InetAddress.getByName(properties.getProperty(MULTICASTADDRESS_STRING));
				registryAddress = InetAddress.getByName(properties.getProperty(REGISTRYADDRESS_STRING));

				if (!multicastAddress.isMulticastAddress())
					throw new InvalidConfigException("Specified multicast address is not in multicast range.");
			}
			catch (UnknownHostException e) { throw new InvalidConfigException(e.getMessage()); }

			registerServiceName = properties.getProperty(REGISTERSERVICENAME_STRING);

		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}

	private static int parsePortNo(final String portNoStr)
	throws NullPointerException, NumberFormatException, IllegalArgumentException
	{
		if (portNoStr == null) throw new NullPointerException();
		final int portNoInt = Integer.parseInt(portNoStr);
		if (portNoInt < 1024 || portNoInt > 65535) throw new IllegalArgumentException("Given port is not in the valid range: [1024; 65535].");
		return portNoInt;
	}
}
