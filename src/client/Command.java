package client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Objects;
import java.util.Set;

import api.CommandCode;
import api.Communication;
import api.ResponseCode;
import api.rmi.InvalidTagException;
import api.rmi.PasswordNotValidException;
import api.rmi.TagListTooLongException;
import api.rmi.UserRMIStorage;
import api.rmi.UsernameAlreadyExistsException;
import api.rmi.UsernameNotValidException;
import cryptography.Passwords;

/**
 * Utility class used to send properly parsed Command-Line commands from the client to the server.
 * The following notation will be used throughout the whole file:
 * - user(x) will be used to denote the user (on WINSOME) with username x, it will also be used as a shorthand for the couple (username, interests(username));
 * - POST(x) will be used to denote the state x is after a certain method's successful execution;
 * - PREV(x) will be used to denote the state x is before a certain method's execution.
 * @author Giacomo Trapani
 */

public class Command
{
	private static final int BUFFERSIZE = 2048;
	/** Used as an error message whenever response's parsing fails. */
	private static final String RESPONSE_FAILURE = "Server response could not be parsed properly.";
	/** Used as an error message whenever an input parameter is null. */
	private static final String NULL_ERROR = " cannot be null.";
	private static final String COMMAND = "command";
	private static final String USERNAME = "username";
	private static final String HASHEDPASSWORD = "hashedpassword";
	private static final String FOLLOWER = "follower";
	private static final String FOLLOWED = "followed";
	private static final String AUTHOR = "author";
	private static final String TITLE = "title";
	private static final String CONTENTS = "contents";
	private static final String POSTID = "postid";

