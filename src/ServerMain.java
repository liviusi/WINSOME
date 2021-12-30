import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import api.CommandCode;
import api.Constants;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import configuration.InvalidConfigException;
import configuration.ServerConfiguration;
import server.API;
import server.RMITask;
import server.rmi.UserMap;
import server.rmi.UserStorage;
import server.user.InvalidLoginException;
import server.user.WrongCredentialsException;

/**
 * @brief Server file.
 * @author Giacomo Trapani
 */

public class ServerMain
{
	public static final String USERS_FILENAME = "./storage/users.json";
	public static final int BUFFERSIZE = 1024;

	private static class SetElement
	{
		final SocketChannel client;
		final int operation;
		final ByteBuffer buffer;

		public SetElement(final SocketChannel client, final int operation, final ByteBuffer buffer)
		throws IllegalArgumentException
		{
			if (operation != SelectionKey.OP_READ && operation != SelectionKey.OP_WRITE)
				throw new IllegalArgumentException("Operation specified is not valid. Only OP_READ and OP_WRITE are permitted.");
			this.client = client;
			this.operation = operation;
			this.buffer = buffer;
		}
	}

	private static class RequestHandler implements Runnable
	{

		private Set<SetElement> toBeRegistered = null;
		private Selector selector = null;
		private SelectionKey key = null;
		private UserMap users = null;

		public RequestHandler(final Set<SetElement> toBeRegistered, final Selector selector,
				final SelectionKey key, final UserMap users)
		{
			this.toBeRegistered = toBeRegistered;
			this.selector = selector;
			this.key = key;
			this.users = users;
		}

		public void run()
		{
			ByteBuffer buffer = (ByteBuffer) key.attachment();
			SocketChannel client = (SocketChannel) key.channel();
			int nRead = 0;

			buffer.flip();
			buffer.clear();
			try { nRead = client.read(buffer); }
			catch (ClosedChannelException e) { return; }
			catch (IOException e)
			{
				System.err.printf("I/O error occurred:\n%s\n", e.getMessage());
				try { client.close(); }
				catch (IOException ignored) { }
				return;
			}
			if (nRead == -1) return;
			else if (nRead == 0) return;
			else // read has not failed:
			{
				buffer.flip();
				final String message = StandardCharsets.UTF_8.decode(buffer).toString();
				final String clientName;
				clientName = Integer.toString(client.hashCode());
				if (message.equals(Constants.QUIT_STRING))
				{
					System.out.printf("client %s is now quitting.\n", clientName);
					try { client.close(); }
					catch (IOException ignored) { }
					return;
				}
				System.out.printf("> client %s: \"%s\"\n", clientName, message);
				String[] command = message.split(":");

				// "Login:<username>:<hashPassword>"
				if (command[0].equals(CommandCode.LOGINATTEMPT.getDescription()))
				{
					// validate command syntax:
					if (command.length != 3) return;
					buffer.flip();
					buffer.clear();
					boolean tmp = false;
					try { API.handleLogin(users, client, command[1], command[2]); }
					catch (InvalidLoginException | WrongCredentialsException e)
					{
						tmp = true;
						buffer.put(e.getMessage().getBytes(StandardCharsets.UTF_8));
					}
					if (!tmp) buffer.put((command[1] + Constants.LOGIN_SUCCESS).getBytes(StandardCharsets.UTF_8));
					buffer.flip();
				}
				// "Login setup:<username>"
				else if (command[0].equals(CommandCode.LOGINSETUP.getDescription()))
				{
					// validate command syntax:
					if (command.length != 2) return;
					buffer.flip();
					buffer.clear();
					final byte[] salt = API.handleLoginSetup(users, command[1]);
					if (salt == null) buffer.put((command[1] + " has yet to sign up.").getBytes(StandardCharsets.UTF_8));
					else buffer.put(salt);
					buffer.flip();
				}

				toBeRegistered.add(new SetElement(client, SelectionKey.OP_WRITE, buffer));
				selector.wakeup();
				return;
			}
		}
	}

