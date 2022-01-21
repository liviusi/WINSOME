package user;

import java.nio.channels.SocketChannel;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * @brief Class used to denote a user registered on WINSOME. This class is thread-safe.
 * @author Giacomo Trapani
 */
public class User
{
	/** User's username. */
	public final String username;
	/** User's hash of the password. */
	public final String hashPassword;
	/** User's salt decoded with US ASCII. */
	public final String saltDecoded;
	/** Identifier of the client which has logged in with this user's credentials. It is set to null on default. */
	private SocketChannel loggedIn;
	/** Set of the tags the user is interested in. */
	private Set<Tag> tags = null;
	/** Set of the usernames of the users this user is currently following. */
	private Set<String> following = null;

	/** Maximum amount of tags to be accepted. */
	private static final int MAXIMUM_TAG_SET_SIZE = 5;
	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * @brief Default constructor.
	 * @param username cannot be null.
	 * @param hashPassword cannot be null.
	 * @param tags cannot be null.
	 * @param saltUsed cannot be null.
	 * @throws NullPointerException if any parameter is null or there exists a string inside the set of tags which is null.
	 * @throws InvalidTagException if there exists a string inside the set of tags which is not a valid tag name.
	 * @throws TagListTooLongException if the set of tags has more than 5 elements.
	 */
	public User(final String username, final String hashPassword, Set<String> tags, final byte[] saltUsed)
	throws NullPointerException, InvalidTagException, TagListTooLongException
	{
		Objects.requireNonNull(tags, "Tags" + NULL_ERROR);
		Objects.requireNonNull(saltUsed, "Salt" + NULL_ERROR);
		this.username = Objects.requireNonNull(username, "Username" + NULL_ERROR);
		this.hashPassword = Objects.requireNonNull(hashPassword, "Hashed password" + NULL_ERROR);
		this.tags = new HashSet<>();
		for (String t : tags)
			this.tags.add(new Tag(Objects.requireNonNull(t, "Tag name" + NULL_ERROR)));
		if (this.tags.size() > MAXIMUM_TAG_SET_SIZE)
			throw new TagListTooLongException("No more than 5 different tags can be chosen.");
		this.saltDecoded = Base64.getEncoder().encodeToString(saltUsed);
		this.loggedIn = null;
		this.following = new HashSet<>();
	}

	/**
	 * @brief Getter for this user's tags.
	 * @return a shallow copy of this user's tags.
	 */
	public Set<Tag> getTags()
	{
		Set<Tag> r = new HashSet<>();
		synchronized(this) { r.addAll(tags); }
		return r;
	}

	/**
	 * @brief Getter for the usernames of the users this user is currently following.
	 * @return a copy of the usernames of the users this user is currently following.
	 */
	public Set<String> getFollowing()
	{
		Set<String> res = new HashSet<>();
		synchronized(this) { res.addAll(following); }
		return res;
	}

	/**
	 * @brief Has the given client login if the credentials are correct.
	 * @param clientID cannot be null.
	 * @param hashPassword cannot be null.
	 * @throws InvalidLoginException if a client has already logged in with this client.
	 * @throws WrongCredentialsException if the credentials are wrong.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void login(SocketChannel clientID, String hashPassword)
	throws InvalidLoginException, WrongCredentialsException, NullPointerException
	{
		Objects.requireNonNull(clientID, "Client" + NULL_ERROR);
		Objects.requireNonNull(hashPassword, "Hashed password" + NULL_ERROR);
		synchronized(this)
		{
			if (this.loggedIn != null)
				throw new InvalidLoginException("User has already logged in.");
			if (!hashPassword.equals(this.hashPassword))
				throw new WrongCredentialsException("Wrong password!");
			this.loggedIn = clientID;
		}
	}

	/**
	 * @brief Has the given client logout from this user.
	 * @param clientID cannot be null.
	 * @throws InvalidLogoutException if the client is not logged in to this user.
	 * @throws NullPointerException if the client is null.
	 */
	public void logout(SocketChannel clientID)
	throws InvalidLogoutException, NullPointerException
	{
		Objects.requireNonNull(clientID, "Client" + NULL_ERROR);
		synchronized(this)
		{
			if (this.loggedIn != clientID)
				throw new InvalidLogoutException("User is not logged in from this client.");
			this.loggedIn = null;
		}
	}

	/**
	 * @brief Has this user start following u.
	 * @param u cannot be null.
	 * @return true on success, false on failure. This function may fail if this user is already following
	 * u or this user and u are the very same user.
	 * @throws NullPointerException if u is null.
	 */
	public boolean follow(User u)
	throws NullPointerException
	{
		Objects.requireNonNull(u, "User cannot be null");
		boolean result = false;
		synchronized(this)
		{
			if (u.equals(this)) return false;
			result = following.add(u.username);
		}
		return result;
	}

	/**
	 * @brief Has this user stop following u.
	 * @param u cannot be null.
	 * @return true on success, false on failure. This function may fail if this user is not following
	 * u or this user and u are the very same user.
	 * @throws NullPointerException if u is null.
	 */
	public boolean unfollow(User u)
	throws NullPointerException
	{
		Objects.requireNonNull(u, "User cannot be null");
		boolean result = false;
		synchronized(this)
		{
			if (u.equals(this)) return false;
			result = following.remove(u.username);
		}
		return result;
	}

	/**
	 * @brief Equality check is made on user's username.
	 * @param o object to check the uguality against.
	 */
	@Override
	public boolean equals(Object o)
	{
		return o instanceof User && this.username.equals(((User) o).username);
	}

	/** Hash function depends on this user's username. */
	@Override
	public int hashCode()
	{
		return username.hashCode();
	}

	public String toString()
	{
		return String.format("{ \"username\": \"%s\", \"tags\": [%s]", username, setToString(tags)) + "}";
	}

	private <T> String setToString(Set<T> set)
	{
		Iterator<T> it = set.iterator();
		StringBuilder sb = new StringBuilder();
		while (it.hasNext())
		{
			sb.append(it.next());
			if (it.hasNext()) sb.append(",");
		}
		return sb.toString();
	}
}
