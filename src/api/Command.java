package api;

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
import server.rmi.PasswordNotValidException;
import server.rmi.UserStorage;
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
		UserStorage service = (UserStorage) r.lookup(serviceName);
		byte[] salt = Passwords.generateSalt();
		String hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.UTF_8), salt);
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

		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGINSETUP.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.UTF_8);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		StringBuilder sb = new StringBuilder();
		if (Communication.receive(server, buffer, sb) == -1) return -1;
		String saltDecoded = sb.toString();
		System.out.println("salt: " + saltDecoded);
		if (saltDecoded.endsWith(Constants.USER_NOT_REGISTERED) || saltDecoded.endsWith(Constants.CLIENT_ALREADY_LOGGED_IN))
		{
			if (verbose) System.out.println(saltDecoded);
			return 0;
		}
		String hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.UTF_8), Passwords.decodeSalt(saltDecoded));
		System.out.println("hashedPassword: " + hashedPassword);
		buffer.flip(); buffer.clear();
		bytes = (CommandCode.LOGINATTEMPT.getDescription() + Constants.DELIMITER + username + Constants.DELIMITER + hashedPassword).getBytes(StandardCharsets.UTF_8);
		Communication.send(server, buffer, bytes);
		buffer.flip(); buffer.clear();
		sb = new StringBuilder();
		if (Communication.receive(server, buffer, sb) == -1) return -1;
		String response = sb.toString();
		if (verbose)
			System.out.println(response);
		if (response.endsWith(Constants.LOGIN_SUCCESS)) return 1;
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
		bytes = (CommandCode.LOGOUT.getDescription() + Constants.DELIMITER + username).getBytes(StandardCharsets.UTF_8);
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
}
