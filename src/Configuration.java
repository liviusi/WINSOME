import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ConcurrentModificationException;
import java.util.Properties;

/**
 * @brief Parser for configuration file.
 * @author Giacomo Trapani
 */


public class Configuration
{
	private static final String SERVERADDRESS_STRING = "SERVERADDRESS";
	private static final String PORTNOTCP_STRING = "TCPPORT";
	private static final String PORTNOUDP_STRING = "UDPPORT";
	private static final String MULTICASTADDRESS_STRING = "MULTICASTADDRESS";
	private static final String PORTNOMULTICAST_STRING = "MULTICASTPORT";
	private static final String REGISTRYADDRESS_STRING = "REGISTRYHOST";
	private static final String PORTNOREGISTRY_STRING = "REGISTRYPORT";
	private static final String SOCKETTIMEOUT_STRING = "SOCKETTIMEOUT";

	final InetAddress serverAddress;
	final int portNoTCP;
	final int portNoUDP;
	final InetAddress multicastAddress;
	final int portNoMulticast;
	final InetAddress registryAddress;
	final int portNoRegistry;
	final int socketTimeout;

	private static int parsePortNo(String portNoStr) throws NumberFormatException, IllegalArgumentException
	{
		int portNoInt;
		portNoInt = Integer.parseInt(portNoStr);
		if (portNoInt < 1024 || portNoInt > 65535) throw new IllegalArgumentException();
		return portNoInt;
	}

	public Configuration(File configurationFile) throws FileNotFoundException, IOException, InvalidConfigException, ConcurrentModificationException
	{
		Properties properties = new Properties();
		FileInputStream fis = new FileInputStream(configurationFile);
		properties.load(fis);
		fis.close();

		if (properties.containsKey(SERVERADDRESS_STRING) && properties.containsKey(PORTNOTCP_STRING) && 
			properties.containsKey(PORTNOUDP_STRING) && properties.containsKey(MULTICASTADDRESS_STRING) &&
			properties.containsKey(REGISTRYADDRESS_STRING) && properties.containsKey(PORTNOREGISTRY_STRING) &&
			properties.containsKey(SOCKETTIMEOUT_STRING))
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
			catch (IllegalArgumentException e) { throw new InvalidConfigException("Given port is not in the valid range: [1024; 65535]."); }

			// validating network addresses:
			try
			{
				serverAddress = InetAddress.getByName(properties.getProperty(SERVERADDRESS_STRING));
				multicastAddress = InetAddress.getByName(properties.getProperty(MULTICASTADDRESS_STRING));
				registryAddress = InetAddress.getByName(properties.getProperty(REGISTRYADDRESS_STRING));
				if (serverAddress.equals(multicastAddress) || serverAddress.equals(registryAddress) || multicastAddress.equals(registryAddress))
					throw new InvalidConfigException("The same address can be used only once.");

				if (!multicastAddress.isMulticastAddress())
					throw new InvalidConfigException("Specified multicast address is not in multicast range.");
			}
			catch (UnknownHostException e) { throw new InvalidConfigException(e.getMessage()); }

			// validating socket timeout:
			try
			{
				socketTimeout = Integer.parseInt(properties.getProperty(SOCKETTIMEOUT_STRING));
				if (socketTimeout <= 0) throw new InvalidConfigException("Socket timeout must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			return;
		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}
}