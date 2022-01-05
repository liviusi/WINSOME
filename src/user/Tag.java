package user;

import java.util.Locale;
import java.util.Objects;

public class Tag
{
	public final String name;

	public Tag(final String name)
	throws NullPointerException, InvalidTagException
	{
		Objects.requireNonNull(name, "Tag name cannot be null.");
		String tmp = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ITALIAN);
		if (tmp.isEmpty()) throw new InvalidTagException("Tags must be non-empty alphanumeric strings.");
		this.name = tmp;
	}

	public boolean equals(Object tag)
	{
		return tag instanceof Tag && ((Tag) tag).name.equals(name);
	}

	public int hashCode()
	{
		return name.hashCode();
	}
}
