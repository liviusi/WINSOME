package server.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;

import server.post.Post.GainAndCurators;
import server.user.InvalidAmountException;
import server.user.InvalidLoginException;
import server.user.InvalidLogoutException;
import server.user.SameUserException;
import server.user.WrongCredentialsException;

/**
 * Interface to be implemented by an actual user storage class.
 * NOTATION: A stringified User u is defined as a String containing u's username and u's tags following JSON syntax.
 * @author Giacomo Trapani.
 */
public interface UserStorage
{
	/**
	 * Recovers the set of the usernames of the users the given user is currently followed by.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return the usernames of every user currently following given user.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> recoverFollowers(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Converts a user to a stringified user.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return Stringified user.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public String usernameToUserString(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Updates the rewards of each and every user specified thus adding both author and curator gains.
	 * @param gains cannot be null, must contain only registered usernames.
	 * @param authorPercentage must be in range ]0; 100[.
	 * @throws InvalidAmountException if any amount specified is not greater than zero.
	 * @throws NoSuchUserException if there exists at least a user specified in the map which is not registered yet.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void updateRewards(final Map<String, GainAndCurators> gains, final double authorPercentage)
	throws InvalidAmountException, NoSuchUserException, NullPointerException;

	/**
	 * Handles the setup needed for a user to login i.e. it recovers the user's salt encoded.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return the decoded salt to use when hashing the password during the login procedure.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Handles login.
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
	 * Handles logout.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @param clientID cannot be null.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws InvalidLogoutException if this client had yet to login with given username.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException, NullPointerException;

	/**
	 * Handles list users.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return a set of stringified users sharing at least a common tag with the one specified.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Handles list following.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return a set of stringified users currently following the one specified.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> handleListFollowing(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Handles follow user.
	 * @param followerUsername cannot be null, must belong to WINSOME registered users' set.
	 * @param followedUsername cannot be null, must belong to WINSOME registered users' set.
	 * @return true on success, false on failure.
	 * @throws SameUserException if follower and followed are the same user.
	 * @throws NoSuchUserException if at least one of the usernames specified does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws SameUserException, NoSuchUserException, NullPointerException;

	/**
	 * Handles unfollow user.
	 * @param followerUsername cannot be null, must belong to WINSOME registered users' set.
	 * @param followedUsername cannot be null, must belong to WINSOME registered users' set.
	 * @return true on success, false on failure.
	 * @throws NoSuchUserException if at least one of the usernames specified does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public boolean handleUnfollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Handles get wallet.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return the history of each and every transaction involving given user written in JSON syntax and
	 * the total amount of WINCOINS currently held by them.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public Set<String> handleGetWallet(final String username)
	throws NoSuchUserException, NullPointerException;

	/**
	 * Handles get wallet btc.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @return the amount of BTC the WINCOINS owned by given user could be exchanged for right now.
	 * @throws IOException if an I/O error occurs while retrieving current WINCOINS to BTC exchange rate.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if username is null.
	 */
	public String handleGetWalletInBitcoin(final String username)
	throws IOException, NoSuchUserException, NullPointerException;

	/**
	 * Backs up WINSOME registered users' set according to JSON syntax.
	 * @param usersImmutableDataFile cannot be null.
	 * @param followingFile cannot be null.
	 * @param transactionsFile cannot be null.
	 * @throws FileNotFoundException if any file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void backupUsers(final File usersImmutableDataFile, final File followingFile, final File transactionsFile)
	throws FileNotFoundException, IOException, NullPointerException;
}