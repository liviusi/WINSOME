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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
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
import server.LoggingTask;
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
import server.user.InvalidLoginException;
import server.user.InvalidLogoutException;
import server.user.SameUserException;
import server.user.WrongCredentialsException;

/**
 * Server main file.
 * @author Giacomo Trapani
 */
public class ServerMain
{
	/** Default size for ByteBuffers. */
	private static final int BUFFERSIZE = 1024;
	/** Part of the error message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";
	/** Used to store multicast address and port as a byte array. */
	private static byte[] multicastInfoBytes;

	/** Used to group together all the information needed for a thread to be dispatched to handle a certain client's request. */
	private static class SetElement
	{
		/** Channel denoting the client. */
		final SocketChannel client;
		/** OP_CODE of the operation to be performed on this client. */
		final int operation;
		/** ByteBuffer to be used when handling this request. */
		final ByteBuffer buffer;

		/**
		 * Default constructor.
		 * @param client cannot be null.
		 * @param operation must be either OP_READ or OP_WRITE.
		 * @param buffer cannot be null.
		 * @throws IllegalArgumentException if operation is neither OP_READ or OP_WRITE.
		 * @throws NullPointerException if any parameter is null
		 */
		public SetElement(final SocketChannel client, final int operation, final ByteBuffer buffer)
		throws IllegalArgumentException
		{
			if (operation != SelectionKey.OP_READ && operation != SelectionKey.OP_WRITE)
				throw new IllegalArgumentException("Operation specified is not valid. Only OP_READ and OP_WRITE are permitted.");
			this.client = Objects.requireNonNull(client, "Client" + NULL_ERROR);
			this.operation = operation;
			this.buffer = Objects.requireNonNull(buffer, "Buffer" + NULL_ERROR);
		}
	}

	/** Used to group together the whole logic for a task to handle a certain client's request. */
	private static class RequestHandler implements Runnable
	{
		/** Pointer to the set of clients to be registered for next select's iteration. */
		private Set<SetElement> toBeRegistered = null;
		/** Pointer to selector. It is used to wake it up after handling the request. */
		private Selector selector = null;
		/** Pointer to the key denoting this client. */
		private SelectionKey key = null;
		/** Pointer to user storage. */
		private UserStorage users = null;
		/** Pointer to post storage. */
		private PostStorage posts = null;
		/** Pointer to the map storing the couples (client, username they have logged in with). */
		private Map<SocketChannel, String> loggedInClients = null;
		/** Pointer to the RMI callbackService. */
		private RMICallbackService callbackService = null;
		/** Pointer to the blocking queue shared with the logging thread. */
		private BlockingQueue<String> logQueue = null;

		private static final String CLIENT_ALREADY_LOGGED_IN = "Client has already logged in";

		/** Default constructor. */
		public RequestHandler(final Set<SetElement> toBeRegistered, final Selector selector,
				final SelectionKey key, final UserStorage users, final PostStorage posts, Map<SocketChannel, String> loggedInClients,
				final RMICallbackService callbackService, BlockingQueue<String> logQueue)
		{
			this.toBeRegistered = Objects.requireNonNull(toBeRegistered, "Set" + NULL_ERROR);
			this.selector = Objects.requireNonNull(selector, "Selector" + NULL_ERROR);
			this.key = Objects.requireNonNull(key, "Key" + NULL_ERROR);
			this.users = Objects.requireNonNull(users, "Users storage" + NULL_ERROR);
			this.posts = Objects.requireNonNull(posts, "Posts storage" + NULL_ERROR);
			this.loggedInClients = Objects.requireNonNull(loggedInClients, "Logged in clients" + NULL_ERROR);
			this.callbackService = Objects.requireNonNull(callbackService, "Callback service" + NULL_ERROR);
			this.logQueue = Objects.requireNonNull(logQueue, "Queue" + NULL_ERROR);
		}

