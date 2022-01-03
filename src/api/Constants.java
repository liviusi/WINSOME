package api;

public class Constants
{
	public final static int BUFFERSIZE = 2048;

	public final static String LOGIN_SUCCESS = " has now logged in.";
	public final static String USER_NOT_REGISTERED = " has yet to sign up.";
	public final static String CLIENT_ALREADY_LOGGED_IN = "Client has already logged in.";
	public final static String LOGOUT_SUCCESS = "Client has now logged out.";
	public final static String LOGOUT_FAILURE = "Client has yet to login.";
	public final static String DELIMITER = ":";

	public final static String QUIT_STRING = ":q!";
	public final static String HELP_STRING = "help";
	public final static String REGISTER_STRING = "register";
	public final static String LOGIN_STRING = "login";

	private Constants() { }
}
