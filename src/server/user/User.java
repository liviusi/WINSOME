package server.user;

import java.util.HashSet;
import java.util.Set;

public class User
{
	public final String username;
	public final String hashPassword;
	private Set<Tag> tags = null;
	private static final int MAXIMUM_TAG_SET_SIZE = 5;

	public User(final String username, final String hashPassword, Set<String> tags)
	throws NullPointerException, InvalidTagException, TagListTooLongException
	{
		if (username == null || hashPassword == null || tags == null)
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
	}

	public boolean addTag(Tag tag)
	{
		if (tag == null) throw new NullPointerException("Null tag cannot be followed.");
		return tags.add(tag);
	}
}
