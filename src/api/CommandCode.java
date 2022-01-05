package api;

public enum CommandCode
{
	LOGINSETUP("Login setup"),
	LOGINATTEMPT("Login"),
	LOGOUT("Logout"),
	LISTUSERS("List users"),
	FOLLOWUSER("Follow");

	private String description;

	private CommandCode(String description) { this.description = description; }

	public String getDescription() { return this.description; }
}
