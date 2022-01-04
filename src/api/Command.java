package api;

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

	public static void register(String username, String password, Set<String> tags, int portNo, String serviceName, boolean verbose)
	throws RemoteException, NotBoundException, NullPointerException, UsernameNotValidException, UsernameAlreadyExistsException,
			PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		if (username == null || password == null || tags == null || serviceName == null)
			throw new NullPointerException("Parameter(s) cannot be null.");
		Registry r = LocateRegistry.getRegistry(portNo);
		UserRMIStorage service = (UserRMIStorage) r.lookup(serviceName);
		byte[] salt = Passwords.generateSalt();
		String hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.US_ASCII), salt);
		service.register(username, hashedPassword, tags, salt);
		if (verbose) System.out.println(username + " has now signed up.");
	}

	public static int login(String username, String password, SocketChannel server, boolean verbose)
	throws IOException
	{
		if (username == null || password == null || server == null)
			throw new NullPointerException("Parameter(s) cannot be null.");

		ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFERSIZE);
		byte[] bytes = null;
		Response r = null;

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGINSETUP.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		StringBuilder sb = new StringBuilder();
		if (Communication.receive(server, buffer, sb) == -1) return -1;
		r = Response.ParseAnswer(sb.toString());
		if (r.code.getValue() != ResponseCode.OK.getValue())
		{
			printIf(System.out, r, verbose);
			return 0;
		}
		String saltDecoded = r.body;
		System.out.println("salt: " + saltDecoded);
		String hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.US_ASCII), Passwords.decodeSalt(saltDecoded));
		System.out.println("hashedPassword: " + hashedPassword);
		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGINATTEMPT.getDescription() + Constants.DELIMITER + username + Constants.DELIMITER + hashedPassword).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receive(server, buffer, sb) == -1) return -1;
		r = Response.ParseAnswer(sb.toString());
		printIf(System.out, r, verbose);
		if (r.code.getValue() == ResponseCode.OK.getValue()) return 1;
		else return 0;
	}

	public static int logout(String username, SocketChannel server, boolean verbose)
	throws IOException
	{
		Objects.requireNonNull(username, "Username cannot be null.");
		Objects.requireNonNull(server, "Server cannot be null.");

		ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFERSIZE);
		byte[] bytes = null;

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGOUT.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		StringBuilder sb = new StringBuilder();
		if (Communication.receive(server, buffer, sb) == -1) return -1;
		String response = sb.toString();
		if (verbose)
			System.out.println(response);
		if (response.endsWith(Constants.LOGOUT_SUCCESS)) return 1;
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

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LISTUSERS.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.US_ASCII);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		return 0;
	}

	private static class Response
	{
		public final ResponseCode code;
		public final String body;

		private Response(ResponseCode code, String body)
		{
			this.code = code;
			this.body = body;
		}

		private static Response ParseAnswer(String str)
		{
			System.out.println(str);
			int code = -1;
			try
			{
				code = Integer.parseInt(str.split(" ", 2)[0]);
			}
			catch (NumberFormatException e)
			{
				return null;
			}
			System.out.println("code = " + code);
			int index = str.indexOf("\r\n") + 1;
			if (index == -1) return null;
			String body = str.substring(index + 1);
			return new Response(ResponseCode.fromCode(code), body);
		}
	}

	private static void printIf(PrintStream stream, Response toPrint, boolean flag)
	{
		if (flag)
		{
			stream.println(toPrint.code.getDescription());
			stream.println(toPrint.body);
		}
	}
}
