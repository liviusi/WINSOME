package server.storage;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

import server.user.InvalidTagException;
import server.user.TagListTooLongException;

/**
 * Interface for RMI register method.
 * @author Giacomo Trapani.
 */
@FunctionalInterface
public interface UserRMIStorage extends Remote
{
	/**
	 * Registers a user to WINSOME.
	 * @param username cannot be null. It will be stripped of any non-alphanumeric character.
	 * @param password cannot be null or the hash of the empty string.
	 * @param tags list of tags the username is interested in.
	 * @param salt cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 * @throws RemoteException if a remote error occurs.
	 * @throws UsernameNotValidException if the username does not contain any alphanumeric character.
	 * @throws UsernameAlreadyExistsException if the username is already taken.
	 * @throws PasswordNotValidException if the password is the empty string.
	 * @throws InvalidTagException if there exists a tag in the set of which the name stripped of any non-alphanumeric
	 * character is the empty string.
	 * @throws TagListTooLongException if the list of tags consists of more than 5 elements.
	 */
	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException;
}
