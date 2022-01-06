import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import api.Command;
import api.Constants;
import client.RMIFollowersMap;
import configuration.Configuration;
import configuration.InvalidConfigException;
import server.RMICallback;
import server.storage.PasswordNotValidException;
import server.storage.UsernameAlreadyExistsException;
import server.storage.UsernameNotValidException;
import user.InvalidTagException;
import user.TagListTooLongException;

/**
 * @brief Client file.
 * @author Giacomo Trapani
 */

public class ClientMain
{
	private static String SERVER_DISCONNECT = "Server has forcibly reset the connection.";

	public static void main(String[] args)
	{
		String configFilename = null;
		Configuration configuration = null;
		SocketChannel client = null;
		RMICallback callback = null;
		RMIFollowersMap callbackObject = null;

		if (args.length >= 1)
		{
			System.err.println("Usage: java -cp \".:./bin/:./libs/gson-2.8.6.jar\" ClientMain <path/to/config>");
			System.exit(1);
		}
		if (args.length == 0)
		{
			configFilename = "./configs/client.txt";
			System.out.printf("No config files have been provided. Default will be used: %s.\n", configFilename);
		}
		else configFilename = args[0];

		try { configuration = new Configuration(new File(configFilename)); }
		catch (NullPointerException | IOException | InvalidConfigException  e)
		{
			System.err.println("Fatal error occurred while parsing configuration: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
		try
		{
			client = SocketChannel.open(new InetSocketAddress(configuration.serverAddress, configuration.portNoTCP));
			System.out.println("Connected to server successfully!");
		}
		catch (IOException e)
		{
			System.err.printf("I/O error occurred:\n%s\n", e.getMessage());
			System.exit(1);
		}
		try
		{
			Registry r = LocateRegistry.getRegistry(configuration.portNoRegistry);
			callback = (RMICallback) r.lookup(configuration.callbackServiceName);
			callbackObject = new RMIFollowersMap();
			callback.registerForCallback(callbackObject);
			System.out.println("RMI Callbacks have now been setup correctly.");
		}
		catch (RemoteException | NotBoundException e)
		{
			System.err.println("Fatal error occurred while setting up RMI callback.");
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("Client is now running...");
		Scanner scanner = new Scanner(System.in);
		String loggedInUsername = null;
		boolean loggedIn = false;
		Set<String> resultSet = null;
		int result = -1;
		loop:
			while(true)
			{
				if (!loggedIn) System.out.printf("> ");
				else System.out.printf("%s> ", loggedInUsername);
				String s = scanner.nextLine();
				if (s.equalsIgnoreCase(Constants.QUIT_STRING)) break loop;
				if (s.equalsIgnoreCase(Constants.HELP_STRING))
				{
					System.out.printf("register <username> <password> <tags>:\n\tRegister.\n");
					continue;
				}
				if (s.equals(Constants.LIST_USERS_STRING))
				{
					resultSet = new HashSet<>();
					try { result = Command.listUsers(loggedInUsername, client, true, resultSet); }
					catch (IOException e)
					{
						e.printStackTrace();
						break loop;
					}
					catch (NullPointerException e)
					{
						System.err.println(Constants.CLIENT_NOT_LOGGED_IN);
						continue;
					}
					if (result == -1)
					{
						System.err.println(SERVER_DISCONNECT);
						break loop;
					}
					else if (result == 0)
					{
						String[] tmp = null;
						if (resultSet.isEmpty()) System.out.println("< " + loggedInUsername + " is not following any user.");
						else
						{
							System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
							System.out.println("< -------------------------------------------------------------------------------");
							for (String u: resultSet)
							{
								tmp = u.split("\r\n");
								System.out.println(String.format("< %30s %25s %10s", tmp[0], "|", tmp[1]));
							}
						}
						continue;
					}
				}
				if (s.equals(Constants.LIST_FOLLOWERS_STRING))
				{
					try { resultSet = callbackObject.recoverFollowers(loggedInUsername); }
					catch (NullPointerException e)
					{
						System.err.println(Constants.CLIENT_NOT_LOGGED_IN);
						continue;
					}

					String[] tmp = null;
					if (resultSet.isEmpty()) System.out.println(loggedInUsername + " is not following any user.");
					else
					{
						System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
						System.out.println("< -------------------------------------------------------------------------------");
						for (String u: resultSet)
						{
							tmp = u.split("\r\n");
							System.out.println(String.format("< %30s %25s %10s", tmp[0], "|", tmp[1]));
						}
					}
					continue;
				}
				String[] command = s.split(" ");
				if (s.startsWith(Constants.REGISTER_STRING))
				{
					int len = command.length;
					if (len < 3)
					{
						System.err.println("Invalid syntax. Type \"help\" to find out which commands are available.");
						continue;
					}
					Set<String> tags = new HashSet<>();
					for (int i = 3; i < len; i++)
						tags.add(command[i]);
					try { Command.register(command[1], command[2], tags, configuration.portNoRegistry, configuration.registerServiceName, true); }
					catch (RemoteException | NotBoundException e)
					{
						System.err.printf("Exception occurred during registration:\n%s\nNow aborting...\n", e.getMessage());
						System.exit(1);
					}
					catch (UsernameNotValidException | PasswordNotValidException | InvalidTagException | TagListTooLongException e)
					{
						System.err.printf("Given credentials do not meet the requirements:\n%s\n", e.getMessage());
						continue;
					}
					catch (NullPointerException e)
					{
						System.err.printf("Exception occurred during registration:\n%s\n", e.getMessage());
						continue;
					}
					catch (UsernameAlreadyExistsException e)
					{
						System.err.printf("Username has already been taken.\n");
						continue;
					}
					continue;
				}
				if (s.startsWith(Constants.LOGIN_STRING))
				{
					int len = command.length;
					if (len != 3)
					{
						System.err.println("Invalid syntax. Type \"help\" to find out which commands are available.");
						continue;
					}
					try { result = Command.login(command[1], command[2], client, true); }
					catch (IOException e)
					{
						e.printStackTrace();
						break loop;
					}
					if (result == -1)
					{
						System.err.println(SERVER_DISCONNECT);
						break loop;
					}
					if (result == 0 && !loggedIn)
					{
						loggedIn = true;
						loggedInUsername = command[1];
						System.out.println("< " + command[1] + Constants.LOGIN_SUCCESS);
					}
					continue;
				}
				if (s.startsWith(Constants.LOGOUT_STRING))
				{
					int len = command.length;
					if (len != 1)
					{
						System.err.println("Invalid syntax. Type \"help\" to find out which commands are available.");
						continue;
					}
					try
					{
						result = Command.logout(loggedInUsername, client, true);
					}
					catch (NullPointerException e)
					{
						if (loggedInUsername == null)
							System.err.println(Constants.CLIENT_NOT_LOGGED_IN);
						continue;
					}
					catch (IOException e)
					{
						e.printStackTrace();
						break loop;
					}
					if (result == -1)
					{
						System.err.println(SERVER_DISCONNECT);
						break loop;
					}
					if (result == 0)
					{
						loggedIn = false;
						loggedInUsername = null;
					}
					continue;
				}
				if (command[0].equals(Constants.FOLLOW_USER_STRING))
				{
					try { result = Command.followUser(loggedInUsername, command[1], client, true); }
					catch (NullPointerException e)
					{
						if (loggedInUsername == null)
							System.err.println(Constants.CLIENT_NOT_LOGGED_IN);
						continue;
					}
					catch (IOException e)
					{
						e.printStackTrace();
						break loop;
					}
				}
			}
		System.out.println("Client is now freeing resources...");
		try
		{
			callback.unregisterForCallback(callbackObject);
			UnicastRemoteObject.unexportObject(callbackObject, true);
		}
		catch (RemoteException ignored) { }
		scanner.close();
		try { client.close(); }
		catch (IOException ignored) { }
	}
}