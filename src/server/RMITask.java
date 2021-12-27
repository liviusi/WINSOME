package server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import configuration.ServerConfiguration;
import server.rmi.UserSet;
import server.rmi.UserStorage;

public class RMITask implements Runnable
{
	public final int port;
	public final String serviceName;

	public RMITask(ServerConfiguration configuration)
	{
		this.port = configuration.portNoRegistry;
		this.serviceName = configuration.registerServiceName;
	}

	public void run()
	{
		UserSet users = new UserSet();
		UserStorage stub = null;
		Registry r = null;
		try
		{
			stub = (UserStorage) UnicastRemoteObject.exportObject(users, 0);
			LocateRegistry.createRegistry(port);
			r = LocateRegistry.getRegistry(port);
			r.rebind(serviceName, stub);
		}
		catch (RemoteException e)
		{
			System.err.printf("Fatal error occurred:\n%s\n", e.getMessage());
			System.exit(1);
		}
		System.out.println("ok rmi!");
	}
}
