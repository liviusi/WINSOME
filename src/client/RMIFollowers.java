package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @brief Client interface for RMI callbacks.
 * @author Giacomo Trapani
 */
public interface RMIFollowers extends Remote
{

	/**
	 * @brief Registers new follower.
	 * @param follower cannot be null.
	 * @param followed cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void registerNewFollower(String follower, String followed)
	throws NullPointerException, RemoteException;

	public void removeFollower(final String follower, final String followed)
	throws NullPointerException, RemoteException;
}
