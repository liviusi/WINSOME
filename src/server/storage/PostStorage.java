package server.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import server.post.InvalidCommentException;
import server.post.InvalidGeneratorException;
import server.post.InvalidPostException;
import server.post.InvalidVoteException;
import server.post.Post.Vote;

/**
 * @brief Interface to be implemented by an actual post storage class.
 * @author Giacomo Trapani
 */
public interface PostStorage
{
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
	 * @return the posts written or rewon by author as a set of strings following the format CONCAT(ID, "\r\n", AUTHOR, "\r\n", TITLE, "\r\n",
	 * CONTENTS) with AUTHOR the very author of the post, TITLE its title, CONTENT its content.
	 * @throws NullPointerException if author is null.
	 */
	public Set<String> handleBlog(final String author)
	throws NoSuchPostException, NullPointerException;

	/**
	 * @brief Handles show feed command.
	 * @param username cannot be null, must belong to WINSOME registered users' set.
	 * @param users cannot be null.
	 * @return the posts written or rewon by the users username is currently following as a set of strings following
	 * the format CONCAT(ID, "\r\n", AUTHOR, "\r\n", TITLE, "\r\n", CONTENTS) with AUTHOR the very author of the post,
	 * TITLE its title, CONTENT its content.
	 * @throws NoSuchUserException if username does not belong to WINSOME registered users' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public Set<String> handleShowFeed(final String username, final UserStorage users)
	throws NoSuchUserException, NullPointerException;

	/**
	 * @brief Handles show post command.
	 * @param id must belong to the set of registered posts' ids.
	 * @return a post written following the format CONCAT(TITLE, "\r\n", CONTENTS, "\r\n", UPVOTES_NO, "\r\n", DOWNVOTES_NO, "\r\n",
	 * COMMENT_{1}, "\r\n", ..., "\r\n", {COMMENT_n}) with TITLE the title of the post, CONTENTS its contents, UPVOTES_NO the number
	 * of upvotes it has received, DOWNVOTES_NO the number of downvotes, COMMENT_{i} the i-th comment the post has received;
	 * a comment follows this format: CONCAT(AUTHOR, "\r\n", CONTENTS) with AUTHOR its author and CONTENTS its contents.
	 * @throws NoSuchPostException if id does not belong to WINSOME registered posts' set.
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
	 * @param contents cannot be null or contain "\r\n".
	 * @throws InvalidCommentException if an attempt is made to commit a comment from the author of given post or
	 * the post does not show up in author's feed.
	 * @throws NoSuchPostException if id does not belong to WINSOME registered posts' set.
	 * @throws NullPointerException if any parameter is null.
	 */
	public void handleAddComment(final String author, final UserStorage users, final int id, final String contents)
	throws InvalidCommentException, NoSuchPostException, NullPointerException;

	/**
	 * @brief Backs up WINSOME users' posts according to json syntax.
	 * @param file the file to store the list in (will be overwritten).
	 * @throws FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws NullPointerException if file is null.
	 */
	public void backupPostsImmutableData(final File backupPostsFile)
	throws FileNotFoundException, IOException, NullPointerException;

	/**
	 * @brief Backs up WINSOME users' posts according to json syntax.
	 * @param file the file to store the list in (will be overwritten).
	 * @throws FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws NullPointerException if file is null.
	 */
	public void backupPostsMutableData(final File backupPostsMetadataFile)
	throws FileNotFoundException, IOException, NullPointerException;
}
