package server.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

import user.InvalidTagException;

public interface UserStorage extends Remote
{
	public boolean register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException;
}