		public void run()
		{
			/** Attachment buffer. */
			ByteBuffer buffer = (ByteBuffer) key.attachment();
			/** Client channel. */
			SocketChannel client = (SocketChannel) key.channel();
			/** Result of reading operations. */
			int nRead = 0;
			/** Size of the buffer. */
			int size = BUFFERSIZE;
			/** Used to parse request. */
			JsonObject JSONMessage = null;
			/** Used to parse JSONMessage. */
			JsonElement elem = null;
			/** Used to build up the answer to be sent back. */
			ByteArrayOutputStream answerConstructor = new ByteArrayOutputStream();
			/** Used to read from client. */
			StringBuilder sb = new StringBuilder();
			/** Username of the user this client is claiming to be logged in with. */
			final String username;
			/** Username of the user this client is currently logged in with. */
			final String loggedInUsername = loggedInClients.get(client);
			/** Request message. */
			String message = null;
			/** Toggled on if the request has triggered an exception in any of the storages used. */
			boolean exceptionCaught = false;
			/** Used to build up the message to be logged. */
			StringBuilder logMessageBuilder = new StringBuilder();

			logMessageBuilder.append(String.format("[%s][THREAD %d][CLIENT %d]",
						DateTimeFormatter.ofPattern("dd MMM. YYYY - HH:mm:ss").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.now()),
						Thread.currentThread().getId(), client.hashCode())
			);
			if (loggedInUsername != null) logMessageBuilder.append(String.format("[%s]", loggedInUsername));

