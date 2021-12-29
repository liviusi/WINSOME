import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import configuration.InvalidConfigException;
import configuration.ServerConfiguration;
import server.RMITask;
import server.rmi.UserSet;
import server.rmi.UserStorage;

/**
 * @brief Server file.
 * @author Giacomo Trapani
 */

public class ServerMain
{
	public static final String USERS_FILENAME = "./storage/users.json";
	public static final int BUFFERSIZE = 1024;
	public static void main(String[] args)
	{

		if (args.length >= 1)
		{
			System.err.println("Usage: java -cp \".:./bin/:./libs/gson-2.8.6.jar\" ServerMain <path/to/config>");
			System.exit(1);
		}
		String configFilename = null;
		if (args.length == 0)
		{
			configFilename = "./configs/server.txt";
			System.out.printf("No config files have been provided. Default will be used: %s.\n", configFilename);
		}
		else configFilename = args[0];

		// parsing configuration file:
		ServerConfiguration configuration = null;
		try { configuration = new ServerConfiguration(new File(configFilename)); }
		catch (NullPointerException | IOException | InvalidConfigException  e)
		{
			System.err.println("Fatal error occurred while parsing configuration: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}

		// setting up rmi:
		UserStorage users = new UserSet();
		Thread rmi = new Thread(new RMITask(configuration, (UserSet) users));
		rmi.start();

		// setting up multiplexing:
		ServerSocketChannel serverSocketChannel = null;
		Selector selector = null;
		try
		{
			serverSocketChannel = ServerSocketChannel.open();
			ServerSocket sSocket = serverSocketChannel.socket();
			sSocket.bind(new InetSocketAddress(configuration.portNoTCP));
			serverSocketChannel.configureBlocking(false);
			selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		}
		catch (IOException e)
		{
			System.err.printf("Fatal I/O error occurred while setting up selector: now aborting...\n%s\n", e.getMessage());
			System.exit(1);
		}

		// properly handling shutdown:
		final Selector selectorHandler = selector;
		final ExecutorService threadPool = new ThreadPoolExecutor(configuration.corePoolSize, configuration.maximumPoolSize, configuration.keepAliveTime,
				TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		final ServerConfiguration configurationHandler = configuration;
		final UserSet userSetHandler = (UserSet) users;
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			public void run()
			{
				System.out.printf("\nServer has now entered shutdown mode.\n");
				rmi.interrupt();
				selectorHandler.wakeup();
				Iterator<SelectionKey> keys = selectorHandler.keys().iterator();
				while (keys.hasNext())
				{
					SelectableChannel c = keys.next().channel();
					if (c instanceof ServerSocketChannel)
					{
						try { c.close(); }
						catch (IOException e) { System.err.printf("I/O error occurred during shutdown:\n%s\n.", e.getMessage()); }
					}
				}
				try { selectorHandler.close(); }
				catch (IOException e) { System.err.printf("I/O error occurred during shutdown:\n%s\n.", e.getMessage()); }
				threadPool.shutdown();
				try
				{
					if (!threadPool.awaitTermination(configurationHandler.threadPoolTimeout, TimeUnit.MILLISECONDS))
						threadPool.shutdownNow();
				}
				catch (InterruptedException e) { threadPool.shutdownNow(); }
				// storing users:
				try { userSetHandler.backupUsers(new File(USERS_FILENAME)); }
				catch (IOException e) { System.err.printf("I/O error occurred during shutdown:\n%s\n", e.getMessage()); }
				try { rmi.join(500); }
				catch (InterruptedException e) { }
			}
		});
		System.out.println("Server is now running...");

		
		// select loop:
		while (true)
		{
			int r = 0;
			try { r = selector.select(); }
			catch (IOException e)
			{
				System.err.printf("I/O error occurred during select:\n%s\n", e.getMessage());
				System.exit(1);
			}
			catch (ClosedSelectorException e) { break; }
			if (!selector.isOpen()) break;
			if (r == 0)
			{
				
			}
		}
	}
}