	/**
	 * Signs up a user to WINSOME.
	 * @param username cannot be null.
	 * @param password cannot be null.
	 * @param tags cannot be null, may be empty.
	 * @param portNo port the registry is located on.
	 * @param serviceName cannot be null, it is the register function's name in the registry.
	 * @param verbose toggled on if any output is to be printed out.
	 * @throws RemoteException if a communication error occurs.
	 * @throws NotBoundException if serviceName is not currently bound.
	 * @throws NullPointerException if any parameters are null.
	 * @throws UsernameNotValidException if username does not contain any alphanumeric character.
	 * @throws UsernameAlreadyExistsException if username is already taken.
	 * @throws PasswordNotValidException if password is the empty string.
	 * @throws InvalidTagException if tag does not contain any alphanumeric character.
	 * @throws TagListTooLongException if more than 5 tags are specified.
	 */
	public static void register(String username, String password, Set<String> tags, int portNo, String serviceName, boolean verbose)
	throws RemoteException, NotBoundException, NullPointerException, UsernameNotValidException, UsernameAlreadyExistsException,
			PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		Registry r = LocateRegistry.getRegistry(portNo);
		UserRMIStorage service = (UserRMIStorage) r.lookup(Objects.requireNonNull(serviceName, "Service to search for cannot be null."));
		byte[] salt = Passwords.generateSalt();
		String hashedPassword = Passwords.hashPassword(Objects.requireNonNull(password, "Password cannot be null.").getBytes(StandardCharsets.US_ASCII), salt);
		service.register(Objects.requireNonNull(username, "Username cannot be null."), hashedPassword, Objects.requireNonNull(tags, "Tags cannot be null."), salt);
		if (verbose) System.out.println(Colors.ANSI_GREEN + username + " has now signed up." + Colors.ANSI_RESET);
	}

	/**
	 * Logs in a user to WINSOME.
	 * <br> - dest: POST(dest) = PREV(dest) U { followedBy(user(username)) } with { followedBy(x) } denoting the set of all the users x is currently followed by.
	 * <br> - JSONMulticastInfo: CONCAT(POST(JSONMulticastInfo), info) with info denoting the String describing the multicast coordinates written following JSON syntax.
	 * @param username cannot be null.
	 * @param password cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param JSONMulticastInfo cannot be null.
	 * @param verbose toggled on if response is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur(s) (refer to Communication receiveMessage, receiveBytes and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int login(String username, String password, SocketChannel server, Set<String> dest, StringBuilder JSONMulticastInfo, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(password, "Password" + NULL_ERROR);
		Objects.requireNonNull(server, "Server channel" + NULL_ERROR);
		Objects.requireNonNull(dest, "Set" + NULL_ERROR);
		Objects.requireNonNull(JSONMulticastInfo, "StringBuilder" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;
		String saltDecoded = null;
		String hashedPassword = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> responsePullFollowers;

		buffer.flip(); buffer.clear();
		// { "command": "Login setup", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\"}", COMMAND, CommandCode.LOGINSETUP.description, USERNAME, username)
				.getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes); // asking for salt
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1; // retrieving salt
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code != ResponseCode.OK)
		{
			printIf(r, verbose);
			return 1;
		}
		saltDecoded = r.body;
		hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.US_ASCII), Passwords.decodeSalt(saltDecoded));
		buffer.flip(); buffer.clear();
		// { "command": "Login", "username": "<username>", "hashedpassword": "<hashed password base64 encoded>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.LOGINATTEMPT.description, USERNAME,
				username, HASHEDPASSWORD, hashedPassword).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes); // asking for client to login
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code != ResponseCode.OK) // client could not login
		{
			printIf(r, verbose);
			return 1;
		}
		buffer.flip(); buffer.clear();
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.PULLFOLLOWERS.description, USERNAME, username)
				.getBytes(StandardCharsets.US_ASCII);
		// { "command": "Pull followers", "username": "<username>" }
		Communication.send(server, buffer, bytes); // asking for initial followers' list
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1; // retrieving followers
		responsePullFollowers = Response.parseAnswer(baos.toByteArray());
		if (responsePullFollowers == null)
		{
			r = Response.parseAnswer(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(baos.toByteArray())).toString());
			if (r == null) throw new IOException(RESPONSE_FAILURE);
			else
			{
				printIf(r, verbose);
				return 1;
			}
		}
		if (r.code == ResponseCode.OK)
		{
			for (String s: responsePullFollowers.body) dest.add(s);
			buffer.flip(); buffer.clear();
			// { "command": "Retrieve multicast" }
			bytes = String.format("{ \"%s\": \"%s\" }", COMMAND, CommandCode.RETRIEVEMULTICAST.description).getBytes(StandardCharsets.US_ASCII);
			Communication.send(server, buffer, bytes); // asking for multicast coordinates to be sent
			buffer.flip(); buffer.clear();
			sb = new StringBuilder();
			if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
			r = Response.parseAnswer(sb.toString());
			if (r == null) throw new IOException(RESPONSE_FAILURE);
			if (r.code != ResponseCode.OK)
			{
				printIf(r, verbose);
				return 1;
			}
			JSONMulticastInfo.append(r.body);
			return 0;
		}
		else
		{
			if (verbose) System.out.printf("< %sCode%s: %s%s%s", Colors.ANSI_YELLOW, Colors.ANSI_RESET, Colors.ANSI_YELLOW, r.code.getDescription(), Colors.ANSI_RESET);
			return 1;
		}
	}

	/**
	 * Logs out a user of WINSOME.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur(s) (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int logout(String username, SocketChannel server, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Logout", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.LOGOUT.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Lists out all the users on WINSOME sharing at least a common interest with the caller.
	 * <br> dest: POST(dest) = PREV(dest) U { commonInterestsWith(user(username)) } with { commonInterestsWith(x) } denoting the set of each and every user
	 * sharing an interest (a.k.a. a tag) with x.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur(s) (refer to Communication receiveBytes and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int listUsers(String username, SocketChannel server, Set<String> dest, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "Set" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> r = null;

		buffer.flip(); buffer.clear();
		// { "command": "List users", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.LISTUSERS.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1;
		r = Response.parseAnswer(baos.toByteArray());
		if (r == null)
		{
			Response<String> retry = Response.parseAnswer(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(baos.toByteArray())).toString());
			if (retry == null) throw new IOException(RESPONSE_FAILURE);
			else
			{
				printIf(retry, verbose);
				return 1;
			}
		}
		for (String s: r.body) dest.add(s);
		return 0;
	}

	/**
	 * Lists out all the users on WINSOME the caller is currently following.
	 * <br> dest: POST(dest) = PREV(dest) U { following(user(username)) } with { following(x) } denoting the set of the users x is currently following.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @param dest cannot be null.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveBytes and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int listFollowing(String username, SocketChannel server, Set<String> dest, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "Set" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> r = null;

		buffer.flip(); buffer.clear();
		// { "command": "List following", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.LISTFOLLOWING.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1;
		r = Response.parseAnswer(baos.toByteArray());
		if (r == null)
		{
			Response<String> retry = Response.parseAnswer(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(baos.toByteArray())).toString());
			if (retry == null) throw new IOException(RESPONSE_FAILURE);
			else
			{
				printIf(retry, verbose);
				return 1;
			}
		}
		for (String s: r.body) dest.add(s);
		return 0;
	}


	/**
	 * Starts following a user on WINSOME.
	 * @param follower cannot be null.
	 * @param followed cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur(s) (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int followUser(String follower, String followed, SocketChannel server, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(follower, "Username" + NULL_ERROR);
		Objects.requireNonNull(followed, "User to be followed's username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Follow", "follower": "<follower>", "followed": "<followed>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.FOLLOWUSER.description, FOLLOWER,
				follower, FOLLOWED, followed).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Stops following a user on WINSOME.
	 * @param follower cannot be null.
	 * @param followed cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur(s) (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int unfollowUser(final String follower, final String followed, final SocketChannel server, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(follower, "Username" + NULL_ERROR);
		Objects.requireNonNull(followed, "User to be unfollowed's username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Unfollow", "follower": "<follower>", "followed": "<followed>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.UNFOLLOWUSER.description, FOLLOWER,
				follower, FOLLOWED, followed).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Retrieves all posts by given author on WINSOME.
	 * <br> dest: POST(dest) = PREV(dest) U { postsBy(user(username)) } with { postsBy(x) } denoting the set of each and every post written by x.
	 * @param author cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveBytes and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int viewBlog(final String author, final SocketChannel server, Set<String> dest, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(author, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "Set" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> r = null;

		buffer.flip(); buffer.clear();
		// { "command": "Blog", "username": "<author>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.VIEWBLOG.description, USERNAME, author).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1;
		r = Response.parseAnswer(baos.toByteArray());
		if (r == null)
		{
			Response<String> retry = Response.parseAnswer(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(baos.toByteArray())).toString());
			if (retry == null) throw new IOException(RESPONSE_FAILURE);
			else
			{
				printIf(retry, verbose);
				return 1;
			}
		}
		for (String s: r.body) dest.add(s);
		return 0;
	}

	/**
	 * Uploads post with given parameters on WINSOME.
	 * <br> dest: POST(dest) = CONCAT(PREV(dest), ID(p)) with ID(x) denoting the ID of the post x and p denoting the post newly created on WINSOME.
	 * @param author cannot be null.
	 * @param title cannot be null.
	 * @param contents cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int createPost(final String author, final String title, final String contents, final SocketChannel server, StringBuilder dest, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(author, "Author" + NULL_ERROR);
		Objects.requireNonNull(title, "Title" + NULL_ERROR);
		Objects.requireNonNull(contents, "Contents" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "Destination" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Post", "author": "<author>", "title": "<title>", "contents": "<contents>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.CREATEPOST.description,
				AUTHOR, author, TITLE, title, CONTENTS, contents).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK)
		{
			dest.append(r.body);
			return 0;
		}
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Retrieves the feed of the given user. Let x be a user, a feed is defined as the union of the set of each and every post the author of which
	 * is an user x is following and (the set of each and every post) has been rewon by an user x is following.
	 * <br> dest: POST(dest) = PREV(dest) U feed(user(username)) with feed defined as before.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveBytes and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int showFeed(final String username, final SocketChannel server, Set<String> dest, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "Set" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> r = null;

		buffer.flip(); buffer.clear();
		// { "command": "Show feed", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.SHOWFEED.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1;
		r = Response.parseAnswer(baos.toByteArray());
		if (r == null)
		{
			Response<String> retry = Response.parseAnswer(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(baos.toByteArray())).toString());
			if (retry == null) throw new IOException(RESPONSE_FAILURE);
			else
			{
				printIf(retry, verbose);
				return 1;
			}
		}
		for (String s: r.body) dest.add(s);
		return 0;
	}

	/**
	 * Shows a post given its unique identifier.
	 * <br> dest: POST(dest) = CONCAT(PREV(dest), postToJson(postID)) with postToJson(x) the post with identifier x written following JSON syntax.
	 * @param username cannot be null.
	 * @param postID identifier of the post.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int showPost(String username, int postID, SocketChannel server, StringBuilder dest, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(dest, "StringBuilder" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Show post", "username", "<username>", "postid": "<postID>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%d\" }", COMMAND, CommandCode.SHOWPOST.description, USERNAME,
				username, POSTID, postID).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK)
		{
			dest.append(r.body);
			return 0;
		}
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Deletes a post given its identifier.
	 * @param username cannot be null.
	 * @param postID identifier of the post.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int deletePost(String username, int postID, SocketChannel server, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Delete post", "username": "<username>", "postid": "<postID>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%d\" }", COMMAND, CommandCode.DELETEPOST.description, USERNAME,
				username, POSTID, postID).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Rewins a post given its identifier. CAVEAT: a rewin does not generate a new post, it is but a symbolic link between a user
	 * and a post by another user.
	 * @param username cannot be null.
	 * @param postID identifier of the post.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int rewinPost(String username, int postID, SocketChannel server, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Rewin", "username": "<username>", "postid": "<postID>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%d\" }", COMMAND, CommandCode.REWIN.description, USERNAME,
				username, POSTID, postID).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Rates a post given its identifier (it may either correspond to upvoting or downvoting).
	 * @param voter cannot be null.
	 * @param postID identifier of the post.
	 * @param vote vote to be cast.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int ratePost(final String voter, final int postID, final int vote, final SocketChannel server, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(voter, "Author" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Rate", "username": "<voter>", "postid": "<postID>", "vote": "<vote>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%d\", \"%s\": \"%d\" }", COMMAND, CommandCode.RATE.description,
				USERNAME, voter, POSTID, postID, "vote", vote).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Adds a comment to a post given its identifier.
	 * @param author cannot be null.
	 * @param postID identifier of the post.
	 * @param contents cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int addComment(final String author, final int postID, final String contents, final SocketChannel server, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(author, "Author" + NULL_ERROR);
		Objects.requireNonNull(contents, "Contents" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		// { "command": "Comment", "author": "<author>", "postid": "<postID>", "contents": "<contents>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\", \"%s\": \"%d\", \"%s\": \"%s\" }", COMMAND, CommandCode.COMMENT.description,
				USERNAME, author, POSTID, postID, CONTENTS, contents).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK) return 0;
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Gets the whole transaction history of a given user.
	 * <br> dest: POST(dest) = PREV(post) U { transactionsBy(user(username)) } with transactionsBy(x) denoting the set of each and every transaction
	 * x is involved with.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int getWallet(final String username, final SocketChannel server, final Set<String> dest, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "Set" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> r = null;

		buffer.flip(); buffer.clear();
		// { "command": "Wallet", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.WALLET.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1;
		r = Response.parseAnswer(baos.toByteArray());
		if (r == null)
		{
			Response<String> retry = Response.parseAnswer(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(baos.toByteArray())).toString());
			if (retry == null) throw new IOException(RESPONSE_FAILURE);
			else
			{
				printIf(retry, verbose);
				return 1;
			}
		}
		for (String s: r.body) dest.add(s);
		return 0;
	}

	/**
	 * Shows given users' WINCOINS converted to BTC.
	 * <br> dest: POST(dest) = CONCAT(PREV(dest), walletToBTC(user(username))) with walletToBTC denoting the function computing the conversion
	 * from WINSOME to BTC.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param dest cannot be null
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int getWalletInBitcoin(final String username, final SocketChannel server, final StringBuilder dest, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);
		Objects.requireNonNull(dest, "StringBuilder" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = new StringBuilder();

		buffer.flip(); buffer.clear();
		// { "command": "Wallet BTC", "username": "<username>" }
		bytes = String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", COMMAND, CommandCode.WALLETBTC.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code == ResponseCode.OK)
		{
			dest.append(r.body);
			return 0;
		}
		else
		{
			printIf(r, verbose);
			return 1;
		}
	}

	/**
	 * Prints on System.out if flag is toggled on.
	 * @param toPrint response to be printed out
	 * @param flag to be toggled on if response is to be printed out.
	 */
	private static void printIf(Response<String> toPrint, boolean flag)
	{
		if (flag)
		{
			System.out.printf("< %sCode%s: %s%s%s", Colors.ANSI_YELLOW, Colors.ANSI_RESET, Colors.ANSI_YELLOW, toPrint.code.getDescription(), Colors.ANSI_RESET);
			System.out.println("< " + Colors.ANSI_YELLOW + toPrint.body + Colors.ANSI_RESET);
		}
	}
}
