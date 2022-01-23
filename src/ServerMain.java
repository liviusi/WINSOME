import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.BufferOverflowException;
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
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import api.CommandCode;
import api.Communication;
import api.ResponseCode;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import configuration.InvalidConfigException;
import configuration.ServerConfiguration;
import server.BackupTask;
import server.RMICallbackService;
import server.RMITask;
import server.RewardsTask;
import server.post.InvalidCommentException;
import server.post.InvalidGeneratorException;
import server.post.InvalidPostException;
import server.post.InvalidVoteException;
import server.post.Post.Vote;
import server.storage.IllegalArchiveException;
import server.storage.NoSuchPostException;
import server.storage.NoSuchUserException;
import server.storage.PostMap;
import server.storage.PostStorage;
import server.storage.UserMap;
import server.storage.UserStorage;
import user.InvalidLoginException;
import user.InvalidLogoutException;
import user.SameUserException;
import user.WrongCredentialsException;

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
		private UserStorage users = null;
		private PostStorage posts = null;
		private Map<SocketChannel, String> loggedInClients = null;
		private RMICallbackService callbackService = null;

		private static final String LOGIN_SUCCESS = " has now logged in";
		private static final String CLIENT_ALREADY_LOGGED_IN = "Client has already logged in";
		private static final String LOGOUT_SUCCESS = " has now logged out";
		private static final String FOLLOW_SUCCESS = " is now following ";
		private static final String UNFOLLOW_SUCCESS = " has now stopped following ";
		private static final String NOT_FOLLOWING = " is not following ";

		public RequestHandler(final Set<SetElement> toBeRegistered, final Selector selector,
				final SelectionKey key, final UserStorage users, final PostStorage posts, Map<SocketChannel, String> loggedInClients,
				final RMICallbackService callbackService)
		{
			this.toBeRegistered = toBeRegistered;
			this.selector = selector;
			this.key = key;
			this.users = users;
			this.posts = posts;
			this.loggedInClients = loggedInClients;
			this.callbackService = callbackService;
		}

		public void run()
		{
			ByteBuffer buffer = (ByteBuffer) key.attachment();
			SocketChannel client = (SocketChannel) key.channel();
			int nRead = 0;
			int size = BUFFERSIZE;
			JsonObject jsonMessage = null;
			JsonElement elem = null;
			ByteArrayOutputStream answerConstructor = new ByteArrayOutputStream();
			StringBuilder sb = new StringBuilder();
			final String username;
			String message = null;
			String clientName = null;
			boolean exceptionCaught = false;
	
			buffer.flip();
			buffer.clear();
			try { nRead = Communication.receiveMessage(client, buffer, sb); }
			catch (ClosedChannelException e) { return; }
			catch (IOException e)
			{
				System.err.printf("I/O error occurred:\n%s\n", e.getMessage());
				try { client.close(); }
				catch (IOException ignored) { }
				return;
			}
			if (nRead == -1) // client forcibly disconnected
			{
				username = loggedInClients.get(client);
				if (username != null)
				{
					loggedInClients.remove(client);
					try { users.handleLogout(username, client); }
					catch (InvalidLogoutException ignored) { }
					catch (NoSuchUserException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
				}
				try { client.close(); }
				catch (IOException ignored) { }
			}
			else if (nRead == 0) return;
			else // read has not failed:
			{
				buffer.flip();
				buffer.clear();
				message = sb.toString();
				clientName = Integer.toString(client.hashCode());
				System.out.printf("> client %s: \"%s\"\n", clientName, message);
				jsonMessage = new Gson().fromJson(message, JsonObject.class);
				elem = jsonMessage.get("command");
				if (elem == null) syntaxErrorHandler(answerConstructor);
				else
				{
					if (elem.getAsString().equals(CommandCode.LOGINATTEMPT.description))
					{
						if (loggedInClients.containsKey(client))
						{
							try
							{
								answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
								answerConstructor.write(CLIENT_ALREADY_LOGGED_IN.getBytes(StandardCharsets.US_ASCII));
							}
							catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
						}
						else
						{
							elem = jsonMessage.get("username");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								username = elem.getAsString();
								elem = jsonMessage.get("hashedpassword");
								if (elem == null) syntaxErrorHandler(answerConstructor);
								else
								{
									String hashedPassword = elem.getAsString();
									try { users.handleLogin(username, client, hashedPassword); }
									catch (InvalidLoginException | WrongCredentialsException | NoSuchUserException e)
									{
										exceptionCaught = true;
										try
										{
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									catch (NullPointerException e)
									{
										exceptionCaught = true;
										try { answerConstructor.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII)); }
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										try
										{
											answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write((username + LOGIN_SUCCESS).getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										loggedInClients.put(client, username);
									}
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.LOGINSETUP.description))
					{
						if (loggedInClients.containsKey(client))
						{
							try
							{
								answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
								answerConstructor.write(CLIENT_ALREADY_LOGGED_IN.getBytes(StandardCharsets.US_ASCII));
							}
							catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
						}
						else
						{
							elem = jsonMessage.get("username");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								username = elem.getAsString();
								String salt = null;
								try { salt = users.handleLoginSetup(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.NOT_FOUND.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(salt.getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.PULLFOLLOWERS.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								Set<String> result = null;
								try { result = users.recoverFollowers(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.NOT_FOUND.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.LOGOUT.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								try { users.handleLogout(username, client); }
								catch (InvalidLogoutException | NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write((username + LOGOUT_SUCCESS).getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									loggedInClients.remove(client);
								}
							}
							else invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.LISTUSERS.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								Set<String> result = null;
								try { result = users.handleListUsers(username); }
								catch (NoSuchUserException | NullPointerException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.LISTFOLLOWING.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								Set<String> result = null;
								try { result = users.handleListFollowing(username); }
								catch (NoSuchUserException | NullPointerException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.FOLLOWUSER.description))
					{
						elem = jsonMessage.get("follower");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("followed");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								final String followed = elem.getAsString();
								if (loggedInClients.get(client).equals(username))
								{
									boolean result = false;
									try { result = users.handleFollowUser(username, followed); }
									catch (IllegalArgumentException | NoSuchUserException | SameUserException e)
									{
										exceptionCaught = true;
										try
										{
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										if (!result)
										{
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + " is already following " + followed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											callbackService.notifyNewFollower(username, followed);
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + FOLLOW_SUCCESS + followed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.UNFOLLOWUSER.description))
					{
						elem = jsonMessage.get("follower");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("followed");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								final String unfollowed = elem.getAsString();
								boolean result = false;
								if (loggedInClients.get(client).equals(username))
								{
									try { result = users.handleUnfollowUser(username, unfollowed); }
									catch (IllegalArgumentException | NullPointerException | NoSuchUserException e)
									{
										exceptionCaught = true;
										try
										{
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										if (!result)
										{
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + NOT_FOLLOWING + unfollowed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											callbackService.notifyUnfollow(username, unfollowed);
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + UNFOLLOW_SUCCESS + unfollowed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.VIEWBLOG.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							Set<String> result = null;
							result = posts.handleBlog(username);
							try
							{
								answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
								size = SetToByteArray(result, answerConstructor);
							}
							catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
						}
					}
					else if (elem.getAsString().equals(CommandCode.CREATEPOST.description))
					{
						elem = jsonMessage.get("author");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("title");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								final String title = elem.getAsString();
								elem = jsonMessage.get("contents");
								if (elem == null) syntaxErrorHandler(answerConstructor);
								else
								{
									final String contents = elem.getAsString();
									if (loggedInClients.get(client).equals(username))
									{
										int postID = -1;
										try { postID = posts.handleCreatePost(username, title, contents); }
										catch (InvalidPostException e)
										{
											exceptionCaught = true;
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										catch (InvalidGeneratorException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										if (!exceptionCaught)
										{
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + " has now created a new post: " + postID).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
									else invalidUsernameHandler(answerConstructor, username);
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.SHOWFEED.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								Set<String> result = null;
								try { result = posts.handleShowFeed(username, users); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.SHOWPOST.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("postid");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								if (loggedInClients.get(client).equals(username))
								{
									String result = null;
									try { result = posts.handleShowPost(Integer.parseInt(elem.getAsString())); }
									catch (NumberFormatException e)
									{
										exceptionCaught = true;
										syntaxErrorHandler(answerConstructor);
									}
									catch (NoSuchPostException e)
									{
										exceptionCaught = true;
										try
										{
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										try
										{
											answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(result.getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
								}
								else invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.DELETEPOST.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("postid");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								if (loggedInClients.get(client).equals(username))
								{
									boolean result = false;
									try { result = posts.handleDeletePost(username, Integer.parseInt(elem.getAsString())); }
									catch (NumberFormatException e)
									{
										exceptionCaught = true;
										syntaxErrorHandler(answerConstructor);
									}
									catch (NoSuchPostException e)
									{
										exceptionCaught = true;
										try
										{
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										if (result)
										{
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post has now been deleted.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post could not be deleted.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.REWIN.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("postid");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								if (loggedInClients.get(client).equals(username))
								{
									boolean result = false;
									try { result = posts.handleRewin(username, users, Integer.parseInt(elem.getAsString())); }
									catch (NumberFormatException e)
									{
										exceptionCaught = true;
										syntaxErrorHandler(answerConstructor);
									}
									catch (NoSuchPostException e)
									{
										exceptionCaught = true;
										try
										{
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										if (result)
										{
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post has now been rewon.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post could not be rewon.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.RATE.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("vote");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								final String vote = elem.getAsString();
								elem = jsonMessage.get("postid");
								if (elem == null) syntaxErrorHandler(answerConstructor);
								else
								{
									if (loggedInClients.get(client).equals(username))
									{
										try { posts.handleRate(username, users, Integer.parseInt(elem.getAsString()), Vote.fromValue(Integer.parseInt(vote))); }
										catch (NumberFormatException e)
										{
											exceptionCaught = true;
											syntaxErrorHandler(answerConstructor);
										}
										catch (NoSuchPostException | InvalidVoteException e)
										{
											exceptionCaught = true;
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										if (!exceptionCaught)
										{
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Vote has now been cast.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
									else invalidUsernameHandler(answerConstructor, username);
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.COMMENT.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = jsonMessage.get("contents");
							if (elem == null) syntaxErrorHandler(answerConstructor);
							else
							{
								final String contents = elem.getAsString();
								elem = jsonMessage.get("postid");
								if (elem == null) syntaxErrorHandler(answerConstructor);
								else
								{
									if (loggedInClients.get(client).equals(username))
									{
										try { posts.handleAddComment(username, users, Integer.parseInt(elem.getAsString()), contents); }
										catch (NumberFormatException e)
										{
											exceptionCaught = true;
											syntaxErrorHandler(answerConstructor);
										}
										catch (InvalidCommentException | NoSuchPostException e)
										{
											exceptionCaught = true;
											try
											{
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										if (!exceptionCaught)
										{
											try
											{
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Comment has now been added.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
									else invalidUsernameHandler(answerConstructor, username);
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.WALLET.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								Set<String> result = null;
								try { result = users.handleGetWallet(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.WALLETBTC.description))
					{
						elem = jsonMessage.get("username");
						if (elem == null) syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInClients.get(client).equals(username))
							{
								String result = null;
								try { result = users.handleGetWalletInBitcoin(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								catch (IOException | NumberFormatException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								if (!exceptionCaught)
								{
									try
									{
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(result.getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else invalidUsernameHandler(answerConstructor, username);
						}
					}
					else syntaxErrorHandler(answerConstructor);

					putByteArray(buffer, size, answerConstructor.toByteArray());
					buffer.flip();

					toBeRegistered.add(new SetElement(client, SelectionKey.OP_WRITE, buffer));
					selector.wakeup();
					return;
				}
			}
		}

		private static <T> int SetToByteArray(Set<T> set, ByteArrayOutputStream dst)
		throws IOException
		{
			int size = BUFFERSIZE;
			ByteBuffer buffer = ByteBuffer.allocate(size);
			byte[] tmp = null;
			for (T item: set)
			{
				tmp = item.toString().getBytes(StandardCharsets.US_ASCII);
				size = putByteArray(buffer, size, tmp);
			}
			byte[] array = new byte[buffer.position()];
			buffer.flip();
			buffer.get(array);
			dst.write(array);
			return size;
		}

		private static int putByteArray(ByteBuffer dst, int buffersize, byte[] src)
		{
			ByteBuffer tmp = null;
			int newSize = buffersize;
			while (true)
			{
				try
				{
					dst.putInt(src.length);
					break;
				}
				catch (BufferOverflowException e)
				{
					newSize *= 2;
					tmp = ByteBuffer.allocate(newSize);
					tmp.put(dst);
					dst = tmp;
				}
			}
			while (true)
			{
				try
				{
					dst.put(src);
					break;
				}
				catch (BufferOverflowException e)
				{
					newSize *= 2;
					tmp = ByteBuffer.allocate(newSize);
					tmp.put(dst);
					dst = tmp;
				}
			}
			return newSize;
		}

		private static void syntaxErrorHandler(ByteArrayOutputStream baos)
		{
			try
			{
				baos.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII));
				baos.write("Syntax error.".getBytes(StandardCharsets.US_ASCII));
			}
			catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
		}

		private static void invalidUsernameHandler(ByteArrayOutputStream baos, final String username)
		{
			try
			{
				baos.write(ResponseCode.NOT_FOUND.getDescription().getBytes(StandardCharsets.US_ASCII));
				baos.write(String.format("User %s is not logged in with this client", username).getBytes(StandardCharsets.US_ASCII));
			}
			catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
		}
	}

	private static class MessageDispatcher implements Runnable
	{
		private Set<SetElement> toBeRegistered = null;
		private Selector selector = null;
		private SelectionKey key = null;
		private UserStorage users = null;
		private Map<SocketChannel, String> loggedInClients = null;

		public MessageDispatcher(final Set<SetElement> toBeRegistered, final Selector selector,
				final SelectionKey key, final UserStorage users, Map<SocketChannel, String> loggedInClients)
		{
			this.toBeRegistered = toBeRegistered;
			this.selector = selector;
			this.key = key;
			this.loggedInClients = loggedInClients;
		}

		public void run()
		{
			SocketChannel client = (SocketChannel) key.channel();
			ByteBuffer buffer = (ByteBuffer) key.attachment();
			String username = null;

			try { client.configureBlocking(false); }
			catch (IOException e)
			{
				username = loggedInClients.get(client);
				if (username != null)
				{
					loggedInClients.remove(client);
					try { users.handleLogout(username, client); }
					catch (InvalidLogoutException ignored) { }
					catch (NoSuchUserException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
				}
				try { client.close(); }
				catch (IOException ignored) { }
			}
			try { client.write(buffer); }
			catch (IOException e) // client forcibly disconnected
			{
				username = loggedInClients.get(client);
				if (username != null)
				{
					loggedInClients.remove(client);
					try { users.handleLogout(username, client); }
					catch (InvalidLogoutException ignored) { }
					catch (NoSuchUserException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
				}
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
		UserStorage users = null;
		try { users = UserMap.fromJSON(new File("./storage/users.json"), new File("./storage/following.json")); }
		catch (FileNotFoundException | IllegalArchiveException e) { users = new UserMap(); }
		catch (IOException e)
		{
			System.err.println("Fatal error occurred while parsing user storage: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
		PostStorage posts = null;
		try { posts = PostMap.fromJSON(new File("./storage/posts.json"), new File("./storage/posts-interactions.json"), users); }
		catch (IOException | IllegalArchiveException | InvalidGeneratorException e)
		{
			posts = new PostMap();
		}
		RMICallbackService callbackService = null;
		try  { callbackService = new RMICallbackService(); }
		catch (RemoteException e)
		{
			System.err.println("Fatal error occurred while setting up RMI callbacks.");
			System.exit(1);
		}
		Thread rmi = new Thread(new RMITask(configuration, (UserMap) users, callbackService));
		rmi.start();
		Thread backup = new Thread(new BackupTask(configuration, users, posts));
		backup.start();
		Thread rewards = new Thread(new RewardsTask(users, posts));
		rewards.start();

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
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			public void run()
			{
				System.out.printf("\nServer has now entered shutdown mode.\n");
				rmi.interrupt();
				rewards.interrupt();
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
				try { backup.join(500); }
				catch (InterruptedException e) { }
				try { rmi.join(500); }
				catch (InterruptedException e) { }
				try { rewards.join(500); }
				catch (InterruptedException e) { }
			}
		});
		System.out.println("Server is now running...");

		Map<SocketChannel, String> loggedInClients = new ConcurrentHashMap<>();
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
							System.out.println("New client accepted: " + client.hashCode());
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
						threadPool.execute(new Thread(new RequestHandler(toBeRegistered, selector, k, users, posts, loggedInClients, callbackService)));
					}
					if (k.isWritable())
					{
						k.cancel();
						threadPool.execute(new Thread(new MessageDispatcher(toBeRegistered, selector, k, users, loggedInClients)));
					}
				}
				catch (CancelledKeyException e) { continue; }
			}
		}
	}
}