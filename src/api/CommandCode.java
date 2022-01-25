package api;

/**
 * Enum used to represent valid commands, which are expected to be sent from the client and properly
 * handled by the server.
 * @author Giacomo Trapani.
 */
public enum CommandCode
{
	/** Command code used to retrieve user's salt. */
	LOGINSETUP("Login setup"),
	/** Command code used to login. */
	LOGINATTEMPT("Login"),
	/** Command code used to retrieve old followers. */
	PULLFOLLOWERS("Pull followers"),
	/** Command code used to ask for multicast coordinates. */
	RETRIEVEMULTICAST("Retrieve multicast"),
	/** Command code used to logout. */
	LOGOUT("Logout"),
	/** Command code used to retrieve the list of users currently sharing an interest with the caller. */
	LISTUSERS("List users"),
	/** Command code used to retrieve the list of users currently followed by the caller. */
	LISTFOLLOWING("List following"),
	/** Command code used to follow another user. */
	FOLLOWUSER("Follow"),
	/** Command code used to unfollow another user. */
	UNFOLLOWUSER("Unfollow"),
	/** Command code used to retrieve the list of the posts by the caller. */
	VIEWBLOG("Blog"),
	/** Command code used to add a post. */
	CREATEPOST("Post"),
	/** Command code used to retrieve the list of the posts on caller's feed. */
	SHOWFEED("Show feed"),
	/** Command code used to show a post. */
	SHOWPOST("Show post"),
	/** Command code used to delete a post. */
	DELETEPOST("Delete post"),
	/** Command code used to ask for a certain post to be rewon by the caller. */
	REWIN("Rewin"),
	/** Command code used to add a new comment to a post. */
	COMMENT("Comment"),
	/** Command code used to rate a certain post. */
	RATE("Rate"),
	/** Command code used to retrieve each and every transaction the caller's been involved with and the total amount of WINCOINS currently owned. */
	WALLET("Wallet"),
	/** Command code used to show user's amount of WINCOINS converted to BTC. */
	WALLETBTC("Wallet BTC");

	/** Used to represent the command as a string. */
	public final String description;

	/**
	 * Private constructor used to initialize a CommandCode.
	 * @param description Used to describe the code.
	 */
	private CommandCode(String description) { this.description = description; }
}
