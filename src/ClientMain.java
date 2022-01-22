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
import com.google.gson.JsonSyntaxException;

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

	private static class Transaction
	{
		public final double amount;
		public final String timestamp;

		private Transaction(final double amount, final String timestamp)
		{
			this.amount = amount;
			this.timestamp = timestamp;
		}

		public static Transaction fromJSON(String jsonString)
		{
			return gson.fromJson(jsonString, Transaction.class);
		}
	}

	/** ERROR MESSAGES */

	private static final String SERVER_DISCONNECT = "Server has forcibly reset the connection.";
	private static final String NOT_LOGGED_IN = "Client has yet to login.";
	private static final String INVALID_SYNTAX = "Invalid syntax. Type \"help\" to find out which commands are available.";
	private static final String FATAL_IO = "Fatal I/O error occurred, now aborting...";

	/** COMMANDS */

	private static final String QUIT = ":q!";
	private static final String SHOW = "show";
	private static final String HELP = "help";
	private static final String REGISTER = "register";
	private static final String LOGIN = "login";
	private static final String LOGOUT = "logout";
	private static final String LIST_USERS = "list users";
	private static final String LIST_FOLLOWERS = "list followers";
	private static final String LIST_FOLLOWING = "list following";
	private static final String FOLLOW = "follow";
	private static final String UNFOLLOW = "unfollow";
	private static final String BLOG = "blog";
	private static final String POST = "post";
	private static final String SHOW_FEED = "show feed";
	private static final String SHOW_POST = "show post";
	private static final String DELETE = "delete";
	private static final String REWIN = "rewin";
	private static final String RATE = "rate";
	private static final String COMMENT = "comment";
	private static final String WALLET = "wallet";
	private static final String WALLET_BTC = "wallet btc";

	private static final Gson gson = new Gson();


	public static void main(String[] args)
	{
		String configFilename = null;
		Configuration configuration = null;
		SocketChannel channel = null;
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
			System.exit(1);
		}
		try
		{
			channel = SocketChannel.open(new InetSocketAddress(configuration.serverAddress, configuration.portNoTCP));
			System.out.println("Connected to server successfully!");
		}
		catch (IOException e)
		{
			System.err.println(FATAL_IO);
			e.printStackTrace();
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

		// Local variables are to be (re-)defined as final to be handled in shutdown hook
		final Scanner scanner = new Scanner(System.in);
		final RMICallback callbackHandler = callback;
		final RMIFollowersSet callbackObjectHandler = callbackObject;
		final SocketChannel channelHandler = channel;

		String loggedInUsername = null;
		int result = -1;

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{
			public void run()
			{
				System.out.println("Client is now freeing resources...");
				try
				{
					callbackHandler.unregisterForCallback(callbackObjectHandler);
					UnicastRemoteObject.unexportObject(callbackObjectHandler, true);
				}
				catch (RemoteException ignored) { }
				catch (NullPointerException ignored) { }
				scanner.close();
				try { channelHandler.close(); }
				catch (IOException ignored) { }
			}
		}));

	loop:
		while(true)
		{
			if (loggedInUsername == null) System.out.printf("> ");
			else System.out.printf("%s> ", loggedInUsername);

			final String s = scanner.nextLine();

			Set<String> destSet = new HashSet<>();
			StringBuilder destStringBuilder = new StringBuilder();

			switch (s)
			{
				case QUIT:
					System.exit(0);

				case HELP:
					System.out.printf("register <username> <password> <tags>:\n\tRegister.\n");
					continue loop;

				case LOGOUT:
					try { result = Command.logout(loggedInUsername, channel, true); }
					catch (NullPointerException e)
					{
						if (loggedInUsername == null)
							System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);
						
						case 0:
							System.out.println("< " + loggedInUsername + " has now logged out.");
							loggedInUsername = null;
							try
							{
								callback.unregisterForCallback(callbackObject);
								UnicastRemoteObject.unexportObject(callbackObject, true);
							}
							catch (RemoteException e)
							{
								System.err.println("Fatal error occurred while cleaning up previously allocated resources.");
								System.exit(1);
							}
							callbackObject = null;
					}
					continue loop;

				case LIST_USERS:
					destSet = new HashSet<>();
					try { result = Command.listUsers(loggedInUsername, channel, destSet, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);
						
						case 0:
							if (destSet.isEmpty()) System.out.println("< " + loggedInUsername + " does not share interests with any user.");
							else
							{
								System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
								System.out.println("< -------------------------------------------------------------------------------");
								for (String u: destSet)
								{
									User tmp = User.fromJSON(u);
									System.out.println(String.format("< %30s %25s %10s", tmp.username, "|", String.join(", ", tmp.tags)));
								}
							}
					}
					continue loop;

				case LIST_FOLLOWERS:
					try { destSet = callbackObject.recoverFollowers(); }
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					if (destSet.isEmpty()) System.out.println(loggedInUsername + " is not followed by any user.");
					else
					{
						System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
						System.out.println("< -------------------------------------------------------------------------------");
						for (String u: destSet)
						{
							User tmp = User.fromJSON(u);
							System.out.println(String.format("< %30s %25s %10s", tmp.username, "|", String.join(", ", tmp.tags)));
						}
					}
					continue loop;

				case LIST_FOLLOWING:
					try { result = Command.listFollowing(loggedInUsername, channel, destSet, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							if (destSet.isEmpty()) System.out.println("< " + loggedInUsername + " has yet to start following any user.");
						else
						{
							System.out.println(String.format("< %30s %25s %10s", "USERNAME", "|", "TAGS"));
							System.out.println("< -------------------------------------------------------------------------------");
							for (String u: destSet)
							{
								User tmp = User.fromJSON(u);
								System.out.println(String.format("< %30s %25s %10s", tmp.username, "|", String.join(", ", tmp.tags)));
							}
						}
					}
					continue loop;

				case BLOG:
					try { result = Command.viewBlog(loggedInUsername, channel, destSet, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							if (destSet.isEmpty()) System.out.println("< " + loggedInUsername + " has yet to start posting.");
							else
							{
								System.out.println(String.format("< %5s %5s %15s %15s %15s", "ID", "|", "AUTHOR", "|", "TITLE"));
								System.out.println("< --------------------------------------------------------------------------");
								for (String p: destSet)
								{
									PostPreview tmp = PostPreview.fromJSON(p);
									System.out.println(String.format("< %5s %5s %15s %15s %15s", tmp.id, "|", tmp.author, "|", tmp.title));
								}
							}
					}
					continue loop;

				case SHOW_FEED:
					try { result = Command.showFeed(loggedInUsername, channel, destSet, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							if (destSet.isEmpty()) System.out.println("< The users " + loggedInUsername + " is following have yet to start posting.");
							else
							{
								System.out.println(String.format("< %5s %5s %15s %15s %15s", "ID", "|", "AUTHOR", "|", "TITLE"));
								System.out.println("< --------------------------------------------------------------------------");
								for (String p: destSet)
								{
									PostPreview tmp = PostPreview.fromJSON(p);
									System.out.println(String.format("< %5s %5s %15s %15s %15s", tmp.id, "|", tmp.author, "|", tmp.title));
								}
							}
					}
					continue loop;

				case WALLET:
					try { result = Command.getWallet(loggedInUsername, channel, destSet, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
						{
							double total = 0;
							boolean flag = true; // toggled on if the first element of the set is yet to be printed
							for (String t: destSet)
							{
								Transaction tmp = null;
								try
								{
									tmp = Transaction.fromJSON(t);
									if (flag)
									{
										System.out.println(String.format("< %30s %25s %10s", "AMOUNT", "|", "TIMESTAMP"));
										System.out.println("< -------------------------------------------------------------------------------");
									}
									flag = false;
									System.out.println(String.format("< %30s %25s %10s", tmp.amount, "|", tmp.timestamp));
								}
								catch (JsonSyntaxException e) { total = Double.parseDouble(t); }
							}
							System.out.printf("< TOTAL: %f WINCOINS\n", total);
						}
					}
					continue loop;
				
				case WALLET_BTC:
					try { result = Command.getWalletInBitcoin(loggedInUsername, channel, destStringBuilder, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.printf("< %s BTC\n", destStringBuilder.toString());
					}
					continue loop;
				
				default:
					break;
			}

			final String[] command = parseQuotes(s);
			final int len = command.length;

			if (len == 0) continue loop;
			switch (command[0])
			{
				case REGISTER:
				{
					if (len < 3)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					if (loggedInUsername != null)
					{
						System.err.println("A new user cannot be registered before logging out.");
						continue loop;
					}
					Set<String> tags = new HashSet<>();
					for (int i = 3; i < len; i++) tags.add(command[i]);
					try { Command.register(command[1], command[2], tags, configuration.portNoRegistry, configuration.registerServiceName, true); }
					catch (RemoteException | NotBoundException e)
					{
						System.err.println("Fatal error occurred during registration, now aborting...");
						e.printStackTrace();
						System.exit(1);
					}
					catch (UsernameNotValidException | PasswordNotValidException | InvalidTagException | TagListTooLongException e)
					{
						System.err.printf("< Given credentials do not meet the requirements:\n%s\n", e.getMessage());
						continue loop;
					}
					catch (UsernameAlreadyExistsException e)
					{
						System.err.println("Username has already been taken.");
						continue loop;
					}
					continue loop;
				}

				case LOGIN:
					if (len != 3)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.login(command[1], command[2], channel, destSet, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							loggedInUsername = command[1];
							System.out.println("< " + command[1] + " has now logged in.");
							try
							{
								callbackObject = new RMIFollowersSet(destSet);
								callback.registerForCallback(callbackObject, loggedInUsername);
							}
							catch (RemoteException e)
							{
								System.err.println("Fatal error occurred while setting up RMI callbacks: now aborting...");
								e.printStackTrace();
								System.exit(1);
							}
					}
					continue loop;

				case FOLLOW:
					if (len != 2)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.followUser(loggedInUsername, command[1], channel, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< " + loggedInUsername + " has now started following " + command[1]);
							break;
					}
					continue loop;

				case UNFOLLOW:
					if (len != 2)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.unfollowUser(loggedInUsername, command[1], channel, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< " + loggedInUsername + " has now stopped following " + command[1]);
							break;
					}
					continue loop;

				case POST:
					if (len != 3)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.createPost(loggedInUsername, command[1], command[2], channel, destStringBuilder, true); }
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< New post created: ID " + destStringBuilder.toString());
							break;
					}
					continue loop;
				
				case SHOW:
					if (len != 3 || !(String.format("%s %s", command[0], command[1]).equals(SHOW_POST)))
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.showPost(loggedInUsername, Integer.parseInt(command[2]), channel, destStringBuilder, true); }
					catch (NumberFormatException e)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							Post p = Post.fromJSON(destStringBuilder.toString());
							System.out.printf("< ID: %d\n< Title: %s\n< Contents:\n\t%s\n< Upvotes: %d - Downvotes: %d\n< Rewon by: %d\n\t",
										p.id, p.title, p.contents, p.upvotes, p.downvotes, p.rewonBy.length);
							for (String r: p.rewonBy) System.out.printf("%s", r);
							System.out.printf("\n< Comments: %d\n", p.comments.length);
							for (Post.Comment c : p.comments) System.out.printf("\t%s:\n\"%s\"\n", c.author, c.contents);
							break;
					}
					continue loop;

				case DELETE:
					if (len != 2)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.deletePost(loggedInUsername, Integer.parseInt(command[1]), channel, true); }
					catch (NumberFormatException e)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< Post has now been deleted.");
							break;
					}
					continue loop;

				case REWIN:
					if (len != 2)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.rewinPost(loggedInUsername, Integer.parseInt(command[1]), channel, true); }
					catch (NumberFormatException e)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< Post has now been rewon.");
					}
					continue loop;

				case RATE:
					if (len != 3)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.ratePost(loggedInUsername, Integer.parseInt(command[1]), Integer.parseInt(command[2]), channel, true); }
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					catch (NumberFormatException e)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< New vote added.");
					}
					continue loop;

				case COMMENT:
					if (len != 3)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					try { result = Command.addComment(loggedInUsername, Integer.parseInt(command[1]), command[2], channel, true); }
					catch (NullPointerException e)
					{
						System.err.println(NOT_LOGGED_IN);
						continue loop;
					}
					catch (NumberFormatException e)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					catch (IOException e)
					{
						System.err.println(FATAL_IO);
						e.printStackTrace();
						System.exit(1);
					}
					switch (result)
					{
						case -1:
							System.err.println(SERVER_DISCONNECT);
							System.exit(0);

						case 0:
							System.out.println("< New comment added.");
					}
					continue loop;


				default:
					System.out.println(INVALID_SYNTAX);
					break;
			}
		}
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