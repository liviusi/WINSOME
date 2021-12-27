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

		if (properties.containsKey(Constants.SERVERADDRESS_STRING) && properties.containsKey(Constants.PORTNOTCP_STRING) && 
			properties.containsKey(Constants.PORTNOUDP_STRING) && properties.containsKey(Constants.MULTICASTADDRESS_STRING) &&
			properties.containsKey(Constants.REGISTRYADDRESS_STRING) && properties.containsKey(Constants.PORTNOREGISTRY_STRING) &&
			properties.containsKey(Constants.REGISTERSERVICENAME_STRING))
		{
			// getting port numbers:
			try
			{
				portNoTCP = Constants.parsePortNo(properties.getProperty(Constants.PORTNOTCP_STRING));
				portNoUDP = Constants.parsePortNo(properties.getProperty(Constants.PORTNOUDP_STRING));
				portNoRegistry = Constants.parsePortNo(properties.getProperty(Constants.PORTNOREGISTRY_STRING));
				portNoMulticast = Constants.parsePortNo(properties.getProperty(Constants.PORTNOMULTICAST_STRING));

				if (portNoTCP == portNoUDP || portNoTCP == portNoRegistry || portNoTCP == portNoMulticast ||
					portNoUDP == portNoRegistry || portNoUDP == portNoMulticast || portNoRegistry == portNoMulticast)
					throw new InvalidConfigException("The same address can be used only once.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException("Specified port number is not a proper int."); }
			catch (IllegalArgumentException e) { throw new InvalidConfigException("Specified port number is not valid."); }

			// validating network addresses:
			try
			{
				serverAddress = InetAddress.getByName(properties.getProperty(Constants.SERVERADDRESS_STRING));
				multicastAddress = InetAddress.getByName(properties.getProperty(Constants.MULTICASTADDRESS_STRING));
				registryAddress = InetAddress.getByName(properties.getProperty(Constants.REGISTRYADDRESS_STRING));
				if (serverAddress.equals(multicastAddress) || serverAddress.equals(registryAddress) || multicastAddress.equals(registryAddress))
					throw new InvalidConfigException("The same address can be used only once.");

				if (!multicastAddress.isMulticastAddress())
					throw new InvalidConfigException("Specified multicast address is not in multicast range.");
			}
			catch (UnknownHostException e) { throw new InvalidConfigException(e.getMessage()); }

			registerServiceName = properties.getProperty(Constants.REGISTERSERVICENAME_STRING);

		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}
}
