package server;

import java.nio.channels.SocketChannel;

import server.rmi.UserMap;
import server.user.InvalidLoginException;
import server.user.InvalidLogoutException;
import server.user.User;
import server.user.WrongCredentialsException;

public class API
{
	private API() { }

	public static void handleLogin(UserMap users, SocketChannel clientID, String username, String hashPassword)
	throws InvalidLoginException, WrongCredentialsException
	{
		User u = users.getUser(username);
		u.login(clientID, hashPassword);
	}

	public static String handleLoginSetup(UserMap users, String username)
	{
		User u = users.getUser(username);
		if (u != null) return u.saltDecoded;
		return null;
	}

	public static void handleLogout(UserMap users, SocketChannel clientID, String username)
	throws InvalidLogoutException
	{
		User u = users.getUser(username);
		u.logout(clientID);
	}
}
