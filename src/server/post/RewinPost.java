package server.post;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @brief Class used to represent a Post in WINSOME.
 * @author Giacomo Trapani
 */
public class RewinPost extends Post
{
	/** Container class for comments. */
	private static class Comment
	{
		/** Name of this comment's author. */
		public final String author;
		/** Contents of this comment. */
		public final String contents;

		/**
		 * @brief Default constructor for a comment.
		 * @param author cannot be null or empty.
		 * @param contents cannot be null or empty.
		 * @throws InvalidCommentException if any parameter is empty.
		 * @throws NullPointerException if any parameter is null.
		 */
		private Comment(String author, String contents)
		throws InvalidCommentException, NullPointerException
		{
			if (Objects.requireNonNull(author, "Author" + NULL_ERROR).isEmpty())
				throw new InvalidCommentException("Author" + EMPTY_ERROR);
			if (Objects.requireNonNull(contents, "Contents" + NULL_ERROR).isEmpty())
				throw new InvalidCommentException("Contents" + EMPTY_ERROR);
			
			this.author = author;
			this.contents = contents;
		}
	}

	/** Unique identifier. */
	private int id = -1;
	/** Name of the author of this post. */
	private String author = null;
	/** Title of this post. */
	private String title = null;
	/** Contents of this post. */
	private String contents = null;
	/** Set of the names of the users whom have rewon this post. */
	private Set<String> rewonBy = null;
	/** Set of the names of the users whom have upvoted this post. */
	private Set<String> upvotedBy = null;
	/** Set of the names of the users whom have downvoted this post. */
	private Set<String> downvotedBy = null;
	/** Queue of the comments this post has received. */
	private Collection<Comment> comments = null;

	/** Part of the exception message when an attempt is made to submit a post with an empty title. */
	private static final String EMPTY_ERROR = " cannot be empty.";
	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";

	public RewinPost(final String author, final String title, final String contents)
	throws InvalidPostException, InvalidGeneratorException, NullPointerException
	{
		if (Objects.requireNonNull(author, "Author" + NULL_ERROR).isEmpty())
			throw new InvalidPostException("Author" + EMPTY_ERROR);
		if (Objects.requireNonNull(title, "Title" + NULL_ERROR).isEmpty())
			throw new InvalidPostException("Title" + EMPTY_ERROR);
		if (Objects.requireNonNull(contents, "Contents" + NULL_ERROR).isEmpty())
			throw new InvalidPostException("Contents" + EMPTY_ERROR);

		if (title.length() > 20)
			throw new InvalidPostException("Title cannot be longer than 20 characters.");
		if (contents.length() > 500)
			throw new InvalidPostException("Contents cannot be longer than 500 characters.");
		if (author.contains("\r\n"))
			throw new InvalidPostException("CRLF cannot be inside an author's name.");
		if (title.contains("\r\n"))
			throw new InvalidPostException("CRLF cannot be inside a post's title");
		if (contents.contains("\r\n"))
			throw new InvalidPostException("CRLF cannot be inside a post's contents.");

		this.id = getNextID();
		this.author = author;
		this.title = title;
		this.contents = contents;
		this.rewonBy = ConcurrentHashMap.newKeySet();
		this.comments = new ConcurrentLinkedQueue<Comment>();
		this.upvotedBy = ConcurrentHashMap.newKeySet();
		this.downvotedBy = ConcurrentHashMap.newKeySet();
	}

	public int getID()
	{
		return id;
	}

	public String getAuthor()
	{
		return author;
	}

	public String getTitle()
	{
		return title;
	}

	public String getContents()
	{
		return contents;
	}

	public Set<String> getRewinnersNames()
	{
		return new HashSet<>(rewonBy);
	}

	public List<String> getComments()
	{
		List<String> res = new ArrayList<>();
		comments.forEach(c -> res.add(c.author + "\r\n" + c.contents));
		return res;
	}

	public int getUpvotesNo()
	{
		return upvotedBy.size();
	}

	public int getDownvotesNo()
	{
		return downvotedBy.size();
	}

	public boolean addRewin(final String username)
	throws NullPointerException
	{
		return rewonBy.add(Objects.requireNonNull(username, "User" + NULL_ERROR));
	}

	public void addComment(final String username, final String contents)
	throws InvalidCommentException, NullPointerException
	{
		if (Objects.requireNonNull(username, "Username" + NULL_ERROR).equals(author))
			throw new InvalidCommentException("A user cannot comment their own posts.");
		if (Objects.requireNonNull(contents, "Contents" + NULL_ERROR).isEmpty())
			throw new InvalidCommentException("Comment contents" + EMPTY_ERROR);

		if (contents.contains("\r\n")) throw new InvalidCommentException("Comment contents cannot contain CRLF.");
		
		comments.add(new Comment(username, contents));
	}

	public void addVote(final String username, final Vote vote)
	throws InvalidVoteException, NullPointerException
	{
		Objects.requireNonNull(vote, "Vote" + NULL_ERROR);
		if (Objects.requireNonNull(username, "Username" + NULL_ERROR).equals(author))
			throw new InvalidVoteException("A user cannot vote their own posts.");
		if (upvotedBy.contains(username) || downvotedBy.contains(username))
			throw new InvalidVoteException("A user can cast their vote only once.");

		if (vote.equals(Vote.UPVOTE))
			upvotedBy.add(username);
		else if (vote.equals(Vote.DOWNVOTE))
			downvotedBy.add(username);

		return;
	}

	public boolean equals(Object o)
	{
		return o instanceof RewinPost && this.id == ((RewinPost) o).id;
	}

	public int hashCode()
	{
		return Integer.valueOf(id).hashCode();
	}

	public String toString()
	{
		return id + "\r\n" + author + "\r\n" + title + "\r\n" + contents;
	}
}
