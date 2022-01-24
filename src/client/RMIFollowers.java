package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @brief Client interface for RMI callbacks.
 * @author Giacomo Trapani.
 */
public interface RMIFollowers extends Remote
{
	/**
	 * @brief Registers new follower.
	 * @param follower cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void registerNewFollower(final String follower)
	throws NullPointerException, RemoteException;

	/**
	 * @brief Removes a follower.
	 * @param follower cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void removeFollower(final String follower)
	throws NullPointerException, RemoteException;
}
