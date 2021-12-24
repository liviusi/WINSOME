package server.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

import user.InvalidTagException;

public interface UserStorage extends Remote
{
	public boolean register(String username, char[] password, Set<String> tags) throws RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
			PasswordNotValidException, InvalidTagException;
}
