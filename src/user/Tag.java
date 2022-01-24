package user;

import java.util.Locale;
import java.util.Objects;

/**
 * @brief Class used to identify a tag i.e. an interest expressed by a certain user.
 * @author Giacomo Trapani.
 */
public class Tag
{
	/** Name of the tag. */
	public final String name;

	/**
	 * @brief Default constructor.
	 * @param name cannot be null, it will be stripped of any non-alphanumeric character and converted to
	 * lower case.
	 * @throws InvalidTagException if name does not contain any non-alphanumeric character.
	 * @throws NullPointerException if name is null.
	 */
	public Tag(final String name)
	throws InvalidTagException, NullPointerException
	{
		String tmp = Objects.requireNonNull(name, "Tag name cannot be null.").replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ITALIAN);
		if (tmp.isEmpty()) throw new InvalidTagException("Tags must be non-empty alphanumeric strings.");
		this.name = tmp;
	}

	/**
	 * @brief Equality check is made on tag's name.
	 * @param o object to check the uguality against.
	 */
	@Override
	public boolean equals(Object o)
	{
		return o instanceof Tag && ((Tag) o).name.equals(name);
	}

	/** Hash function depends on tag's name. */
	@Override
	public int hashCode()
	{
		return name.hashCode();
	}

	public String toString()
	{
		return name;
	}
}
