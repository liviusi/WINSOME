package api;

public class Constants
{
	public final static int BUFFERSIZE = 2048;
	
	public static final String DELIMITER = "\r\n";
	public static final String QUIT_STRING = ":q!";
	public static final String HELP_STRING = "help";
	public static final String REGISTER_STRING = "register";
	public static final String LOGIN_STRING = "login";
	public static final String LOGOUT_STRING = "logout";
	public static final String LIST_USERS_STRING = "list users";
	public static final String LIST_FOLLOWERS_STRING = "list followers";
	public static final String LIST_FOLLOWING_STRING = "list following";
	public static final String FOLLOW_USER_STRING = "follow";
	public static final String UNFOLLOW_USER_STRING = "unfollow";
	public static final String CREATE_POST_STRING = "post";
	public static final String COMMENT_STRING = "comment";
	public static final String RATE_STRING = "rate";

	private Constants() { }
}
