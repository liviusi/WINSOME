import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import api.Command;
import client.RMIFollowersSet;
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
	private static class User
	{
		public final String username;
		public final String[] tags;

		public static final Gson gson = new Gson();

		private User(String username, String[] tags)
		{
			this.username = username;
			this.tags = tags;
		}

		public static User fromJSON(String jsonString)
		{
			User u = gson.fromJson(jsonString, User.class);
			return u;
		}
	}

	private static class Post
	{
		public final int id;
		public final String author;
		public final String title;
		public final String contents;

		public static final Gson gson = new Gson();

		private Post(final int id, final String author, final String title, final String contents)
		{
			this.id = id;
			this.author = author;
			this.title = title;
			this.contents = contents;
		}

		public static Post fromJSON(String jsonString)
		{
			Post p = gson.fromJson(jsonString, Post.class);
			return p;
		}
	}

	private static final String SERVER_DISCONNECT = "Server has forcibly reset the connection.";
	private static final String LOGIN_SUCCESS = " has now logged in.";
	private static final String NOT_LOGGED_IN = "Client has yet to login";
	private static final String INVALID_SYNTAX = "Invalid syntax. Type \"help\" to find out which commands are available.";

	private static final String QUIT_STRING = ":q!";
	private static final String HELP_STRING = "help";
	private static final String REGISTER_STRING = "register";
	private static final String LOGIN_STRING = "login";
	private static final String LOGOUT_STRING = "logout";
	private static final String LIST_USERS_STRING = "list users";
	private static final String LIST_FOLLOWERS_STRING = "list followers";
	private static final String LIST_FOLLOWING_STRING = "list following";
	private static final String FOLLOW_USER_STRING = "follow";
	private static final String UNFOLLOW_USER_STRING = "unfollow";
	private static final String BLOG_STRING = "blog";
	private static final String CREATE_POST_STRING = "post";
	private static final String SHOW_FEED_STRING = "show feed";
	private static final String COMMENT_STRING = "comment";
	private static final String RATE_STRING = "rate";


	public static void main(String[] args)
	{
		String configFilename = null;
		Configuration configuration = null;
		SocketChannel client = null;
		RMICallback callback = null;
		RMIFollowersSet callbackObject = null;

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
		}
		catch (RemoteException | NotBoundException e)
		{
			System.err.println("Fatal error occurred while setting up RMI callbacks.");
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("Client is now running...");

		Scanner scanner = new Scanner(System.in);
		String loggedInUsername = null;
		boolean loggedIn = false;
		Set<String> resultSet = null;
		int result = -1;

		while(true)
		{
			if (!loggedIn) System.out.printf("> ");
			else System.out.printf("%s> ", loggedInUsername);
			String s = scanner.nextLine();
			if (s.equalsIgnoreCase(QUIT_STRING)) break;
			if (s.equalsIgnoreCase(HELP_STRING))
			{
				System.out.printf("register <username> <password> <tags>:\n\tRegister.\n");
				continue;
			}
			if (s.equals(LIST_USERS_STRING))
			{
				resultSet = new HashSet<>();
				try { result = Command.listUsers(loggedInUsername, client, true, resultSet); }
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				else if (result == 0)
				{
					if (resultSet.isEmpty()) System.out.println("< " + loggedInUsername + " does not share interests with any user.");
					else
					{
						System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
						System.out.println("< -------------------------------------------------------------------------------");
						for (String u: resultSet)
						{
							User tmp = User.fromJSON(u);
							System.out.println(String.format("< %30s %25s %10s", tmp.username, "|", String.join(", ", tmp.tags)));
						}
					}
					continue;
				}
			}
			if (s.equals(LIST_FOLLOWERS_STRING))
			{
				try { resultSet = callbackObject.recoverFollowers(); }
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}

				if (resultSet.isEmpty()) System.out.println(loggedInUsername + " is not followed by any user.");
				else
				{
					System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
					System.out.println("< -------------------------------------------------------------------------------");
					for (String u: resultSet)
					{
						User tmp = User.fromJSON(u);
						System.out.println(String.format("< %30s %25s %10s", tmp.username, "|", String.join(", ", tmp.tags)));
					}
				}
				continue;
			}
			if (s.equals(LIST_FOLLOWING_STRING))
			{
				resultSet = new HashSet<>();
				try { result = Command.listFollowing(loggedInUsername, client, true, resultSet); }
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				else if (result == 0)
				{
					if (resultSet.isEmpty()) System.out.println("< " + loggedInUsername + " has yet to start following any user.");
					else
					{
						System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
						System.out.println("< -------------------------------------------------------------------------------");
						for (String u: resultSet)
						{
							User tmp = User.fromJSON(u);
							System.out.println(String.format("< %30s %25s %10s", tmp.username, "|", String.join(", ", tmp.tags)));
						}
					}
					continue;
				}
			}
			if (s.equals(LOGOUT_STRING))
			{
				try
				{
					result = Command.logout(loggedInUsername, client, true);
				}
				catch (NullPointerException e)
				{
					if (loggedInUsername == null)
						System.err.println(NOT_LOGGED_IN);
					continue;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0)
				{
					System.out.println("< " + loggedInUsername + " has now logged out.");
					loggedIn = false;
					loggedInUsername = null;
					try
					{
						callback.unregisterForCallback(callbackObject);
						UnicastRemoteObject.unexportObject(callbackObject, true);
					}
					catch (RemoteException e)
					{
						System.err.println("Fatal error occurred while cleaning up previously allocated resources.");
						e.printStackTrace();
						System.exit(1);
					}
				}
				continue;
			}
			if (s.equals(BLOG_STRING))
			{
				if (loggedInUsername == null)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				resultSet = new HashSet<>();
				try { result = Command.viewBlog(loggedInUsername, client, resultSet, true); }
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				else
				{
					if (resultSet.isEmpty()) System.out.println("< " + loggedInUsername + " has yet to start posting.");
					else
					{
						System.out.println(String.format("< %5s %5s %15s %15s %15s", "ID", "|", "AUTHOR", "|", "TITLE"));
						System.out.println("< --------------------------------------------------------------------------");
						for (String p: resultSet)
						{
							Post tmp = Post.fromJSON(p);
							System.out.println(String.format("< %5s %5s %15s %15s %15s", tmp.id, "|", tmp.author, "|", tmp.title));
						}
					}
					continue;
				}
			}
			if (s.equals(SHOW_FEED_STRING))
			{
				if (loggedInUsername == null)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				resultSet = new HashSet<>();
				try { result = Command.showFeed(loggedInUsername, client, resultSet, true); }
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				else
				{
					if (resultSet.isEmpty()) System.out.println("< The users " + loggedInUsername + " is following have yet to start posting.");
					else
					{
						System.out.println(String.format("< %5s %5s %15s %15s %15s", "ID", "|", "AUTHOR", "|", "TITLE"));
						System.out.println("< --------------------------------------------------------------------------");
						for (String p: resultSet)
						{
							Post tmp = Post.fromJSON(p);
							System.out.println(String.format("< %5s %5s %15s %15s %15s", tmp.id, "|", tmp.author, "|", tmp.title));
						}
					}
					continue;
				}
			}
			String[] command = parseQuotes(s);
			if (command[0].equals(REGISTER_STRING))
			{
				int len = command.length;
				if (len < 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				Set<String> tags = new HashSet<>();
				for (int i = 3; i < len; i++)
					tags.add(command[i]);
				try { Command.register(command[1], command[2], tags, configuration.portNoRegistry, configuration.registerServiceName, true); }
				catch (RemoteException | NotBoundException e)
				{
					System.err.printf("Exception occurred during registration:\n%s\nNow aborting...\n", e.getMessage());
					break;
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
			if (command[0].equals(LOGIN_STRING))
			{
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				resultSet = new HashSet<>();
				try { result = Command.login(command[1], command[2], client, resultSet, true); }
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0 && !loggedIn)
				{
					loggedIn = true;
					loggedInUsername = command[1];
					System.out.println("< " + command[1] + LOGIN_SUCCESS);
					try
					{
						callbackObject = new RMIFollowersSet(resultSet);
						callback.registerForCallback(callbackObject, loggedInUsername);
					}
					catch (RemoteException e)
					{
						System.err.println("Fatal error occurred while setting up RMI callbacks.");
						e.printStackTrace();
						System.exit(1);
					}
				}
				continue;
			}
			if (command[0].equals(FOLLOW_USER_STRING))
			{
				if (command.length != 2)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.followUser(loggedInUsername, command[1], client, true); }
				catch (NullPointerException e)
				{
					if (loggedInUsername == null)
						System.err.println(NOT_LOGGED_IN);
					continue;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0)
				{
					System.out.println("< " + loggedInUsername + " has now started following " + command[1]);
					continue;
				}
				continue;
			}
			if (command[0].equals(UNFOLLOW_USER_STRING))
			{
				if (command.length != 2)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.unfollowUser(loggedInUsername, command[1], client, true); }
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0)
				{
					System.out.println("< " + loggedInUsername + " has now stopped following " + command[1]);
					continue;
				}
			}
			if (command[0].equals(CREATE_POST_STRING))
			{
				StringBuilder sb = new StringBuilder();
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.createPost(loggedInUsername, command[1], command[2], client, true, sb); }
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0)
				{
					System.out.println("< New post created: ID " + sb.toString());
					continue;
				}
			}
			/**
			if (command[0].equals(COMMENT_STRING))
			{
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.comment(loggedInUsername, Integer.parseInt(command[1]), command[2], client, true); }
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0)
				{
					System.out.println("< New comment added.");
					continue;
				}
			}
			if (command[0].equals(RATE_STRING))
			{
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.rate(loggedInUsername, Integer.parseInt(command[1]), Integer.parseInt(command[2]), client, true); }
				catch (NullPointerException e)
				{
					System.err.println(NOT_LOGGED_IN);
					continue;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
				if (result == -1)
				{
					System.err.println(SERVER_DISCONNECT);
					break;
				}
				if (result == 0)
				{
					System.out.println("< New vote added.");
					continue;
				}
			}
			*/
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

	private static String[] parseQuotes(String quotedString)
	{
		List<String> list = new ArrayList<String>();
		Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(quotedString);
		while (m.find())
			list.add(m.group(1).replace("\"", ""));
		return list.toArray(new String[0]);
	}
}