			buffer.flip();
			buffer.clear();
			try { nRead = Communication.receiveMessage(client, buffer, sb); }
			catch (ClosedChannelException e) { return; }
			catch (IOException e)
			{
				if (loggedInUsername != null)
				{
					loggedInClients.remove(client);
					try { users.handleLogout(loggedInUsername, client); }
					catch (InvalidLogoutException ignored) { }
					catch (NoSuchUserException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
				}
				logMessageBuilder.append(String.format("[I/O ERROR %s][DISCONNECTION]\n", e.getMessage()));
				try { client.close(); }
				catch (IOException ignored) { }
				logQueue.offer(logMessageBuilder.toString());
				return;
			}
			if (nRead == -1) // client forcibly disconnected
			{
				if (loggedInUsername != null)
				{
					loggedInClients.remove(client);
					try { users.handleLogout(loggedInUsername, client); }
					catch (InvalidLogoutException ignored) { }
					catch (NoSuchUserException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
				}
				logMessageBuilder.append("[DISCONNECTION]\n");
				try { client.close(); }
				catch (IOException ignored) { }
				logQueue.offer(logMessageBuilder.toString());
				return;
			}
			else if (nRead == 0) return;
			else // read has not failed:
			{
				buffer.flip();
				buffer.clear();
				message = sb.toString();
				/** Code to be appended in the log. */
				ResponseCode code = null;
				logMessageBuilder.append(String.format("[%s]", message));
				JSONMessage = new Gson().fromJson(message, JsonObject.class);
				elem = JSONMessage.get("command");
				if (elem == null) code = syntaxErrorHandler(answerConstructor);
				else
				{
					if (elem.getAsString().equals(CommandCode.LOGINATTEMPT.description))
					{
						if (loggedInClients.containsKey(client))
						{
							try
							{
								code = ResponseCode.FORBIDDEN;
								answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
								answerConstructor.write(CLIENT_ALREADY_LOGGED_IN.getBytes(StandardCharsets.US_ASCII));
							}
							catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
						}
						else
						{
							elem = JSONMessage.get("username");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								username = elem.getAsString();
								elem = JSONMessage.get("hashedpassword");
								if (elem == null) code = syntaxErrorHandler(answerConstructor);
								else
								{
									String hashedPassword = elem.getAsString();
									try { users.handleLogin(username, client, hashedPassword); }
									catch (InvalidLoginException | WrongCredentialsException | NoSuchUserException e)
									{
										exceptionCaught = true;
										try
										{
											code = ResponseCode.FORBIDDEN;
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									catch (NullPointerException e)
									{
										exceptionCaught = true;
										code = ResponseCode.BAD_REQUEST;
										try { answerConstructor.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII)); }
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										try
										{
											code = ResponseCode.OK;
											answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write((username + " has now logged in.").getBytes(StandardCharsets.US_ASCII));
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
								code = ResponseCode.FORBIDDEN;
								answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
								answerConstructor.write(CLIENT_ALREADY_LOGGED_IN.getBytes(StandardCharsets.US_ASCII));
							}
							catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
						}
						else
						{
							elem = JSONMessage.get("username");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
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
										code = ResponseCode.NOT_FOUND;
										answerConstructor.write(ResponseCode.NOT_FOUND.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
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
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								Set<String> result = null;
								try { result = users.recoverFollowers(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.NOT_FOUND;
										answerConstructor.write(ResponseCode.NOT_FOUND.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.RETRIEVEMULTICAST.description))
					{
						try
						{
							code = ResponseCode.OK;
							answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
							answerConstructor.write(multicastInfoBytes);
						}
						catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
					}
					else if (elem.getAsString().equals(CommandCode.LOGOUT.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								try { users.handleLogout(username, client); }
								catch (InvalidLogoutException | NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.FORBIDDEN;
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write((username + " has now logged out").getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									loggedInClients.remove(client);
								}
							}
							else code = invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.LISTUSERS.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								Set<String> result = null;
								try { result = users.handleListUsers(username); }
								catch (NoSuchUserException | NullPointerException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.BAD_REQUEST;
										answerConstructor.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else code = invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.LISTFOLLOWING.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								Set<String> result = null;
								try { result = users.handleListFollowing(username); }
								catch (NoSuchUserException | NullPointerException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.BAD_REQUEST;
										answerConstructor.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else code = invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.FOLLOWUSER.description))
					{
						elem = JSONMessage.get("follower");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("followed");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								final String followed = elem.getAsString();
								if (loggedInUsername.equals(username))
								{
									boolean result = false;
									try { result = users.handleFollowUser(username, followed); }
									catch (IllegalArgumentException | NoSuchUserException | SameUserException e)
									{
										exceptionCaught = true;
										try
										{
											code = ResponseCode.FORBIDDEN;
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
												code = ResponseCode.FORBIDDEN;
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
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + " is now following " + followed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else code = invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.UNFOLLOWUSER.description))
					{
						elem = JSONMessage.get("follower");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("followed");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								final String unfollowed = elem.getAsString();
								boolean result = false;
								if (loggedInUsername.equals(username))
								{
									try { result = users.handleUnfollowUser(username, unfollowed); }
									catch (IllegalArgumentException | NullPointerException | NoSuchUserException e)
									{
										exceptionCaught = true;
										try
										{
											code = ResponseCode.FORBIDDEN;
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
												code = ResponseCode.FORBIDDEN;
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + " is not following " + unfollowed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											callbackService.notifyUnfollow(username, unfollowed);
											try
											{
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + " has now stopped following " + unfollowed).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else code = invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.VIEWBLOG.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							Set<String> result = null;
							result = posts.handleBlog(username);
							try
							{
								code = ResponseCode.OK;
								answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
								size = SetToByteArray(result, answerConstructor);
							}
							catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
						}
					}
					else if (elem.getAsString().equals(CommandCode.CREATEPOST.description))
					{
						elem = JSONMessage.get("author");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("title");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								final String title = elem.getAsString();
								elem = JSONMessage.get("contents");
								if (elem == null) code = syntaxErrorHandler(answerConstructor);
								else
								{
									final String contents = elem.getAsString();
									if (loggedInUsername.equals(username))
									{
										int postID = -1;
										try { postID = posts.handleCreatePost(username, title, contents); }
										catch (InvalidPostException e)
										{
											exceptionCaught = true;
											try
											{
												code = ResponseCode.FORBIDDEN;
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
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write((username + " has now created a new post: " + postID).getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
									else code = invalidUsernameHandler(answerConstructor, username);
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.SHOWFEED.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								Set<String> result = null;
								try { result = posts.handleShowFeed(username, users); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.FORBIDDEN;
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else code = invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.SHOWPOST.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("postid");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								if (loggedInUsername.equals(username))
								{
									String result = null;
									try { result = posts.handleShowPost(Integer.parseInt(elem.getAsString())); }
									catch (NumberFormatException e)
									{
										exceptionCaught = true;
										code = syntaxErrorHandler(answerConstructor);
									}
									catch (NoSuchPostException e)
									{
										exceptionCaught = true;
										try
										{
											code = ResponseCode.FORBIDDEN;
											answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
									if (!exceptionCaught)
									{
										try
										{
											code = ResponseCode.OK;
											answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
											answerConstructor.write(result.getBytes(StandardCharsets.US_ASCII));
										}
										catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
									}
								}
								else code = invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.DELETEPOST.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("postid");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								if (loggedInUsername.equals(username))
								{
									boolean result = false;
									try { result = posts.handleDeletePost(username, Integer.parseInt(elem.getAsString())); }
									catch (NumberFormatException e)
									{
										exceptionCaught = true;
										code = syntaxErrorHandler(answerConstructor);
									}
									catch (NoSuchPostException e)
									{
										exceptionCaught = true;
										try
										{
											code = ResponseCode.FORBIDDEN;
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
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post has now been deleted.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											try
											{
												code = ResponseCode.FORBIDDEN;
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post could not be deleted.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else code = invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.REWIN.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("postid");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								if (loggedInUsername.equals(username))
								{
									boolean result = false;
									try { result = posts.handleRewin(username, users, Integer.parseInt(elem.getAsString())); }
									catch (NumberFormatException e)
									{
										exceptionCaught = true;
										code = syntaxErrorHandler(answerConstructor);
									}
									catch (NoSuchPostException e)
									{
										exceptionCaught = true;
										try
										{
											code = ResponseCode.FORBIDDEN;
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
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post has now been rewon.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										else
										{
											try
											{
												code = ResponseCode.FORBIDDEN;
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Post could not be rewon.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
								}
								else code = invalidUsernameHandler(answerConstructor, username);
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.RATE.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("vote");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								final String vote = elem.getAsString();
								elem = JSONMessage.get("postid");
								if (elem == null) code = syntaxErrorHandler(answerConstructor);
								else
								{
									if (loggedInUsername.equals(username))
									{
										try { posts.handleRate(username, users, Integer.parseInt(elem.getAsString()), Vote.fromValue(Integer.parseInt(vote))); }
										catch (NumberFormatException e)
										{
											exceptionCaught = true;
											code = syntaxErrorHandler(answerConstructor);
										}
										catch (NoSuchPostException | InvalidVoteException e)
										{
											exceptionCaught = true;
											try
											{
												code = ResponseCode.FORBIDDEN;
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										if (!exceptionCaught)
										{
											try
											{
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Vote has now been cast.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
									else code = invalidUsernameHandler(answerConstructor, username);
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.COMMENT.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							elem = JSONMessage.get("contents");
							if (elem == null) code = syntaxErrorHandler(answerConstructor);
							else
							{
								final String contents = elem.getAsString();
								elem = JSONMessage.get("postid");
								if (elem == null) code = syntaxErrorHandler(answerConstructor);
								else
								{
									if (loggedInUsername.equals(username))
									{
										try { posts.handleAddComment(username, users, Integer.parseInt(elem.getAsString()), contents); }
										catch (NumberFormatException e)
										{
											exceptionCaught = true;
											code = syntaxErrorHandler(answerConstructor);
										}
										catch (InvalidCommentException | NoSuchPostException e)
										{
											exceptionCaught = true;
											try
											{
												code = ResponseCode.FORBIDDEN;
												answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
										if (!exceptionCaught)
										{
											try
											{
												code = ResponseCode.OK;
												answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
												answerConstructor.write("Comment has now been added.".getBytes(StandardCharsets.US_ASCII));
											}
											catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
										}
									}
									else code = invalidUsernameHandler(answerConstructor, username);
								}
							}
						}
					}
					else if (elem.getAsString().equals(CommandCode.WALLET.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								Set<String> result = null;
								try { result = users.handleGetWallet(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.FORBIDDEN;
										answerConstructor.write(ResponseCode.FORBIDDEN.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(e.getMessage().getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
								if (!exceptionCaught)
								{
									try
									{
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										size = SetToByteArray(result, answerConstructor);
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else code = invalidUsernameHandler(answerConstructor, username);
						}
					}
					else if (elem.getAsString().equals(CommandCode.WALLETBTC.description))
					{
						elem = JSONMessage.get("username");
						if (elem == null) code = syntaxErrorHandler(answerConstructor);
						else
						{
							username = elem.getAsString();
							if (loggedInUsername.equals(username))
							{
								String result = null;
								try { result = users.handleGetWalletInBitcoin(username); }
								catch (NoSuchUserException e)
								{
									exceptionCaught = true;
									try
									{
										code = ResponseCode.FORBIDDEN;
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
										code = ResponseCode.OK;
										answerConstructor.write(ResponseCode.OK.getDescription().getBytes(StandardCharsets.US_ASCII));
										answerConstructor.write(result.getBytes(StandardCharsets.US_ASCII));
									}
									catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
								}
							}
							else code = invalidUsernameHandler(answerConstructor, username);
						}
					}
					else code = syntaxErrorHandler(answerConstructor);

					logMessageBuilder.append(String.format("[%d]\n", code.getValue()));

					putByteArray(buffer, size, answerConstructor.toByteArray());
					buffer.flip();

					toBeRegistered.add(new SetElement(client, SelectionKey.OP_WRITE, buffer));
					selector.wakeup();
					logQueue.offer(logMessageBuilder.toString());
					return;
				}
			}
		}

		/**
		 * Method used to convert a set to a byte array following the syntax CONCAT(LENGTH, ITEM) with ITEM denoting the item of the set
		 * and LENGTH its length when converted to byte array.
		 * @param <T> type of the elements of the set.
		 * @param set set to be converted.
		 * @param dst BAOS the result will be appended to.
		 * @return size of the buffer.
		 * @throws IOException if I/O error(s) occur(s).
		 */
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

		/**
		 * Puts a byte array inside a ByteBuffer doubling its size every time this operation is not successful.
		 * @param dst buffer the array is to be put in.
		 * @param buffersize buffer's initial size.
		 * @param src byte array to be put inside the buffer.
		 * @return new buffer's size.
		 */
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

		/** Handles syntax errors. */
		private static ResponseCode syntaxErrorHandler(ByteArrayOutputStream baos)
		{
			try
			{
				baos.write(ResponseCode.BAD_REQUEST.getDescription().getBytes(StandardCharsets.US_ASCII));
				baos.write("Syntax error.".getBytes(StandardCharsets.US_ASCII));
			}
			catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
			return ResponseCode.BAD_REQUEST;
		}

		/** Handles username's errors. */
		private static ResponseCode invalidUsernameHandler(ByteArrayOutputStream baos, final String username)
		{
			try
			{
				baos.write(ResponseCode.NOT_FOUND.getDescription().getBytes(StandardCharsets.US_ASCII));
				baos.write(String.format("User %s is not logged in with this client", username).getBytes(StandardCharsets.US_ASCII));
			}
			catch (IOException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }
			return ResponseCode.NOT_FOUND;
		}
	}

	/** Class used to wrap together the whole logic for a task to send a message to a client. */
	private static class MessageDispatcher implements Runnable
	{
		/** Pointer to the set of clients to be registered for next select's iteration. */
		private Set<SetElement> toBeRegistered = null;
		/** Pointer to selector. It is used to wake it up after handling the request. */
		private Selector selector = null;
		/** Pointer to the key denoting this client. */
		private SelectionKey key = null;
		/** Pointer to user storage. */
		private UserStorage users = null;
		/** Pointer to the map storing the couples (client, username they have logged in with). */
		private Map<SocketChannel, String> loggedInClients = null;

		public MessageDispatcher(final Set<SetElement> toBeRegistered, final Selector selector,
				final SelectionKey key, final UserStorage users, Map<SocketChannel, String> loggedInClients)
		{
			this.toBeRegistered = Objects.requireNonNull(toBeRegistered, "Set" + NULL_ERROR);
			this.selector = Objects.requireNonNull(selector, "Selector" + NULL_ERROR);
			this.key = Objects.requireNonNull(key, "Key" + NULL_ERROR);
			this.loggedInClients = Objects.requireNonNull(loggedInClients, "Logged in clients" + NULL_ERROR);
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
			configFilename = "./configs/server.properties";
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
		multicastInfoBytes = configuration.getMulticastInfo().getBytes(StandardCharsets.US_ASCII);

		// setting up rmi:
		UserStorage users = null;
		try { users = UserMap.fromJSON(new File(configuration.userStorageFilename), new File(configuration.followingStorageFilename), new File(configuration.transactionsFilename)); }
		catch (FileNotFoundException | IllegalArchiveException e) { users = new UserMap(); }
		catch (IOException e)
		{
			System.err.println("Fatal error occurred while parsing user storage: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
		PostStorage posts = null;
		try { posts = PostMap.fromJSON(new File(configuration.postStorageFilename), new File(configuration.postsInteractionsStorageFilename)); }
		catch (IOException | IllegalArchiveException | InvalidGeneratorException e)
		{
			posts = new PostMap();
		}
		RMICallbackService callbackService = null;
		try  { callbackService = new RMICallbackService(); }
		catch (RemoteException e)
		{
			System.err.println("Fatal error occurred while setting up RMI callbacks: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
		Thread rmi = new Thread(new RMITask(configuration, (UserMap) users, callbackService));
		rmi.start();
		Thread backup = new Thread(new BackupTask(configuration, users, posts));
		backup.start();
		Thread rewards = null;
		try { rewards = new Thread(new RewardsTask(configuration, users, posts)); }
		catch (IOException e)
		{
			System.err.println("Fatal error occurred while setting up multicast: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
		rewards.start();
		final Thread rewardsHandler = rewards;
		BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
		final Thread logging = new Thread(new LoggingTask(logQueue, configuration));
		logging.start();

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
			System.err.println("Fatal I/O error occurred while setting up selector: now aborting...");
			e.printStackTrace();
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
				rewardsHandler.interrupt();
				logging.interrupt();
				selectorHandler.wakeup();
				Iterator<SelectionKey> keys = selectorHandler.keys().iterator();
				while (keys.hasNext())
				{
					SelectableChannel c = keys.next().channel();
					if (c instanceof ServerSocketChannel)
					{
						try { c.close(); }
						catch (IOException e)
						{
							System.err.println("I/O error occurred during shutdown:");
							e.printStackTrace();
						}
					}
				}
				try { selectorHandler.close(); }
				catch (IOException e)
				{
					System.err.println("I/O error occurred during shutdown:");
					e.printStackTrace();
				}
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
				try { rewardsHandler.join(500); }
				catch (InterruptedException e) { }
				try { logging.join(500); }
				catch (InterruptedException e) { }
			}
		});

		/** Maps a channel to the username it has logged in with. */
		Map<SocketChannel, String> loggedInClients = new ConcurrentHashMap<>();
		// select loop:
		while (true)
		{
			int r = 0;
			try { r = selector.select(); }
			catch (IOException e)
			{
				System.err.println("Fatal I/O error occurred during select: now aborting...");
				e.printStackTrace();
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
						threadPool.execute(new Thread(new RequestHandler(toBeRegistered, selector, k, users, posts, loggedInClients, callbackService, logQueue)));
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