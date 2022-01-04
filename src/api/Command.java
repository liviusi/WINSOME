package api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import server.rmi.PasswordNotValidException;
import server.rmi.UserRMIStorage;
import server.rmi.UsernameAlreadyExistsException;
import server.rmi.UsernameNotValidException;
import server.user.InvalidTagException;
import server.user.TagListTooLongException;

public class Command
{

	private static final String RESPONSE_FAILURE = "Server response could not be parsed properly.";

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

	public static int login(String username, String password, SocketChannel server, boolean verbose)
	throws IOException
	{
		Objects.requireNonNull(username, "Username cannot be null.");
		Objects.requireNonNull(password, "Password cannot be null.");
		Objects.requireNonNull(server, "Server channel cannot be null.");

		ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGINSETUP.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		StringBuilder sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (r.code != ResponseCode.OK)
		{
			printIf(System.out, r, verbose);
			return 0;
		}
		String saltDecoded = r.body;
		String hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.US_ASCII), Passwords.decodeSalt(saltDecoded));
		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGINATTEMPT.getDescription() + Constants.DELIMITER + username + Constants.DELIMITER + hashedPassword).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		printIf(System.out, r, verbose);
		if (r.code == ResponseCode.OK) return 1;
		else return 0;
	}

	public static int logout(String username, SocketChannel server, boolean verbose)
	throws IOException
	{
		Objects.requireNonNull(username, "Username cannot be null.");
		Objects.requireNonNull(server, "Server cannot be null.");

		ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFERSIZE);
		byte[] bytes = null;
		Response<String> r = null;

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGOUT.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		StringBuilder sb = new StringBuilder();
		if (Communication.receiveMessage(server, buffer, sb) == -1) return -1;
		r = Response.parseAnswer(sb.toString());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		printIf(System.out, r, verbose);
		if (r.code == ResponseCode.OK) return 1;
		else return 0;
	}

	public static int listUsers(String username, SocketChannel server, boolean verbose, Set<String> dest)
	throws IOException
	{
		Objects.requireNonNull(username, "Username cannot be null.");
		Objects.requireNonNull(server, "Server cannot be null.");
		Objects.requireNonNull(dest, "Set cannot be null.");

		ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFERSIZE);
		byte[] bytes = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Response<Set<String>> r = null;

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LISTUSERS.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		if (Communication.receiveBytes(server, buffer, baos) == -1) return -1;
		r = Response.parseAnswer(baos.toByteArray());
		if (r == null) throw new IOException(RESPONSE_FAILURE);
		if (verbose)
		{
			System.out.printf("Code: %s", r.code.getDescription());
		}
		if (r.code != ResponseCode.OK) return 0;
		for (String s: r.body)
			dest.add(s);
		return 1;
	}

	private static void printIf(PrintStream stream, Response<String> toPrint, boolean flag)
	{
		if (flag)
		{
			stream.printf("Code: %s", toPrint.code.getDescription());
			stream.println(toPrint.body);
		}
	}
}
