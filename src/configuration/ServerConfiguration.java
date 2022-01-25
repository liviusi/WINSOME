package configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * Parser for server configuration file.
 * @author Giacomo Trapani
*/
public class ServerConfiguration extends Configuration
{
	private static final String MULTICASTADDRESS_STRING = "MULTICASTADDRESS";
	private static final String PORTNOMULTICAST_STRING = "MULTICASTPORT";
	private static final String COREPOOLSIZE_STRING = "COREPOOLSIZE";
	private static final String MAXIMUMPOOLSIZE_STRING = "MAXIMUMPOOLSIZE";
	private static final String KEEPALIVETIME_STRING = "KEEPALIVETIME";
	private static final String THREADPOOLTIMEOUT_STRING = "THREADPOOLTIMEOUT";
	private static final String USERSTORAGE_STRING = "USERSTORAGE";
	private static final String FOLLOWINGSTORAGE_STRING = "FOLLOWINGSTORAGE";
	private static final String TRANSACTIONSSTORAGE_STRING = "TRANSACTIONSSTORAGE";
	private static final String POSTSSTORAGE_STRING = "POSTSSTORAGE";
	private static final String POSTSINTERACTIONSSTORAGE_STRING = "POSTSINTERACTIONSSTORAGE";
	private static final String BACKUPINTERVAL_STRING = "BACKUPINTERVAL";
	private static final String REWARDSINTERVAL_STRING = "REWARDSINTERVAL";
	private static final String REWARDSAUTHORPERCENTAGE_STRING = "REWARDSAUTHORPERCENTAGE";
	private static final String LOGFILE_STRING = "LOGFILE";

	/** Multicast address. */
	public final InetAddress multicastAddress;
	/** Multicast port number. */
	public final int portNoMulticast;
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
	/** Filename of the file users' transactions are to be stored in. */
	public final String transactionsFilename;
	/** Filename of the file posts are to be stored in. */
	public final String postStorageFilename;
	/** Filename of the file interactions with posts are to be stored in. */
	public final String postsInteractionsStorageFilename;
	/** Interval between backups in msec. */
	public final int backupInterval;
	/** Interval (in msec) to wait between rewards' periodic calculation. */
	public final int rewardsInterval;
	/** Percentage of the rewards to be sent out to the author of a certain post. */
	public final double rewardsAuthorPercentage;
	/** Filename of the file server's log is to be stored in. */
	public final String logFilename;

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

		if (properties.containsKey(COREPOOLSIZE_STRING) && properties.containsKey(LOGFILE_STRING) &&
			properties.containsKey(MAXIMUMPOOLSIZE_STRING) && properties.containsKey(KEEPALIVETIME_STRING) &&
			properties.containsKey(THREADPOOLTIMEOUT_STRING) && properties.containsKey(USERSTORAGE_STRING) &&
			properties.containsKey(TRANSACTIONSSTORAGE_STRING) && properties.containsKey(FOLLOWINGSTORAGE_STRING)
			&& properties.containsKey(POSTSSTORAGE_STRING) && properties.containsKey(POSTSINTERACTIONSSTORAGE_STRING)
			&& properties.containsKey(REWARDSINTERVAL_STRING) && properties.containsKey(MULTICASTADDRESS_STRING)
			&& properties.containsKey(PORTNOMULTICAST_STRING) && properties.containsKey(REWARDSAUTHORPERCENTAGE_STRING)
			&& properties.containsKey(BACKUPINTERVAL_STRING))
		{
			// validating multicast port number:
			try { portNoMulticast = parsePortNo(properties.getProperty(PORTNOMULTICAST_STRING)); }
			catch (NumberFormatException e) { throw new InvalidConfigException("Specified port number is not a proper int."); }
			catch (IllegalArgumentException e) { throw new InvalidConfigException("Specified port number is not valid."); }
			if (portNoUDP == portNoMulticast || portNoRegistry == portNoMulticast || portNoTCP == portNoMulticast)
				throw new InvalidConfigException("The same address can be used only once.");
			// validating multicast address
			try { multicastAddress = InetAddress.getByName(properties.getProperty(MULTICASTADDRESS_STRING)); }
			catch (UnknownHostException e) { throw new InvalidConfigException(e.getMessage()); }
			if (!multicastAddress.isMulticastAddress()) throw new InvalidConfigException("Specified multicast address is not in multicast range.");
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
			try
			{
				rewardsInterval = Integer.parseInt(properties.getProperty(REWARDSINTERVAL_STRING));
				if (rewardsInterval <= 0) throw new InvalidConfigException("Rewards' interval must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			try
			{
				rewardsAuthorPercentage = Double.parseDouble(properties.getProperty(REWARDSAUTHORPERCENTAGE_STRING));
				if (rewardsAuthorPercentage <= 0 || rewardsAuthorPercentage >= 100) throw new InvalidConfigException("Author's reward percentage must be in range ]0; 100[.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			try
			{
				backupInterval = Integer.parseInt(properties.getProperty(BACKUPINTERVAL_STRING));
				if (backupInterval <= 0) throw new InvalidConfigException("Backup interval must be greater than zero.");
			}
			catch (NumberFormatException e) { throw new InvalidConfigException(e.getMessage()); }
			userStorageFilename = properties.getProperty(USERSTORAGE_STRING);
			followingStorageFilename = properties.getProperty(FOLLOWINGSTORAGE_STRING);
			transactionsFilename = properties.getProperty(TRANSACTIONSSTORAGE_STRING);
			postStorageFilename = properties.getProperty(POSTSSTORAGE_STRING);
			postsInteractionsStorageFilename = properties.getProperty(POSTSINTERACTIONSSTORAGE_STRING);
			logFilename = properties.getProperty(LOGFILE_STRING);
			return;
		}
		else
			throw new InvalidConfigException("Not all required fields have been specified; it is advised to check" +
				" against the documentation and the example(s) available.");
	}

	public String getMulticastInfo()
	{
		return String.format("{ \"%s\": \"%s\", \"%s\": %d }", "address", multicastAddress.getHostName(), "portNo", portNoMulticast);
	}
}