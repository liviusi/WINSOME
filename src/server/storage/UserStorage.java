package server.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Set;

import user.InvalidLoginException;
import user.InvalidLogoutException;
import user.WrongCredentialsException;

/**
 * @brief Interface to be implemented by an actual storage class.
 * @author Giacomo Trapani
 */
public interface UserStorage
{
	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException;

	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException;

	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException;

	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException;

	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException;

	public void backupUsers(final File file)
	throws FileNotFoundException, IOException;

	public void backupFollowing(final File file)
	throws FileNotFoundException, IOException;
}