package api.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Server interface for RMI callbacks.
 * @author Giacomo Trapani.
 */
public interface RMICallback extends Remote
{
	/**
	 * Registers given client for callbacks regarding a certain username.
	 * @param client cannot be null.
	 * @throws NullPointerException if client is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void registerForCallback(RMIFollowers client, final String username)
	throws NullPointerException, RemoteException;

	/**
	 * Unregisters given client for callbacks.
	 * @param client cannot be null.
	 * @throws NullPointerException if client is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void unregisterForCallback(RMIFollowers client)
	throws NullPointerException, RemoteException;
}
