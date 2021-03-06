package server;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;

import api.rmi.UserRMIStorage;
import configuration.ServerConfiguration;
import server.storage.UserMap;

/**
 * Utility class used to group together the whole RMI logic as a single task.
 * @author Giacomo Trapani
 */
public class RMITask implements Runnable
{
	/** Name of the address the registry is to be located on. */
	public final String registryAddressName;
	/** Port the registry is to be located on. */
	public final int port;
	/** Name of the registration service. */
	public final String registerServiceName;
	/** Name of the callback service. */
	public final String callbackServiceName;
	/** User storage */
	private UserMap users = null;
	/** Callback service. */
	private RMICallbackService callbackService = null;
	/** Time to be spent sleeping. */
	public static final int TIMEOUT = 10000;

	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * Default constructor.
	 * @param configuration cannot be null.
	 * @param users cannot be null.
	 * @param callbackService cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 */
	public RMITask(ServerConfiguration configuration, UserMap users, RMICallbackService callbackService)
	throws NullPointerException
	{
		Objects.requireNonNull(configuration, "Configuration" + NULL_ERROR);
		Objects.requireNonNull(users, "User map" + NULL_ERROR);
		Objects.requireNonNull(callbackService, "Callback service" + NULL_ERROR);
		this.registryAddressName = configuration.registryAddressName;
		this.port = configuration.portNoRegistry;
		this.registerServiceName = configuration.registerServiceName;
		this.callbackServiceName = configuration.callbackServiceName;
		this.users = users;
		this.callbackService = callbackService;
	}

	public void run()
	{
		UserRMIStorage registerService = null;
		Registry r = null;
		try
		{
			LocateRegistry.createRegistry(port);
			r = LocateRegistry.getRegistry(registryAddressName, port);
			registerService = (UserRMIStorage) UnicastRemoteObject.exportObject(users, 0);
			r.rebind(registerServiceName, registerService);
			r.rebind(callbackServiceName, callbackService);
		}
		catch (RemoteException e)
		{
			System.err.println("Fatal error occurred in RMITask.");
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("RMI task is now running!");
		/** This thread needs not to die before deallocating the resources used for RMI. */
		while (!Thread.currentThread().isInterrupted())
		{
			try { Thread.sleep(TIMEOUT); }
			catch (InterruptedException shutdown) { break; }
		}
		try
		{
			// deallocate resources:
			r.unbind(registerServiceName);
			UnicastRemoteObject.unexportObject(users, true);
			return;
		}
		catch (NotBoundException | RemoteException ignored) { return; }
	}
}
