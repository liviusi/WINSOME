import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import api.rmi.InvalidTagException;
import api.rmi.PasswordNotValidException;
import api.rmi.RMICallback;
import api.rmi.TagListTooLongException;
import api.rmi.UsernameAlreadyExistsException;
import api.rmi.UsernameNotValidException;
import client.Colors;
import client.Command;
import client.MulticastInfo;
import client.MulticastWorker;
import client.RMIFollowersSet;
import configuration.Configuration;
import configuration.InvalidConfigException;

/**
 * Client file.
 * @author Giacomo Trapani.
 */

public class ClientMain
{
	/** Container class used to parse a JSON-ified user. */
	private static class User
	{
		public final String username;
		public final String[] tags;

		private User(String username, String[] tags)
		{
			this.username = username;
			this.tags = tags;
		}

		public static User fromJSON(String JSONString)
		{
			return gson.fromJson(JSONString, User.class);
		}
	}

	/** Container class used to parse a JSON-ified post preview. */
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

		public static PostPreview fromJSON(String JSONString)
		{
			return gson.fromJson(JSONString, PostPreview.class);
		}
	}

	/** Container class used to parse a JSON-ified post. */
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

		public static Post fromJSON(String JSONString)
		{
			return gson.fromJson(JSONString, Post.class);
		}
	}

	/** Container class used to parse a JSON-ified transaction. */
	private static class Transaction
	{
		public final double amount;
		public final String timestamp;

		private Transaction(final double amount, final String timestamp)
		{
			this.amount = amount;
			this.timestamp = timestamp;
		}

		public static Transaction fromJSON(String JSONString)
		{
			return gson.fromJson(JSONString, Transaction.class);
		}
	}


	/** ERROR MESSAGES */

	/** [Fatal] Disconnection by server error message. */
	private static final String SERVER_DISCONNECT = Colors.ANSI_RED + "Server has forcibly reset the connection." + Colors.ANSI_RESET;
	/** [Non-fatal] Not logged in client error message. */
	private static final String NOT_LOGGED_IN = Colors.ANSI_YELLOW + "Client has yet to login." + Colors.ANSI_RESET;
	/** [Non-fatal] Syntax error message. */
	private static final String INVALID_SYNTAX = Colors.ANSI_YELLOW + "Invalid syntax. Type \"help\" to find out which commands are available." + Colors.ANSI_RESET;
	/** [Fatal] I/O error message. */
	private static final String FATAL_IO = Colors.ANSI_RED + "Fatal I/O error occurred, now aborting..." + Colors.ANSI_RESET;


	/** COMMANDS */
	/** Quit gracefully. */
	private static final String QUIT = ":q!";
	/** Part of the show post command. */
	private static final String SHOW = "show";
	/** Help command. */
	private static final String HELP = "help";
	/** Register command. */
	private static final String REGISTER = "register";
	/** Login command. */
	private static final String LOGIN = "login";
	/** Logout command. */
	private static final String LOGOUT = "logout";
	/** List users command. */
	private static final String LIST_USERS = "list users";
	/** List followers command. */
	private static final String LIST_FOLLOWERS = "list followers";
	/** List following command. */
	private static final String LIST_FOLLOWING = "list following";
	/** Follow command. */
	private static final String FOLLOW = "follow";
	/** Unfollow command. */
	private static final String UNFOLLOW = "unfollow";
	/** Blog command. */
	private static final String BLOG = "blog";
	/** Post command. */
	private static final String POST = "post";
	/** Show feed command. */
	private static final String SHOW_FEED = "show feed";
	/** Show post command. */
	private static final String SHOW_POST = "show post";
	/** Delete command. */
	private static final String DELETE = "delete";
	/** Rewin command. */
	private static final String REWIN = "rewin";
	/** Rate command. */
	private static final String RATE = "rate";
	/** Comment command. */
	private static final String COMMENT = "comment";
	/** Wallet command. */
	private static final String WALLET = "wallet";
	/** Wallet BTC command. */
	private static final String WALLET_BTC = "wallet btc";
	/** Used to parse JSON messages. */
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
			System.err.println("Usage: java -cp \".:./bin/:./libs/gson-2.8.6.jar\" ClientMain [<path/to/config>]");
			System.exit(1);
		}
		System.out.printf("LEGEND:\n\t%sCOLOR USED FOR FATAL ERRORS%s\n\t%sCOLOR USED FOR WARNINGS%s\n\t%sCOLOR USED FOR INFO MESSAGES%s\n\t%sCOLOR USED TO PRETTY PRINT OUTPUTS%s\n",
					Colors.ANSI_RED, Colors.ANSI_RESET, Colors.ANSI_YELLOW, Colors.ANSI_RESET, Colors.ANSI_CYAN, Colors.ANSI_RESET, Colors.ANSI_GREEN, Colors.ANSI_RESET);
		if (args.length == 0)
		{
			configFilename = "./configs/client.properties";
			System.out.printf("%sNo config files have been provided. Default will be used: %s%s.\n", Colors.ANSI_YELLOW, configFilename, Colors.ANSI_RESET);
		}
		else configFilename = args[0];

		try { configuration = new Configuration(new File(configFilename)); }
		catch (NullPointerException | IOException | InvalidConfigException  e)
		{
			System.err.println(Colors.ANSI_RED + "Fatal error occurred while parsing configuration: now aborting..." + Colors.ANSI_RESET);
			e.printStackTrace();
			System.exit(1);
		}
		try
		{
			channel = SocketChannel.open(new InetSocketAddress(configuration.serverAddress, configuration.portNoTCP));
			System.out.println(Colors.ANSI_CYAN + "Connected to server successfully!" + Colors.ANSI_RESET);
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
			System.err.println(Colors.ANSI_RED + "Fatal error occurred while setting up RMI callbacks." + Colors.ANSI_RESET);
			e.printStackTrace();
			System.exit(1);
		}
		System.out.printf("%sClient is now running...\nType \":q!\" to quit.%s\n", Colors.ANSI_CYAN, Colors.ANSI_RESET);

		/** Thread handling multicast messages. */
		Thread multicastWorker = null;
		final Scanner scanner = new Scanner(System.in);
		/** Set to the username this client has logged in with, null if none. */
		String loggedInUsername = null;
		/** Used to retrieve commands' return value. */
		int result = -1;
		/** Shared queue used to retrieve whichever messages have been received via multicast. */
		Queue<String> multicastMessages = new ConcurrentLinkedQueue<>();
		/** Shared boolean toggled on whenever this client has successfully logged in. */
		AtomicBoolean isLogged = new AtomicBoolean(false);

	loop:
		while(true)
		{
			if (loggedInUsername == null) System.out.printf("> ");
			else
			{
				// Check whether new messages have been received via multicast
				String message = null;
				{
					if (!multicastMessages.isEmpty())
					{
						System.out.println("< " + Colors.ANSI_CYAN + "New messages received:" + Colors.ANSI_RESET);
						while ((message = multicastMessages.poll()) != null) System.out.printf("\t%s\n", message);
					}
				}
				System.out.printf("%s> ", loggedInUsername);
			}

			final String s;
			s = scanner.nextLine();

			/** Used by certain commands to return values. */
			Set<String> destSet = new HashSet<>();
			/** Used by certain commands to return values. */
			StringBuilder destStringBuilder = new StringBuilder();

			switch (s)
			{
				case QUIT:
					break loop;

				case HELP:
					printHelpMessage();
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
							System.out.println("< " + Colors.ANSI_GREEN + loggedInUsername + " has now logged out." + Colors.ANSI_RESET);
							loggedInUsername = null;
							try
							{
								callback.unregisterForCallback(callbackObject);
								UnicastRemoteObject.unexportObject(callbackObject, true);
							}
							catch (RemoteException e)
							{
								System.err.println(Colors.ANSI_RED + "Fatal error occurred while cleaning up previously allocated resources." + Colors.ANSI_RESET);
								e.printStackTrace();
								System.exit(1);
							}
							callbackObject = null;
							isLogged.set(false);
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
							if (destSet.isEmpty()) System.out.println("< " + Colors.ANSI_GREEN + loggedInUsername + " does not share interests with any user." + Colors.ANSI_RESET);
							else
							{
								List<User> users = new ArrayList<>();
								destSet.forEach(d -> users.add(User.fromJSON(d)));
								printUsers(users);
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
					if (destSet.isEmpty()) System.out.println("< " + Colors.ANSI_GREEN + loggedInUsername + " is not followed by any user." + Colors.ANSI_RESET);
					else
					{
						List<User> users = new ArrayList<>();
						destSet.forEach(d -> users.add(User.fromJSON(d)));
						printUsers(users);
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
							List<User> users = new ArrayList<>();
							destSet.forEach(d -> users.add(User.fromJSON(d)));
							printUsers(users);
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
							if (destSet.isEmpty()) System.out.println("< " + Colors.ANSI_GREEN + loggedInUsername + " has yet to start posting." + Colors.ANSI_RESET);
							else
							{
								List<PostPreview> previews = new ArrayList<>();
								destSet.forEach(d -> previews.add(PostPreview.fromJSON(d)));
								printPreviews(previews);
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
							if (destSet.isEmpty()) System.out.println("< " + Colors.ANSI_GREEN + "The users " + loggedInUsername + " is following have yet to start posting." + Colors.ANSI_RESET);
							else
							{
								List<PostPreview> previews = new ArrayList<>();
								destSet.forEach(d -> previews.add(PostPreview.fromJSON(d)));
								printPreviews(previews);
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
							List<Transaction> transactions = new ArrayList<>();
							double total = 0;
							for (String t: destSet)
							{
								Transaction tmp = null;
								try
								{
									tmp = Transaction.fromJSON(t);
									transactions.add(tmp);
								}
								catch (JsonSyntaxException e) { total = Double.parseDouble(t); }
							}
							printTransactions(transactions, total);
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
							System.out.printf("< %sWallet value corresponds to %s BTC%s\n", Colors.ANSI_GREEN, destStringBuilder.toString(), Colors.ANSI_RESET);
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
						System.err.println(Colors.ANSI_YELLOW + "A new user cannot be registered before logging out." + Colors.ANSI_RESET);
						continue loop;
					}
					Set<String> tags = new HashSet<>();
					for (int i = 3; i < len; i++) tags.add(command[i]);
					try { Command.register(command[1], command[2], tags, configuration.portNoRegistry, configuration.registerServiceName, true); }
					catch (RemoteException | NotBoundException e)
					{
						System.err.println(Colors.ANSI_RED + "Fatal error occurred during registration, now aborting..." + Colors.ANSI_RESET);
						e.printStackTrace();
						System.exit(1);
					}
					catch (UsernameNotValidException | PasswordNotValidException | InvalidTagException | TagListTooLongException e)
					{
						System.err.printf("< %sGiven credentials do not meet the requirements%s:\n%s\n", Colors.ANSI_YELLOW, Colors.ANSI_RESET, e.getMessage());
						continue loop;
					}
					catch (UsernameAlreadyExistsException e)
					{
						System.err.println("< " + Colors.ANSI_YELLOW + "Username has already been taken." + Colors.ANSI_RESET);
						continue loop;
					}
					System.out.println("< " + Colors.ANSI_GREEN + "User has been registered successfully." + Colors.ANSI_RESET);
					continue loop;
				}

				case LOGIN:
				{
					if (len != 3)
					{
						System.err.println(INVALID_SYNTAX);
						continue loop;
					}
					StringBuilder sb = new StringBuilder();
					try { result = Command.login(command[1], command[2], channel, destSet, sb, true); }
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
							loggedInUsername = command[1];
							MulticastInfo info = MulticastInfo.fromJSON(sb.toString());
							if (info == null)
							{
								System.err.println(Colors.ANSI_RED + "Multicast address' info could not be parsed: now aborting..." + Colors.ANSI_RESET);
								System.exit(1);
							}
							System.out.println("< " + Colors.ANSI_GREEN + command[1] + " has now logged in." + Colors.ANSI_RESET);
							try
							{
								callbackObject = new RMIFollowersSet(destSet);
								callback.registerForCallback(callbackObject, loggedInUsername);
							}
							catch (RemoteException e)
							{
								System.err.println(Colors.ANSI_RED + "Fatal error occurred while setting up RMI callbacks: now aborting..." + Colors.ANSI_RESET);
								e.printStackTrace();
								System.exit(1);
							}
							isLogged.set(true);
							if (multicastWorker == null)
							{
								try 
								{
									MulticastSocket socket = new MulticastSocket(info.portNo);
									socket.setSoTimeout(500);
									socket.joinGroup(info.getAddress());
									multicastWorker = new Thread(new MulticastWorker(socket, info.getAddress(), isLogged, multicastMessages));
									multicastWorker.start();
								}
								catch (IOException e)
								{
									System.err.println(FATAL_IO);
									System.exit(1);
								}
							}
						}

					}
					continue loop;
				}

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
							System.out.println("< " + Colors.ANSI_GREEN + loggedInUsername + " has now started following " + command[1] + Colors.ANSI_RESET);
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
							System.out.println("< "+ Colors.ANSI_GREEN + loggedInUsername + " has now stopped following " + command[1] + Colors.ANSI_RESET);
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
							System.out.println("< " + Colors.ANSI_GREEN + "New post created: ID " + destStringBuilder.toString() + Colors.ANSI_RESET);
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
							System.out.printf("< %sID%s: %d\n< %sTitle%s: %s\n< %sContents%s:\n\t%s\n< %sUpvotes%s: %d - %sDownvotes%s: %d\n< %sRewon by%s: %d\n",
										Colors.ANSI_GREEN, Colors.ANSI_RESET, p.id, Colors.ANSI_GREEN, Colors.ANSI_RESET, p.title, Colors.ANSI_GREEN, Colors.ANSI_RESET, p.contents, Colors.ANSI_GREEN, Colors.ANSI_RESET,
										p.upvotes, Colors.ANSI_GREEN, Colors.ANSI_RESET, p.downvotes, Colors.ANSI_GREEN, Colors.ANSI_RESET, p.rewonBy.length);
							if (p.rewonBy.length != 0) System.out.print("\n\t");
							for (String r: p.rewonBy) System.out.printf("%s", r);
							System.out.printf("< %sComments%s: %d\n", Colors.ANSI_GREEN, Colors.ANSI_RESET, p.comments.length);
							for (Post.Comment c : p.comments) System.out.printf("\t%s:\n\t\t%s\n", c.author, c.contents);
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
							System.out.println("< "+ Colors.ANSI_GREEN + "Post has now been deleted." + Colors.ANSI_RESET);
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
							System.out.println("< " + Colors.ANSI_GREEN + "Post has now been rewon." + Colors.ANSI_RESET);
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
							System.out.println("< " + Colors.ANSI_GREEN + "New vote added." + Colors.ANSI_RESET);
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
							System.out.println("< " + Colors.ANSI_GREEN + "New comment added." + Colors.ANSI_RESET);
					}
					continue loop;


				default:
					System.out.println(INVALID_SYNTAX);
					break;
			}
		}
		System.out.println(Colors.ANSI_CYAN + "Client is now freeing resources..." + Colors.ANSI_RESET);
		try { scanner.close(); }
		catch (NullPointerException ignored) { }
		try { channel.close(); }
		catch (IOException ignored) { }
		catch (NullPointerException ignored) { }
		try
		{
			multicastWorker.interrupt();
			try { multicastWorker.join(500); }
			catch (InterruptedException ignored) { }
		}
		catch (NullPointerException ignored) { }
		try
		{
			callback.unregisterForCallback(callbackObject);
			UnicastRemoteObject.unexportObject(callbackObject, true);
		}
		catch (RemoteException ignored) { }
		catch (NullPointerException ignored) { }
		System.exit(0);
	}

	private static void printHelpMessage()
	{
		final String REGISTER = "register <username> <password> [tags]\n\t" + Colors.ANSI_CYAN +
					"Registers user to WINSOME, an optional list of a maximum of 5 tags can be specified.\n" + Colors.ANSI_RESET;
		final String LOGIN = "login <username> <password>\n\t" + Colors.ANSI_CYAN + "Logs in user using given credentials to WINSOME.\n" + Colors.ANSI_RESET;
		final String LOGOUT = "logout\n\t" + Colors.ANSI_CYAN + "Logs out user from WINSOME.\n" + Colors.ANSI_RESET;
		final String LISTUSERS = "list users\n\t" + Colors.ANSI_CYAN + "Lists out all the users sharing at least a common interest with you.\n" + Colors.ANSI_RESET;
		final String LISTFOLLOWERS = "list followers\n\t" + Colors.ANSI_CYAN + "Lists out all the users currently following you.\n" + Colors.ANSI_RESET;
		final String LISTFOLLOWING = "list following\n\t" + Colors.ANSI_CYAN + "Lists out all the users you are currently following.\n" + Colors.ANSI_RESET;
		final String FOLLOWUSER = "follow <username>\n\t" + Colors.ANSI_CYAN + "Makes you start following given user.\n" + Colors.ANSI_RESET;
		final String UNFOLLOWUSER = "unfollow <username>\n\t" + Colors.ANSI_CYAN + "Makes you stop following given user.\n" + Colors.ANSI_RESET;
		final String VIEWBLOG = "view blog\n\t" + Colors.ANSI_CYAN + "Lists out all posts uploaded by you.\n" + Colors.ANSI_RESET;
		final String CREATEPOST = "post <title> <content>\n\t" + Colors.ANSI_CYAN + "Makes you create a new post with given title and content.\n" + Colors.ANSI_RESET;
		final String SHOWFEED = "show feed\n\t" + Colors.ANSI_CYAN +
					"Lists out all the posts on your feed (i.e. all those written and/or rewon by a user you're currently following).\n" + Colors.ANSI_RESET;
		final String SHOWPOST = "show post <id>\n\t" + Colors.ANSI_CYAN + "Shows every detail about a post given its identifier.\n" + Colors.ANSI_RESET;
		final String DELETEPOST = "delete <id>\n\t" + Colors.ANSI_CYAN + "Makes you delete a post you've created before given its identifier.\n" + Colors.ANSI_RESET;
		final String REWINPOST = "rewin <id>\n\t" + Colors.ANSI_CYAN + "Makes you share on your blog a post on your feed given its identifier.\n" + Colors.ANSI_RESET;
		final String RATEPOST = "rate <id> <vote>\n\t" + Colors.ANSI_CYAN +
					"Makes you rate a post on your feed given its identifier. A vote must belong to {-1, 1} and can be cast only once.\n" + Colors.ANSI_RESET;
		final String ADDCOMMENT = "comment <id> <contents>\n\t" + Colors.ANSI_CYAN +
					"Makes you add a comment with given contents to a post on your feed given its identifier.\n" + Colors.ANSI_RESET;
		final String GETWALLET = "wallet\n\t" + Colors.ANSI_CYAN +
					"Lists out all transactions you've been involved with and the total amount of WINCOINS you own.\n" + Colors.ANSI_RESET;
		final String GETWALLETBTC = "wallet btc\n\t" + Colors.ANSI_CYAN +
					"Retrieves the value of the WINCOINS you own in Bitcoin based on the current currency exchange rate.\n" + Colors.ANSI_RESET;


		System.out.print(REGISTER + LOGIN + LOGOUT + LISTUSERS + LISTFOLLOWERS + LISTFOLLOWING + FOLLOWUSER + UNFOLLOWUSER +
					VIEWBLOG + CREATEPOST + SHOWFEED + SHOWPOST + DELETEPOST + REWINPOST + RATEPOST + ADDCOMMENT + GETWALLET + GETWALLETBTC);
	}

	private static String[] parseQuotes(final String quotedString)
	{
		List<String> list = new ArrayList<String>();
		Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(quotedString);
		while (m.find())
			list.add(m.group(1).replace("\"", ""));
		return list.toArray(new String[0]);
	}

	private static String centerString (final int width, final String s)
	{
	    return String.format("%-" + width  + "s", String.format("%" + (s.length() + (width - s.length()) / 2) + "s", s));
	}

	private static void printUsers(final List<User> users)
	{
		final String USERNAME = "USERNAME";
		final String TAGS = "TAGS";
		int maxNameLen = USERNAME.length();
		int maxTagsLen = TAGS.length();
		List<String> tags = new ArrayList<>();
		for (int i = 0; i < users.size(); i++)
		{
			// from an array of tags to a colored comma separated String
			final User u = users.get(i);
			int tagLen = 0;
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < u.tags.length; j++)
			{
				sb.append(Colors.ANSI_GREEN + u.tags[j] + Colors.ANSI_RESET);
				if (j != u.tags.length - 1)
				{
					sb.append(", ");
					tagLen += u.tags[j].length() + 2;
				}
				else tagLen += u.tags[j].length();
			}
			tags.add(sb.toString());
			// computing max length
			maxNameLen = Math.max(maxNameLen, u.username.length());
			maxTagsLen = Math.max(maxTagsLen, tagLen);
		}
		final String line = Stream.generate(() -> "-").limit(maxNameLen + maxTagsLen + 7).collect(Collectors.joining());
		System.out.printf("< %s\n", line);
		System.out.println(String.format("< | %s%s%s | %s%s%s |", Colors.ANSI_GREEN, centerString(maxNameLen, USERNAME),
					Colors.ANSI_RESET, Colors.ANSI_GREEN, centerString(maxTagsLen, TAGS), Colors.ANSI_RESET));
		System.out.printf("< %s\n", line);
		for (int i = 0; i < users.size(); i++)
		{
			System.out.println(String.format("< | %s%s%s | %s%s%s |", Colors.ANSI_GREEN, centerString(maxNameLen, users.get(i).username), Colors.ANSI_RESET,
						Colors.ANSI_GREEN, centerString(maxTagsLen, tags.get(i)), Colors.ANSI_RESET));
		}
		System.out.printf("< %s\n", line);
	}

	private static void printPreviews(final List<PostPreview> previews)
	{
		final String ID = "ID";
		final String AUTHOR = "AUTHOR";
		final String TITLE = "TITLE";
		int maxIDLen = ID.length();
		int maxAuthorLen = AUTHOR.length();
		int maxTitleLen = TITLE.length();
		for (PostPreview p: previews)
		{
			maxIDLen = Math.max(maxIDLen, String.valueOf(p.id).length());
			maxAuthorLen = Math.max(maxAuthorLen, p.author.length());
			maxTitleLen = Math.max(maxTitleLen, p.title.length());
		}
		final String line = Stream.generate(() -> "-").limit(maxIDLen + maxAuthorLen + maxTitleLen + 10).collect(Collectors.joining());
		System.out.printf("< %s\n", line);
		System.out.println(String.format("< | %s%s%s | %s%s%s | %s%s%s |", Colors.ANSI_GREEN, centerString(maxIDLen, ID), Colors.ANSI_RESET,
					Colors.ANSI_GREEN, centerString(maxAuthorLen, AUTHOR), Colors.ANSI_RESET, Colors.ANSI_GREEN, centerString(maxTitleLen, TITLE), Colors.ANSI_RESET));
		System.out.printf("< %s\n", line);
		for (PostPreview p: previews)
		{
			System.out.println(String.format("< | %s%s%s | %s%s%s | %s%s%s |", Colors.ANSI_GREEN, centerString(maxIDLen, Integer.toString(p.id)), Colors.ANSI_RESET,
						Colors.ANSI_GREEN, centerString(maxAuthorLen, p.author), Colors.ANSI_RESET, Colors.ANSI_GREEN, centerString(maxTitleLen, p.title), Colors.ANSI_RESET));
		}
		System.out.printf("< %s\n", line);
	}

	private static void printTransactions(final List<Transaction> transactions, final double total)
	{
		final String AMOUNT = "AMOUNT";
		final String TIMESTAMP = "TIMESTAMP";
		int maxAmountLen = AMOUNT.length();
		int maxTimestampLen = TIMESTAMP.length();
		for (Transaction t: transactions)
		{
			maxAmountLen = Math.max(maxAmountLen, String.valueOf(t.amount).length());
			maxTimestampLen = Math.max(maxTimestampLen, t.timestamp.length());
		}
		final String line = Stream.generate(() -> "-").limit(maxAmountLen + maxTimestampLen + 7).collect(Collectors.joining());
		System.out.printf("< %s\n", line);
		System.out.println(String.format("< | %s%s%s | %s%s%s |", Colors.ANSI_GREEN, centerString(maxAmountLen, AMOUNT), Colors.ANSI_RESET,
					Colors.ANSI_GREEN, centerString(maxTimestampLen, TIMESTAMP), Colors.ANSI_RESET));
		System.out.printf("< %s\n", line);
		for (Transaction t: transactions)
		{
				System.out.println(String.format("< | %s%s%s | %s%s%s |", Colors.ANSI_GREEN, centerString(maxAmountLen, Double.toString(t.amount)),
							Colors.ANSI_RESET, Colors.ANSI_GREEN, centerString(maxTimestampLen, t.timestamp), Colors.ANSI_RESET));
		}
		System.out.printf("< %s\n", line);
		System.out.printf("< %sTOTAL%s: %f WINCOINS\n", Colors.ANSI_GREEN, Colors.ANSI_RESET, total);
	}
}