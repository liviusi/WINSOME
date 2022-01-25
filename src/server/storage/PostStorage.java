package server.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import server.post.InvalidCommentException;
import server.post.InvalidGeneratorException;
import server.post.InvalidPostException;
import server.post.InvalidVoteException;
import server.post.Post.GainAndCurators;
import server.post.Post.Vote;

/**
 * @brief Interface to be implemented by an actual post storage class.
 * NOTATION: a preview of a Post p is defined as { ID, AUTHOR, TITLE }.
 * @author Giacomo Trapani.
 */
public interface PostStorage
{
	/** Handles the computation of the rewards to be handed out. */
	public Map<String, GainAndCurators> calculateGains();

	/**
	 * @brief Handles post creation.
	 * @param author cannot be null or empty.
	 * @param title cannot be null or empty or longer than 20 characters.
	 * @param contents cannot be null or empty or longer than 500 characters.
	 * @return created post ID on success.
	 * @throws InvalidPostException if author is empty or title is empty or title is longer than 20 characters or contents are empty
	 * or contents are longer than 500 characters.
	 * @throws InvalidGeneratorException if posts' ID generator is not in a valid state.
	 * @throws NullPointerException if any parameter is null.
	 */
	public int handleCreatePost(final String author, final String title, final String contents)
	throws InvalidPostException, InvalidGeneratorException, NullPointerException;

	/**
	 * @brief Handles blog command.
	 * @param author cannot be null.
	 * @return the previews of each and every post written or rewon by author as a set of strings written following JSON syntax.
	 * @throws NullPointerException if author is null.
	 */
	public Set<String> handleBlog(final String author)
	throws NullPointerException;

	/**
	 * @brief Handles show feed command.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @param users cannot be null.
	 * @return the previews of each and every post written or rewon by the users username is currently following as a set of strings following JSON syntax.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public Set<String> handleShowFeed(final String username, final UserStorage users)
	throws NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles show post command.
	 * @param id must belong to the set of registered posts' ids.
	 * @return post with given ID written following JSON syntax.
	 */
	public String handleShowPost(final int id)
	throws NoSuchPostException;

	/**
	 * @brief Handles delete command.
	 * @param username cannot be null.
	 * @param id must belong to the set of registered posts' ids.
	 * @return true on success, false on failure.
	 * @throws NoSuchPostException if id does not belong to WINSOME registered posts' set.
	 * @throws NullPointerException if username is null.
	 */
	public boolean handleDeletePost(final String username, final int id)
	throws NoSuchPostException, NullPointerException;

	/**
	 * @brief Handles rewin command.
	 * @param username cannot be null.
	 * @param id must belong to the set of registered posts' ids.
	 * @return true on success, false on failure.
	 * @throws NoSuchPostException if id does not belong to WINSOME registered posts' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public boolean handleRewin(final String username, final UserStorage users, final int id)
	throws NoSuchPostException, NullPointerException;

	/**
	 * @brief Handles rate command.
	 * @param username cannot be null.
	 * @param users cannot be null.
	 * @param id must belong to the set of registered posts' ids.
	 * @param vote cannot be null.
	 * @throws NoSuchPostException if id does not belong to WINSOME registered posts' set.
	 * @throws InvalidVoteException if an attempt is made to cast a vote more than once or the post does not show up on this user's feed.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void handleRate(final String username, final UserStorage users, final int id, final Vote vote)
	throws NoSuchPostException, InvalidVoteException, NullPointerException;

	/**
	 * @brief Handles add comment command.
	 * @param author cannot be null.
	 * @param users cannot be null.
	 * @param id must belong to the set of registered posts' ids.
	 * @throws InvalidCommentException if an attempt is made to commit a comment from the author of given post or
	 * the post does not show up in author's feed.
	 * @throws NoSuchPostException if id does not belong to WINSOME registered posts' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void handleAddComment(final String author, final UserStorage users, final int id, final String contents)
	throws InvalidCommentException, NoSuchPostException, NullPointerException;

	/**
	 * @brief Backs up posts splitting the data between two files: one will contain their immutable data, the other
	 * the mutable one.
	 * @param backupPostsImmutableDataFile cannot be null.
	 * @param backupPostsMutableDataFile cannot be null.
	 * @throws FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws NullPointerException if file is null.
	 */
	public void backupPosts(final File backupPostsImmutableDataFile, final File backupPostsMutableDataFile)
	throws FileNotFoundException, IOException, NullPointerException;
}
