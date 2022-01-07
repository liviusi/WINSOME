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

	private static final String SOCKETTIMEOUT_STRING = "SOCKETTIMEOUT";
	private static final String COREPOOLSIZE_STRING = "COREPOOLSIZE";
	private static final String MAXIMUMPOOLSIZE_STRING = "MAXIMUMPOOLSIZE";
	private static final String KEEPALIVETIME_STRING = "KEEPALIVETIME";
	private static final String THREADPOOLTIMEOUT_STRING = "THREADPOOLTIMEOUT";
	private static final String USERSTORAGE_STRING = "USERSTORAGE";
	private static final String FOLLOWINGSTORAGE_STRING = "FOLLOWINGSTORAGE";
	private static final String POSTSSTORAGE_STRING = "POSTSSTORAGE";

	/** Socket timeout value. */
	public final int socketTimeout;
	/** Thread pool core pool size. */
	public final int corePoolSize;
	/** Thread pool maximum pool size. */
	public final int maximumPoolSize;
	/** Thread pool keep alive time. */
	public final int keepAliveTime;
	/** Thread pool timeout value. */
	public final int threadPoolTimeout;
	/** Filename of the file users are to be stored in. */
	public final String userStorageFilename;
	/** Filename of the file users and the users they are following are to be stored in. */
	public final String followingStorageFilename;
	/** Filename of the file posts are to be stored in. */
	public final String postStorageFilename;

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

		if (properties.containsKey(SOCKETTIMEOUT_STRING) && properties.containsKey(COREPOOLSIZE_STRING) &&
			properties.containsKey(MAXIMUMPOOLSIZE_STRING) && properties.containsKey(KEEPALIVETIME_STRING) &&
			properties.containsKey(THREADPOOLTIMEOUT_STRING) && properties.containsKey(USERSTORAGE_STRING) &&
			properties.containsKey(FOLLOWINGSTORAGE_STRING) && properties.containsKey(POSTSSTORAGE_STRING))
		{
			// validating socket timeout:
			try
			{
				socketTimeout = Integer.parseInt(properties.getProperty(SOCKETTIMEOUT_STRING));
				if (socketTimeout <= 0) throw new InvalidConfigException("Socket timeout must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating core pool size:
			try
			{
				corePoolSize = Integer.parseInt(properties.getProperty(COREPOOLSIZE_STRING));
				if (corePoolSize <= 0) throw new InvalidConfigException("Core pool size must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating maximum pool size:
			try
			{
				maximumPoolSize = Integer.parseInt(properties.getProperty(MAXIMUMPOOLSIZE_STRING));
				if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) throw new InvalidConfigException("Maximum pool size must be greater than zero" + 
					" and, at minimum, equal to core pool size.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating keep alive time:
			try
			{
				keepAliveTime = Integer.parseInt(properties.getProperty(KEEPALIVETIME_STRING));
				if (keepAliveTime <= 0) throw new InvalidConfigException("Keep alive time must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			// validating thread pool timeout:
			try
			{
				threadPoolTimeout = Integer.parseInt(properties.getProperty(THREADPOOLTIMEOUT_STRING));
				if (threadPoolTimeout <= 0) throw new InvalidConfigException("Thread pool timeout must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			userStorageFilename = properties.getProperty(USERSTORAGE_STRING);
			followingStorageFilename = properties.getProperty(FOLLOWINGSTORAGE_STRING);
			postStorageFilename = properties.getProperty(POSTSSTORAGE_STRING);
			return;
		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}
}