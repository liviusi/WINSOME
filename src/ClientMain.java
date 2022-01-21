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

		private User(String username, String[] tags)
		{
			this.username = username;
			this.tags = tags;
		}

		public static User fromJSON(String jsonString)
		{
			return gson.fromJson(jsonString, User.class);
		}
	}

	private static class PostPreview
	{
		public final int id;
		public final String author;
		public final String title;

		public static final Gson gson = new Gson();

		private PostPreview(final int id, final String author, final String title)
		{
			this.id = id;
			this.author = author;
			this.title = title;
		}

		public static PostPreview fromJSON(String jsonString)
		{
			return gson.fromJson(jsonString, PostPreview.class);
		}
	}

	private static class Post
	{
		private static class Comment
		{
			public final String author;
			public final String contents;

			private Comment(String author, String contents)
			{
				this.author = author;
				this.contents = contents;
			}
		}

		public final int id;
		public final String title;
		public final String contents;
		public final int upvotes;
		public final int downvotes;
		public final String[] rewonBy;
		public final Comment[] comments;

		private Post(int id, String title, String contents, int upvotes, int downvotes, String[] rewonBy, Comment[] comments)
		{
			this.id = id;
			this.title = title;
			this.contents = contents;
			this.upvotes = upvotes;
			this.downvotes = downvotes;
			this.rewonBy = rewonBy;
			this.comments = comments;
		}

		public static Post fromJSON(String jsonString)
		{
			return gson.fromJson(jsonString, Post.class);
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
	private static final String SHOW_POST_STRING = "show post";
	private static final String DELETE_POST_STRING = "delete";
	private static final String REWIN_STRING = "rewin";
	private static final String RATE_STRING = "rate";
	// private static final String COMMENT_STRING = "comment";

	private static final Gson gson = new Gson();


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
							PostPreview tmp = PostPreview.fromJSON(p);
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
							PostPreview tmp = PostPreview.fromJSON(p);
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
				if (result == 0) System.out.println("< " + loggedInUsername + " has now stopped following " + command[1]);
				continue;
			}
			if (command[0].equals(CREATE_POST_STRING))
			{
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				StringBuilder sb = new StringBuilder();
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
				if (result == 0) System.out.println("< New post created: ID " + sb.toString());
				continue;
			}
			if (String.format("%s %s", command[0], command[1]).equals(SHOW_POST_STRING))
			{
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				StringBuilder sb = new StringBuilder();
				try { result = Command.showPost(loggedInUsername, Integer.parseInt(command[2]), client, sb, true); }
				catch (NumberFormatException e)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
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
					Post p = Post.fromJSON(sb.toString());
					System.out.printf("< ID: %d\n< Title: %s\n< Contents:\n\t%s\n< Upvotes: %d - Downvotes: %d\n< Rewon by: %d\n\t",
							p.id, p.title, p.contents, p.upvotes, p.downvotes, p.rewonBy.length);
					for (String r: p.rewonBy) System.out.printf("%s", r);
					System.out.printf("\n< Comments: %d\n", p.comments.length);
					for (Post.Comment c : p.comments)
						System.out.printf("\t%s:\n\"%s\"\n", c.author, c.contents);
				}
				continue;
			}
			if (command[0].equals(DELETE_POST_STRING))
			{
				if (command.length != 2)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.deletePost(loggedInUsername, Integer.parseInt(command[1]), client, true); }
				catch (NumberFormatException e)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
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
				if (result == 0) System.out.println("< Post has now been deleted.");
				continue;
			}
			if (command[0].equals(REWIN_STRING))
			{
				if (command.length != 2)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.rewinPost(loggedInUsername, Integer.parseInt(command[1]), client, true); }
				catch (NumberFormatException e)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
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
				if (result == 0) System.out.println("< Post has now been rewon.");
				continue;
			}
			if (command[0].equals(RATE_STRING))
			{
				if (command.length != 3)
				{
					System.err.println(INVALID_SYNTAX);
					continue;
				}
				try { result = Command.ratePost(loggedInUsername, Integer.parseInt(command[1]), Integer.parseInt(command[2]), client, true); }
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