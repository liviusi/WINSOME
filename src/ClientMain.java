import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import api.Command;
import api.Constants;
import configuration.Configuration;
import configuration.InvalidConfigException;
import server.rmi.PasswordNotValidException;
import server.rmi.UsernameAlreadyExistsException;
import server.rmi.UsernameNotValidException;
import server.user.InvalidTagException;
import server.user.TagListTooLongException;

/**
 * @brief Client file.
 * @author Giacomo Trapani
 */

public class ClientMain
{

	public static void main(String[] args)
	{
		if (args.length >= 1)
		{
			System.err.println("Usage: java -cp \".:./bin/:./libs/gson-2.8.6.jar\" ClientMain <path/to/config>");
			System.exit(1);
		}
		String configFilename = null;
		if (args.length == 0)
		{
			configFilename = "./configs/client.txt";
			System.out.printf("No config files have been provided. Default will be used: %s.\n", configFilename);
		}
		else configFilename = args[0];

		Configuration configuration = null;
		try { configuration = new Configuration(new File(configFilename)); }
		catch (NullPointerException | IOException | InvalidConfigException  e)
		{
			System.err.println("Fatal error occurred while parsing configuration: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
		SocketChannel client = null;
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
	

		System.out.println("Client is now running...");
		Scanner scanner = new Scanner(System.in);
		String loggedInUsername = null;
		boolean loggedIn = false;
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
					if (len < 3)
					{
						System.err.println("Invalid syntax. Type \"help\" to find out which commands are available.");
						continue;
					}
					boolean tmp;
					try { tmp = Command.login(command[1], command[2], client, true); }
					catch (IOException e)
					{
						e.printStackTrace();
						break loop;
					}
					if (tmp && !loggedIn)
					{
						loggedIn = true;
						loggedInUsername = command[1];
					}
					continue;
				}
			}
		System.out.println("Client is now freeing resources...");
		scanner.close();
		try { client.close(); }
		catch (IOException ignored) { }
	}
}