	private static class MessageDispatcher implements Runnable
	{

		private Set<SetElement> toBeRegistered = null;
		private Selector selector = null;
		private SelectionKey key = null;

		public MessageDispatcher(final Set<SetElement> toBeRegistered, final Selector selector, final SelectionKey key)
		{
			this.toBeRegistered = toBeRegistered;
			this.selector = selector;
			this.key = key;
		}

		public void run()
		{
			SocketChannel client = (SocketChannel) key.channel();
			ByteBuffer buffer = (ByteBuffer) key.attachment();

			try { client.configureBlocking(false); }
			catch (ClosedChannelException e)
			{
				try { client.close(); }
				catch (IOException ignored) { }
				return;
			}
			catch (IOException e)
			{
				try { client.close(); }
				catch (IOException ignored) { }
				return;
			}
			try { client.write(buffer); }
			catch (ClosedChannelException e) { return; }
			catch (IOException e)
			{
				System.err.printf("I/O error occurred:\n%s\nNow removing client.\n", e.getMessage());
				try { client.close(); }
				catch (IOException ignored) { }
			}
			buffer.clear();
			toBeRegistered.add(new SetElement(client, SelectionKey.OP_READ, buffer));
			selector.wakeup();
			return;
		}
	}
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
		UserStorage users = new UserMap();
		Thread rmi = new Thread(new RMITask(configuration, (UserMap) users));
		rmi.start();

		// setting up multiplexing:
		ServerSocketChannel serverSocketChannel = null;
		Selector selector = null;
		try
		{
			serverSocketChannel = ServerSocketChannel.open();
			ServerSocket sSocket = serverSocketChannel.socket();
			sSocket.bind(new InetSocketAddress(configuration.serverAddress, configuration.portNoTCP));
			serverSocketChannel.configureBlocking(false);
			selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		}
		catch (IOException e)
		{
			System.err.printf("Fatal I/O error occurred while setting up selector: now aborting...\n%s\n", e.getMessage());
			System.exit(1);
		}
		Set<SetElement> toBeRegistered = ConcurrentHashMap.newKeySet();

		// properly handling shutdown:
		final Selector selectorHandler = selector;
		final ExecutorService threadPool = new ThreadPoolExecutor(configuration.corePoolSize, configuration.maximumPoolSize, configuration.keepAliveTime,
				TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		final ServerConfiguration configurationHandler = configuration;
		final UserMap userSetHandler = (UserMap) users;
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
				if (!toBeRegistered.isEmpty())
				{
					for (final SetElement s : toBeRegistered)
					{
						try { s.client.register(selector, s.operation, s.buffer); }
						catch (ClosedChannelException clientDisconnected) { }
						toBeRegistered.remove(s);
					}
				}
			}
			final Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
			while (keys.hasNext())
			{
				try
				{
					final SelectionKey k = keys.next();
					keys.remove(); // remove from selected set
					if (k.isAcceptable())
					{
						final ServerSocketChannel server = (ServerSocketChannel) k.channel();
						SocketChannel client = null;
						try
						{
							client = server.accept();
							client.configureBlocking(false);

							client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFERSIZE));
							System.out.println("New client accepted: " + client.getLocalAddress());
						}
						catch (ClosedChannelException clientDisconnected) { } // nothing to do
						catch (IOException e)
						{
							System.err.printf("I/O error occurred:\n%s\n", e.getMessage());
						}
						continue;
					}
					if (k.isReadable())
					{
						k.cancel();
						threadPool.execute(new Thread(new RequestHandler(toBeRegistered, selector, k, (UserMap) users)));
					}
					if (k.isWritable())
					{
						k.cancel();
						threadPool.execute(new Thread(new MessageDispatcher(toBeRegistered, selector, k)));
					}
				}
				catch (CancelledKeyException e) { continue; }
			}
		}
	}
}