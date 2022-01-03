package server;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import configuration.ServerConfiguration;
import server.rmi.UserMap;
import server.rmi.UserRMIStorage;

public class RMITask implements Runnable
{
	public final int port;
	public final String serviceName;
	private UserMap users = null;
	public static final int TIMEOUT = 10000;

	public RMITask(ServerConfiguration configuration, UserMap users)
	{
		this.port = configuration.portNoRegistry;
		this.serviceName = configuration.registerServiceName;
		this.users = users;
	}

	public void run()
	{
		UserRMIStorage stub = null;
		Registry r = null;
		try
		{
			stub = (UserRMIStorage) UnicastRemoteObject.exportObject(users, 0);
			LocateRegistry.createRegistry(port);
			r = LocateRegistry.getRegistry(port);
			r.rebind(serviceName, stub);
		}
		catch (RemoteException e)
		{
			System.err.printf("Fatal error occurred:\n%s\n", e.getMessage());
			System.exit(1);
		}
		System.out.println("RMI setup complete.");
		while (true)
		{
			try { Thread.sleep(TIMEOUT); }
			catch (InterruptedException shutdown)
			{
				try
				{
					r.unbind(serviceName);
					UnicastRemoteObject.unexportObject(users, true);
					System.out.println("RMI shutdown complete.");
				}
				catch (NotBoundException | RemoteException ignored)
				{
					return;
				}
			}
		}
	}
}
