package server.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;

import server.post.Post.GainAndCurators;
import user.InvalidAmountException;
import user.InvalidLoginException;
import user.InvalidLogoutException;
import user.SameUserException;
import user.WrongCredentialsException;

/**
 * @brief Interface to be implemented by an actual user storage class.
 * @author Giacomo Trapani
 */
public interface UserStorage
{
	/**
	 * @brief Recovers the set of the usernames of the users the given user is currently followed by.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return the usernames of every user currently following given user.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> recoverFollowers(final String username)
	throws NoSuchUserException, NullPointerException;

	public void updateRewards(final Map<String, GainAndCurators> gains, final double authorPercentage)
	throws InvalidAmountException, NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles the setup needed for a user to login i.e. it recovers the user's salt
	 * encoded with US ASCII.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return the decoded salt to use when hashing the password during the login procedure.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles login.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @param clientID cannot be null.
	 * @param hashPassword cannot be null.
	 * @throws InvalidLoginException if there's already a client logged in with given username.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws WrongCredentialsException if the password associated with username is not equal to the one given
	 * as an input parameter.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException;

	/**
	 * @brief Handles logout.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @param clientID cannot be null.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws InvalidLogoutException if this client had yet to login with given username.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException, NullPointerException;

	/**
	 * @brief Handles list users.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return a set of strings following the format CONCAT(USERNAME, "\r\n", TAG_{1},...,TAG_{n}) with USERNAME
	 * representing the username of a user different from the one in input sharing a common interest with it and
	 * TAG_{i} the i-th tag USERNAME is interested in.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles list following.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return a set of string following the format CONCAT(USERNAME, "\r\n", TAG_{1},...,TAG_{n}) with USERNAME
	 * representing the username of a user the one in input is following and TAG_{i} the i-th tag USERNAME is interested in.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> handleListFollowing(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles follow user.
	 * @param followerUsername cannot be null, must belong to WINSOME registered users' set.
	 * @param followedUsername cannot be null, must belong to WINSOME registered users' set.
	 * @return true on success, false on failure.
	 * @throws NoSuchUserException if at least one of the usernames specified does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws SameUserException, NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles unfollow user.
	 * @param followerUsername cannot be null, must belong to WINSOME registered users' set.
	 * @param followedUsername cannot be null, must belong to WINSOME registered users' set.
	 * @return true on success, false on failure.
	 * @throws NoSuchUserException if at least one of the usernames specified does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public boolean handleUnfollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException;

	/**
	 * @brief Backs up WINSOME registered users' set according to json syntax.
	 * @param file the file to store the list in (will be overwritten).
	 * @throws FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws NullPointerException if file is null.
	 */
	public void backupUsers(final File file)
	throws FileNotFoundException, IOException, NullPointerException;

	/**
	 * @brief For each user registered to WINSOME, it backs up their set of followed users according to json syntax.
	 * @param file the file to store the list in (will be overwritten).
	 * @throws FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws NullPointerException if file is null.
	 */
	public void backupFollowing(final File file)
	throws FileNotFoundException, IOException, NullPointerException;
}