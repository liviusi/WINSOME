package server.post;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
	private Set<String> rewonBy = null;
	/** Set of the names of the users whom have upvoted this post. */
	private Set<String> upvotedBy = null;
	/** Set of the names of the users whom have downvoted this post. */
	private Set<String> downvotedBy = null;
	/** Queue of the comments this post has received. */
	private Collection<Comment> comments = null;

	private AtomicInteger newVotes = new AtomicInteger(0);
	private Map<String, Integer> newCommentsBy = new ConcurrentHashMap<>();
	private Set<String> newCurators = ConcurrentHashMap.newKeySet();
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

	public synchronized GainAndCurators getGainAndCurators()
	{
		Objects.requireNonNull("Curators" + NULL_ERROR);

		iterations++;

		double tmp = 0;
		int newVotesNo = newVotes.get();
		for (Integer cp : newCommentsBy.values()) tmp += (2 / (1 + Math.pow(Math.E, -(cp - 1))));
		Set<String> curators = new HashSet<>(); curators.addAll(newCurators);

		newCommentsBy = new ConcurrentHashMap<>();
		newCurators = ConcurrentHashMap.newKeySet();
		newVotes = new AtomicInteger(0);

		double result = (Math.log(Math.max(newVotesNo, 0) + 1) + Math.log(tmp + 1)) / iterations;

		return new GainAndCurators(result, curators);
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

		comments.add(new Comment(username, contents));
		newCommentsBy.compute(username, (k, v) -> v == null ? 1 : v + 1);
		newCurators.add(username);
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
		{
			upvotedBy.add(username);
			newVotes.incrementAndGet();
			newCurators.add(username);
		}
		else if (vote.equals(Vote.DOWNVOTE))
		{
			downvotedBy.add(username);
			newVotes.decrementAndGet();
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
