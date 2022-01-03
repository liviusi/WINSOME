package server.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

import server.user.InvalidTagException;
import server.user.TagListTooLongException;

public interface UserRMIStorage extends Remote
{
	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException;
}
