package api;

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

public class Register
{

	private final int portNo;
	private final String serviceName;

	private Register(int portNo, String serviceName)
	{
		this.portNo = portNo;
		this.serviceName = serviceName;
	}

	public static void register(String username, String password, Set<String> tags, int portNo, String serviceName)
	throws RemoteException, NotBoundException, NullPointerException, UsernameNotValidException, UsernameAlreadyExistsException,
			PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		Register register = new Register(portNo, serviceName);
		Registry r = LocateRegistry.getRegistry(register.portNo);
		UserStorage service = (UserStorage) r.lookup(register.serviceName);
		byte[] salt = Passwords.generateSalt();
		service.register(username, password, tags, salt);
	}
}
