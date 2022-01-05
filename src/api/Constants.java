package api;

public class Constants
{
	public final static int BUFFERSIZE = 2048;

	public static final String LOGIN_SUCCESS = " has now logged in";
	public static final String NOT_REGISTERED = " has yet to sign up";
	public static final String CLIENT_ALREADY_LOGGED_IN = "Client has already logged in";
	public static final String LOGOUT_SUCCESS = " has now logged out";
	public static final String CLIENT_NOT_LOGGED_IN = "Client has yet to login";
	public static final String ALREADY_FOLLOWS = " is already following ";
	public static final String FOLLOW_SUCCESS = " is now following ";
	
	public static final String DELIMITER = ":";
	public static final String QUIT_STRING = ":q!";
	public static final String HELP_STRING = "help";
	public static final String REGISTER_STRING = "register";
	public static final String LOGIN_STRING = "login";
	public static final String LOGOUT_STRING = "logout";
	public static final String LIST_USERS_STRING = "list users";
	public static final String FOLLOW_USER_STRING = "follow";

	private Constants() { }
}
