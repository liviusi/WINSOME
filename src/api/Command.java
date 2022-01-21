package api;

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

import cryptography.Passwords;
import server.storage.PasswordNotValidException;
import server.storage.UserRMIStorage;
import server.storage.UsernameAlreadyExistsException;
import server.storage.UsernameNotValidException;
import user.InvalidTagException;
import user.TagListTooLongException;

/**
 * @brief Utility class used to send properly parsed Command-Line commands from the client to the server.
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

	/**
	 * @brief Signs up a user to WINSOME.
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
		if (verbose) System.out.println(username + " has now signed up.");
	}

	/**
	 * @brief Logs in a user to WINSOME.
	 * @param username cannot be null.
	 * @param password cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if response is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int login(String username, String password, SocketChannel server, Set<String> dest, boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(password, "Password" + NULL_ERROR);
		Objects.requireNonNull(server, "Server channel" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;
		String saltDecoded = null;
		String hashedPassword = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> responsePullFollowers;

		buffer.flip(); buffer.clear();
		bytes = String.format("{ \"%s\": \"%s\",\n \"%s\": \"%s\"}", COMMAND, CommandCode.LOGINSETUP.description, USERNAME, username)
				.getBytes(StandardCharsets.US_ASCII);
		// Login setup:<username>
		Communication.send(server, buffer, bytes);
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
		bytes = String.format("{ \"%s\": \"%s\"\n, \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.LOGINATTEMPT.description, USERNAME,
				username, HASHEDPASSWORD, hashedPassword).getBytes(StandardCharsets.US_ASCII);
		// Login:<username>:<hash(password, salt)>
		Communication.send(server, buffer, bytes);
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
		buffer.flip(); buffer.clear();
		bytes = String.format("{ \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.PULLFOLLOWERS.description, USERNAME, username)
				.getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
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
			return 0;
		}
		else
		{
			if (verbose) System.out.printf("< Code: %s", r.code.getDescription());
			return 1;
		}
	}

	/**
	 * @brief Logs out a user of WINSOME.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
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
		bytes = String.format("{ \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.LOGOUT.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
		// Logout:<username>
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
	 * @brief Lists out all the users on WINSOME sharing at least a common interest with the caller.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @param dest cannot be null, it will contain the usernames of the users sharing at least a common interest with username and their interests.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int listUsers(String username, SocketChannel server, boolean verbose, Set<String> dest)
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
		bytes = String.format("{ \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.LISTUSERS.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
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
	 * @brief Lists out all the users on WINSOME the caller is currently following.
	 * @param username cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @param dest cannot be null, it will contain the usernames of the users username is currently following and their interests.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
	 * @throws NullPointerException if any parameters are null.
	 */
	public static int listFollowing(String username, SocketChannel server, boolean verbose, Set<String> dest)
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
		bytes = String.format("{ \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.LISTFOLLOWING.description, USERNAME, username).getBytes(StandardCharsets.US_ASCII);
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
	 * @brief Starts following a user on WINSOME.
	 * @param follower cannot be null.
	 * @param followed cannot be null.
	 * @param server cannot be null.
	 * @param verbose toggled on if any output is to be printed out.
	 * @return 0 on success, 1 on failure, -1 if an error occurs.
	 * @throws IOException if I/O error(s) occur (refer to Communication receiveMessage and send) or an invalid response is received.
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
		bytes = String.format("{ \"%s\": \"%s\"\n, \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.FOLLOWUSER.description, FOLLOWER,
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
		bytes = String.format("{ \"%s\": \"%s\"\n, \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.UNFOLLOWUSER.description, FOLLOWER,
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

	public static int blog(final String author, final SocketChannel server, Set<String> dest, final boolean verbose)
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
		bytes = String.format("{ \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.VIEWBLOG.description, USERNAME, author).getBytes(StandardCharsets.US_ASCII);
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

	public static int post(final String author, final String title, final String contents, final SocketChannel server, final boolean verbose, StringBuilder dest)
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
		bytes = String.format("{ \"%s\": \"%s\"\n, \"%s\": \"%s\",\n \"%s\": \"%s\",\n \"%s\": \"%s\" }", COMMAND, CommandCode.CREATEPOST.description,
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
	public static int comment(final String author, final int postID, final String contents, final SocketChannel server, final boolean verbose)
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
		bytes = (CommandCode.COMMENT.description + Constants.DELIMITER + author + Constants.DELIMITER + postID + Constants.DELIMITER + contents)
			.getBytes(StandardCharsets.US_ASCII);
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

	public static int rate(final String voter, final int postID, final int vote, final SocketChannel server, final boolean verbose)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(voter, "Author" + NULL_ERROR);
		Objects.requireNonNull(server, "Server" + NULL_ERROR);

		ByteBuffer buffer = ByteBuffer.allocate(BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;
		StringBuilder sb = null;

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.RATE.description + Constants.DELIMITER + voter + Constants.DELIMITER + postID + Constants.DELIMITER + vote)
			.getBytes(StandardCharsets.US_ASCII);
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
	*/

	/**
	 * @brief Prints on System.out if flag is toggled on.
	 * @param toPrint response to be printed out
	 * @param flag to be toggled on if response is to be printed out.
	 */
	private static void printIf(Response<String> toPrint, boolean flag)
	{
		if (flag)
		{
			System.out.printf("< Code: %s", toPrint.code.getDescription());
			System.out.println("< "+ toPrint.body);
		}
	}
}
