package configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * @brief Parser for server configuration file.
 * @author Giacomo Trapani
*/
public class ServerConfiguration extends Configuration
{
	public final int socketTimeout;
	public final int corePoolSize;
	public final int maximumPoolSize;
	public final int keepAliveTime;
	public final int threadPoolTimeout;

	/**
	 * @param configurationFile cannot be null. It must follow the syntax specified in the report.
	 * @throws NullPointerException if configurationFile is null.
	 * @throws FileNotFoundException if configurationFile cannot be found.
	 * @throws IOException if an I/O error occurs when closing the stream required to read configurationFile.
	 * @throws InvalidConfigException if configurationFile does not follow the required syntax rules.
	*/
	public ServerConfiguration(final File configurationFile)
	throws NullPointerException, FileNotFoundException, IOException, InvalidConfigException
	{
		super(configurationFile);
		final Properties properties = new Properties();
		final FileInputStream fis = new FileInputStream(configurationFile);
		properties.load(fis);
		fis.close();

		if (properties.containsKey(Constants.SOCKETTIMEOUT_STRING) && properties.containsKey(Constants.COREPOOLSIZE_STRING) &&
			properties.containsKey(Constants.MAXIMUMPOOLSIZE_STRING) && properties.containsKey(Constants.KEEPALIVETIME_STRING) &&
			properties.containsKey(Constants.THREADPOOLTIMEOUT_STRING))
		{
			// validating socket timeout:
			try
			{
				socketTimeout = Integer.parseInt(properties.getProperty(Constants.SOCKETTIMEOUT_STRING));
				if (socketTimeout <= 0) throw new InvalidConfigException("Socket timeout must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating core pool size:
			try
			{
				corePoolSize = Integer.parseInt(properties.getProperty(Constants.COREPOOLSIZE_STRING));
				if (corePoolSize <= 0) throw new InvalidConfigException("Core pool size must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating maximum pool size:
			try
			{
				maximumPoolSize = Integer.parseInt(properties.getProperty(Constants.MAXIMUMPOOLSIZE_STRING));
				if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) throw new InvalidConfigException("Maximum pool size must be greater than zero" + 
					" and, at minimum, equal to core pool size.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating keep alive time:
			try
			{
				keepAliveTime = Integer.parseInt(properties.getProperty(Constants.KEEPALIVETIME_STRING));
				if (keepAliveTime <= 0) throw new InvalidConfigException("Keep alive time must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating thread pool timeout:
			try
			{
				threadPoolTimeout = Integer.parseInt(properties.getProperty(Constants.THREADPOOLTIMEOUT_STRING));
				if (threadPoolTimeout <= 0) throw new InvalidConfigException("Thread pool timeout must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			return;
		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}
}