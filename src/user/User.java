package user;

import java.nio.channels.SocketChannel;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User
{
	public final String username;
	public final String hashPassword;
	public final String saltDecoded;
	private SocketChannel loggedIn;
	private Set<Tag> tags = null;
	private static final int MAXIMUM_TAG_SET_SIZE = 5;
	private Set<String> following = null;


	public User(final String username, final String hashPassword, Set<String> tags, final byte[] saltUsed)
	throws NullPointerException, InvalidTagException, TagListTooLongException
	{
		Objects.requireNonNull(tags, "Tags cannot be null.");
		this.username = Objects.requireNonNull(username, "Username cannot be null.");
		this.hashPassword = Objects.requireNonNull(hashPassword, "Hashed password cannot be null.");
		this.tags = new HashSet<>();
		for (String t : tags)
			this.tags.add(new Tag(t));
		if (this.tags.size() > MAXIMUM_TAG_SET_SIZE)
			throw new TagListTooLongException("No more than 5 different tags can be chosen.");
		this.saltDecoded = Base64.getEncoder().encodeToString(Objects.requireNonNull(saltUsed, "Salt cannot be null."));
		this.loggedIn = null;
		this.following = new HashSet<>();
	}

	public void login(SocketChannel client, String hashPassword)
	throws InvalidLoginException, WrongCredentialsException
	{
		Objects.requireNonNull(client, "Client cannot be null.");
		Objects.requireNonNull(hashPassword, "Hashed password cannot be null.");
		synchronized(this)
		{
			if (this.loggedIn != null)
				throw new InvalidLoginException("User has already logged in.");
			if (!hashPassword.equals(this.hashPassword))
				throw new WrongCredentialsException("Wrong password!");
			this.loggedIn = client;
		}
	}

	public void logout(SocketChannel client)
	throws InvalidLogoutException
	{
		synchronized(this)
		{
			if (this.loggedIn != client)
				throw new InvalidLogoutException("User is not logged in from this client.");
			this.loggedIn = null;
		}
	}

	public boolean equals(User u)
	{
		return this.username.equals(u.username);
	}

	public Set<Tag> getTags()
	{
		Set<Tag> r = new HashSet<>();
		synchronized(this) { r.addAll(tags); }
		return r;
	}

	public boolean follow(User u)
	{
		Objects.requireNonNull(u, "User cannot be null");
		if (u.equals(this)) throw new IllegalArgumentException("A user cannot follow themselves.");
		return following.add(u.username);
	}

	public Set<String> getFollowing()
	{
		Set<String> res = new HashSet<>();
		synchronized(this) { res.addAll(following); }
		return res;
	}
}
