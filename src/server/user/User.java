package server.user;

import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class User
{
	public final String username;
	public final String hashPassword;
	public final byte[] saltUsed;
	private SocketChannel loggedIn;
	private Set<Tag> tags = null;
	private static final int MAXIMUM_TAG_SET_SIZE = 5;


	public User(final String username, final String hashPassword, Set<String> tags, final byte[] saltUsed)
	throws NullPointerException, InvalidTagException, TagListTooLongException
	{
		if (username == null || hashPassword == null || tags == null || saltUsed == null)
			throw new NullPointerException("Constructor parameters cannot be null.");
		this.username = username;
		this.hashPassword = hashPassword;
		this.tags = new HashSet<>();
		for (String t : tags)
		{
			if (t == null) throw new NullPointerException("Tag cannot be null.");
			this.tags.add(new Tag(t));
		}
		if (this.tags.size() > MAXIMUM_TAG_SET_SIZE)
			throw new TagListTooLongException("No more than 5 different tags can be chosen.");
		this.saltUsed = Arrays.copyOf(saltUsed, saltUsed.length);
		this.loggedIn = null;
	}

	public boolean addTag(Tag tag)
	{
		if (tag == null) throw new NullPointerException("Null tag cannot be followed.");
		return tags.add(tag);
	}

	public void login(SocketChannel client, String hashPassword)
	throws InvalidLoginException, WrongCredentialsException
	{
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
}
