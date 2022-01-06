package server;

import java.rmi.Remote;
import java.rmi.RemoteException;

import client.RMIFollowers;

/**
 * @brief Server interface for RMI callbacks.
 * @author Giacomo Trapani
 */
public interface RMICallback extends Remote
{
	/**
	 * @brief Registers given client for callbacks.
	 * @param client cannot be null.
	 * @throws NullPointerException if client is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	void registerForCallback(RMIFollowers client)
	throws NullPointerException, RemoteException;

	/**
	 * @brief Unregisters given client for callbacks.
	 * @param client cannot be null.
	 * @throws NullPointerException if client is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	void unregisterForCallback(RMIFollowers client)
	throws NullPointerException, RemoteException;
}
