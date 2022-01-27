package api.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Client interface for RMI callbacks.
 * @author Giacomo Trapani.
 */
public interface RMIFollowers extends Remote
{
	/**
	 * Registers new follower.
	 * @param follower cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void registerNewFollower(final String follower)
	throws NullPointerException, RemoteException;

	/**
	 * Removes a follower.
	 * @param follower cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 * @throws RemoteException if a remote error occurs.
	 */
	public void removeFollower(final String follower)
	throws NullPointerException, RemoteException;
}
