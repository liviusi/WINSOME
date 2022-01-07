package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @brief Class implementation of RMI callbacks interface using a concurrent hashmap.
 * @author Giacomo Trapani
 */
public class RMIFollowersMap extends UnicastRemoteObject implements RMIFollowers
{
	private static Map<String, Set<String>> followers = new ConcurrentHashMap<>();

	/** Default constructor. */
	public RMIFollowersMap()
	throws RemoteException
	{
		super();
	}

	public void registerNewFollower(String follower, String followed)
	throws NullPointerException, RemoteException
	{
		Objects.requireNonNull(follower, "Follower user's username cannot be null.");
		Objects.requireNonNull(followed, "Followed user's username cannot be null.");
		followers.putIfAbsent(followed, ConcurrentHashMap.newKeySet());
		followers.get(followed).add(follower);
	}

	public void removeFollower(final String follower, final String followed)
	throws NullPointerException, RemoteException
	{
		Objects.requireNonNull(follower, "Follower user's username cannot be null.");
		Objects.requireNonNull(followed, "Followed user's username cannot be null.");
		followers.putIfAbsent(followed, ConcurrentHashMap.newKeySet());
		followers.get(followed).remove(follower);
	}

	/**
	 * @brief Recovers the set of the usernames of the users following given user.
	 * @param username cannot be null.
	 * @return a copy of the set of the usernames of the users following given user.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> recoverFollowers(String username)
	throws NullPointerException
	{
		Objects.requireNonNull(username, "Username cannot be null.");

		Set<String> set = followers.get(username);

		return set == null ? new HashSet<>() : set;

	}
}
