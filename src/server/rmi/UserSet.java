package server.rmi;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import user.*;

public class UserSet implements UserStorage
{
	private Set<User> users = null;

	public UserSet()
	{
		users = ConcurrentHashMap.newKeySet();
	}

	public boolean register(String username, char[] password, Set<String> tags) throws RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
			PasswordNotValidException, InvalidTagException
	{
		// decrypt password
		// validate signature
		// hash password + salt
		String tmp = username.replaceAll("[^a-zA-Z0-9]", "");
		if (username.isEmpty()) throw new UsernameNotValidException("Username cannot be empty.");
		User u = new User(tmp, password, tags);
		if (!users.add(u))
			throw new UsernameAlreadyExistsException("Username has already been taken.");
		return true;
	}

	public static UserSet fromJSON(File file)
	{
		return null;
	}
}
