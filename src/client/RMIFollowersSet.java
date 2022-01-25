package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class implementation of RMI callbacks interface using a concurrent hashset.
 * @author Giacomo Trapani.
 */
public class RMIFollowersSet extends UnicastRemoteObject implements RMIFollowers
{
	private Set<String> followers = ConcurrentHashMap.newKeySet();

	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * Default constructor.
	 * @param followers cannot be null or contain a null value.
	 * @throws RemoteException if a remote error occurs.
	 * @throws NullPointerException if follower is null or contains null.
	*/
	public RMIFollowersSet(Set<String> followers)
	throws RemoteException, NullPointerException
	{
		super();
		this.followers.addAll(Objects.requireNonNull(followers));
	}

	public void registerNewFollower(String follower)
	throws NullPointerException, RemoteException
	{
		Objects.requireNonNull(follower, "Follower user's username" + NULL_ERROR);
		followers.add(follower);
	}

	public void removeFollower(final String follower)
	throws NullPointerException, RemoteException
	{
		Objects.requireNonNull(follower, "Follower user's username" + NULL_ERROR);
		followers.remove(follower);
	}

	/**
	 * Recovers the set of the usernames of the users following this user.
	 * @param username cannot be null.
	 * @return a copy of the set of the usernames of the users following this user.
	 */
	public Set<String> recoverFollowers()
	throws NullPointerException
	{

		Set<String> res = new HashSet<>();
		res.addAll(followers);

		return res;

	}
}
