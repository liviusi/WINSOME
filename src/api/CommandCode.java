package api;

/**
 * @brief Enum used to represent valid commands, which are expected to be sent from the client and properly
 * handled by the server.
 * @author Giacomo Trapani
 */
public enum CommandCode
{
	LOGINSETUP("Login setup"),
	LOGINATTEMPT("Login"),
	PULLFOLLOWERS("Pull followers"),
	LOGOUT("Logout"),
	LISTUSERS("List users"),
	LISTFOLLOWING("List following"),
	FOLLOWUSER("Follow"),
	UNFOLLOWUSER("Unfollow"),
	CREATEPOST("Post"),
	COMMENT("Comment"),
	RATE("Rate");

	/** Used to represent the command as a string. */
	public final String description;

	/**
	 * @brief Private constructor used to initialize a CommandCode.
	 * @param description Used to describe the code.
	 */
	private CommandCode(String description) { this.description = description; }
}
