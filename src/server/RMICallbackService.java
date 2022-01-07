package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import client.RMIFollowers;

public class RMICallbackService extends UnicastRemoteObject implements RMICallback
{
	private Set<RMIFollowers> clients = null;
	private Set<RMIFollowers> toDelete = null;

	private static final String NULL_ERROR = " cannot be null.";

	public RMICallbackService()
	throws RemoteException
	{
		this.clients = new HashSet<>();
		this.toDelete = new HashSet<>();
	}

	public void registerForCallback(RMIFollowers client)
	throws NullPointerException, RemoteException
	{
		clients.add(Objects.requireNonNull(client, "Client" + NULL_ERROR));
	}

	public void unregisterForCallback(RMIFollowers client)
	throws NullPointerException, RemoteException
	{
		clients.remove(Objects.requireNonNull(client, "Client" + NULL_ERROR));
	}

	/**
	 * @brief Notifies clients and registers new follower.
	 * @param follower cannot be null.
	 * @param followed cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void notifyNewFollower(String follower, String followed)
	throws NullPointerException
	{
		Objects.requireNonNull(follower, "Follower user's username" + NULL_ERROR);
		Objects.requireNonNull(followed, "Followed user's username" + NULL_ERROR);

		synchronized(this)
		{
			for (RMIFollowers c: clients)
			{
				try { c.registerNewFollower(follower, followed); }
				catch (RemoteException clientDisconnected) { toDelete.add(c); }
			}
			for (RMIFollowers c: toDelete)
				clients.remove(c);
			toDelete = new HashSet<>();
		}
	}

	public void notifyUnfollow(final String follower, final String followed)
	{
		Objects.requireNonNull(follower, "Follower user's username" + NULL_ERROR);
		Objects.requireNonNull(followed, "Followed user's username" + NULL_ERROR);

		synchronized(this)
		{
			for (RMIFollowers c: clients)
			{
				try { c.removeFollower(follower, followed); }
				catch (RemoteException clientDisconnected) { toDelete.add(c); }
			}
			for (RMIFollowers c: toDelete)
				clients.remove(c);
			toDelete = new HashSet<>();
		}
	}
}