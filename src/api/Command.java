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
		if (verbose && service.register(username, hashedPassword, tags, salt))
			System.out.println(username + " has now signed up.");
	}

	public static boolean login(String username, String password, SocketChannel server, boolean verbose)
	throws IOException
	{
		if (username == null || password == null || server == null)
			throw new NullPointerException("Parameter(s) cannot be null.");
		ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFERSIZE);
		buffer.put((CommandCode.LOGINSETUP + username).getBytes(StandardCharsets.UTF_8));
		buffer.flip();
		while (buffer.hasRemaining())
			server.write(buffer);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		buffer.clear();
		server.read(buffer);
		buffer.flip();
		while (buffer.hasRemaining())
			baos.write(buffer.get()); // reading salt
		byte[] salt = baos.toByteArray();
		String hashedPassword = Passwords.hashPassword(password.getBytes(StandardCharsets.UTF_8), salt);
		buffer.clear();
		buffer.put((CommandCode.LOGINATTEMPT + username + ":" + hashedPassword).getBytes(StandardCharsets.UTF_8));
		buffer.flip();
		while (buffer.hasRemaining())
			server.write(buffer);
		buffer.clear();
		server.read(buffer);
		StringBuilder response = new StringBuilder();
		buffer.flip();
		while (buffer.hasRemaining()) response.append((char) buffer.get());
		String r = response.toString();
		if (verbose)
			System.out.println(r);
		return r.endsWith(Constants.LOGIN_SUCCESS_POSTFIX);
	}
}
