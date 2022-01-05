package server;

import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Set;

import server.rmi.UserStorage;
import server.user.InvalidLoginException;
import server.user.InvalidLogoutException;
import server.user.User;
import server.user.WrongCredentialsException;

public class API
{
	private static final String NULL_ERROR = " cannot be null.";

	private API() { }

	public static void handleLogin(UserStorage users, SocketChannel clientID, String username, String hashPassword)
	throws InvalidLoginException, WrongCredentialsException, NullPointerException
	{
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_ERROR);
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(hashPassword, "Hashed password" + NULL_ERROR);
		User u = users.getUser(username);
		u.login(clientID, hashPassword);
	}

	public static String handleLoginSetup(UserStorage users, String username)
	throws NullPointerException
	{
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		User u = users.getUser(username);
		if (u != null) return u.saltDecoded;
		return null;
	}

	public static void handleLogout(UserStorage users, SocketChannel clientID, String username)
	throws InvalidLogoutException, NullPointerException
	{
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_ERROR);
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		User u = users.getUser(username);
		u.logout(clientID);
	}

	public static Set<String> handleListUser(UserStorage users, String username)
	throws NullPointerException
	{
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		return users.getAllUsersWithSameInterestsAs(username);
	}

	public static boolean handleFollowUser(UserStorage users, String followed, String follower)
	throws NullPointerException, IllegalArgumentException
	{
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		Objects.requireNonNull(followed, "Followed user's username" + NULL_ERROR);
		Objects.requireNonNull(follower, "Follower user's username" + NULL_ERROR);
		if (followed.equals(follower)) throw new IllegalArgumentException("A user cannot follow themselves.");
		return users.addFollower(followed, follower);
	}
}
