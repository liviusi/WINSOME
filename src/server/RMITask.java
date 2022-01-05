package server;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;

import configuration.ServerConfiguration;
import server.storage.UserMap;
import server.storage.UserRMIStorage;

/**
 * @brief Utility class used to group together the whole RMI logic.
 * @author Giacomo Trapani
 */
public class RMITask implements Runnable
{
	/** Port the registry is located on. */
	public final int port;
	/** Name of the registration service. */
	public final String serviceName;
	/** User storage */
	private UserMap users = null;
	/** Time to be spent sleeping. */
	public static final int TIMEOUT = 10000;

	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * @brief Default constructor.
	 * @param configuration cannot be null.
	 * @param users cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 */
	public RMITask(ServerConfiguration configuration, UserMap users)
	throws NullPointerException
	{
		Objects.requireNonNull(configuration, "Configuration" + NULL_ERROR);
		Objects.requireNonNull(users, "User map" + NULL_ERROR);
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
		/**
		 * This thread needs not to die before deallocating the resources used for RMI.
		 */
		while (true)
		{
			try { Thread.sleep(TIMEOUT); }
			catch (InterruptedException shutdown)
			{
				try
				{
					// deallocate resources:
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
