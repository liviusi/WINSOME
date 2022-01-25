package server.post;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class used to represent a Post in WINSOME.
 * @author Giacomo Trapani.
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
		 * Default constructor for a comment.
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

		public String toString()
		{
			return String.format("{ \"%s\": \"%s\", \"%s\": \"%s\" }", "author", author, "contents", contents);
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
	private Set<String> rewonBy = ConcurrentHashMap.newKeySet();
	/** Set of the names of the users whom have upvoted this post. */
	private Set<String> upvotedBy = ConcurrentHashMap.newKeySet();
	/** Set of the names of the users whom have downvoted this post. */
	private Set<String> downvotedBy = ConcurrentHashMap.newKeySet();
	/** Queue of the comments this post has received. */
	private Queue<Comment> comments = new ConcurrentLinkedQueue<Comment>();

	/** Weighted sum of upvotes and downvotes received recently. An upvote is valued 1, a downvote -1. */
	private int newVotes = 0;
	/** Maps a username to the number of new comments by it, with new denoting those received recently. */
	private Map<String, Integer> newCommentsBy = new HashMap<>();
	/** Collects the usernames of each and every user who's now become a curator for this post. */
	private Set<String> newCurators = new HashSet<>();
	/** Number of iterations of the Gain formula this post has been run against. */
	private int iterations = 0;

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

		this.id = getNextID();
		this.author = author;
		this.title = title;
		this.contents = contents;
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
		// recovering the comments this way is legit because comments is a concurrent data structure.
		comments.forEach(c -> res.add(c.toString()));
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

	// synchronized is required to avoid having the post in a non-consistent state.
	public synchronized GainAndCurators getGainAndCurators()
	{
		Objects.requireNonNull("Curators" + NULL_ERROR);

		iterations++;

		double tmp = 0;
		for (Integer cp : newCommentsBy.values()) tmp += (2 / (1 + Math.pow(Math.E, -(cp - 1))));
		Set<String> curators = newCurators;

		// computing gain ratio formula
		double result = (Math.log(Math.max(newVotes, 0) + 1) + Math.log(tmp + 1)) / iterations;

		// resetting params
		newCommentsBy = new HashMap<>();
		newCurators = new HashSet<>();
		newVotes = 0;

		return new GainAndCurators(result, curators);
	}

	public boolean addRewin(final String username)
	throws NullPointerException
	{
		// rewonBy is a concurrent data structure
		return rewonBy.add(Objects.requireNonNull(username, "User" + NULL_ERROR));
	}

	// synchronized is required to avoid having a post in a non-consistent state as multiple data structures are to be modified
	public synchronized void addComment(final String username, final String contents)
	throws InvalidCommentException, NullPointerException
	{
		if (Objects.requireNonNull(username, "Username" + NULL_ERROR).equals(author))
			throw new InvalidCommentException("A user cannot comment their own posts.");
		if (Objects.requireNonNull(contents, "Contents" + NULL_ERROR).isEmpty())
			throw new InvalidCommentException("Comment contents" + EMPTY_ERROR);

		comments.add(new Comment(username, contents));
		newCommentsBy.compute(username, (k, v) -> v == null ? 1 : v + 1);
		newCurators.add(username);
	}

	// synchronized is required to avoid having a post in a non-consistent state as multiple data structures are to be modified
	public synchronized void addVote(final String username, final Vote vote)
	throws InvalidVoteException, NullPointerException
	{
		Objects.requireNonNull(vote, "Vote" + NULL_ERROR);
		if (Objects.requireNonNull(username, "Username" + NULL_ERROR).equals(author))
			throw new InvalidVoteException("A user cannot vote their own posts.");
		if (upvotedBy.contains(username) || downvotedBy.contains(username))
			throw new InvalidVoteException("A user can cast their vote only once.");

		if (vote.equals(Vote.UPVOTE))
		{
			upvotedBy.add(username);
			newVotes++;
			newCurators.add(username);
		}
		else if (vote.equals(Vote.DOWNVOTE))
		{
			downvotedBy.add(username);
			newVotes--;
		}

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
}
