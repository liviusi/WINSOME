package user;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class User
{
	public final String username;
	public final char[] hashPassword;
	private Set<Tag> tags = null;

	public User(final String username, final char[] hashPassword, Set<String> tags) throws NullPointerException, InvalidTagException
	{
		if (username == null || hashPassword == null || tags == null)
			throw new NullPointerException("Constructor parameters cannot be null.");
		this.username = username;
		this.hashPassword = Arrays.copyOf(hashPassword, hashPassword.length);
		this.tags = new HashSet<>();
		for (String t : tags)
		{
			if (t == null) throw new NullPointerException("Tag cannot be null.");
			this.tags.add(new Tag(t));
		}
	}

	public boolean addTag(Tag tag)
	{
		if (tag == null) throw new NullPointerException("Null tag cannot be followed.");
		return tags.add(tag);
	}
}
