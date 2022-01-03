package api;

public enum CommandCode
{
	LOGINSETUP("Login setup"),
	LOGINATTEMPT("Login"),
	LOGOUT("Logout"),
	LISTUSERS("List users");

	private String description;

	private CommandCode(String description) { this.description = description; }

	public String getDescription() { return this.description; }
}
