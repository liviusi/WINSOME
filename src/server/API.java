package server;

import java.nio.channels.SocketChannel;

import server.rmi.UserSet;
import server.user.InvalidLoginException;
import server.user.User;
import server.user.WrongCredentialsException;

public class API
{
	private API() { }

	public static void handleLogin(UserSet users, SocketChannel clientID, String username, String hashPassword)
	throws InvalidLoginException, WrongCredentialsException
	{
		User u = users.getUser(username);
		u.login(clientID, hashPassword);
	}
}
