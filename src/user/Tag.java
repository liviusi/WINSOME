package user;

import java.util.Locale;

public class Tag
{
	public final String name;

	public Tag(String name) throws NullPointerException, InvalidTagException
	{
		if (name == null) throw new NullPointerException("Tag name cannot be null.");
		String tmp = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ITALIAN);
		if (tmp.isEmpty()) throw new InvalidTagException("Tags must be non-empty alphanumeric strings.");
		this.name = tmp;
	}
}
