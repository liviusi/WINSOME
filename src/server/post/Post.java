package server.post;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @brief Abstract class defining a post in WINSOME.
 * @author Giacomo Trapani
 */
public abstract class Post
{
	/** Enum used to upvote and downvote a certain post. */
	public static enum Vote
	{
		UPVOTE(1),
		DOWNVOTE(-1);

		final int value;

		private Vote(int value)
		{
			this.value = value;
		}

		/**
		 * @brief Instantiates a vote given its numerical value.
		 * @param value must belong to { -1, 1 }.
		 * @return instantiated vote on success, null otherwise.
		 */
		public static Vote fromValue(int value)
		{
			Vote v = null;
			switch (value)
			{
				case 1:
					v = Vote.UPVOTE;
					break;

				case -1:
					v = Vote.DOWNVOTE;
					break;
			}
			return v;
		}
	}

	/** Generator for new posts' IDs. */
	private static AtomicInteger generatorID = null;

	/** Returns true if and only if the generator has been properly initialized. */
	public boolean isIDGenerated()
	{
		return generatorID == null;
	}

	/** Instantiates generator. */
	public static void generateID()
	throws InvalidGeneratorException
	{
		if (generatorID != null)
			throw new InvalidGeneratorException("ID has already been generated.");
		generatorID = new AtomicInteger(0);
	}

	/** Instantiates generator given an initial value. */
	public static void generateID(final int value)
	throws InvalidGeneratorException
	{
		if (generatorID != null)
			throw new InvalidGeneratorException("ID has already been generated");
		generatorID = new AtomicInteger(value);
	}

	/** Getter for next valid ID. This method is thread-safe. */
	public int getNextID()
	throws InvalidGeneratorException
	{
		if (generatorID == null)
			throw new InvalidGeneratorException("ID has yet to be generated.");
		return generatorID.incrementAndGet();
	}

	/** Getter for this post's unique identifier. */
	public abstract int getID();

	/** Getter for this post's author. */
	public abstract String getAuthor();

	/** Getter for this post's title. */
	public abstract String getTitle();

	/** Getter for this post's contents. */
	public abstract String getContents();

	/** Getter for this post's rewinners' names. */
	public abstract Set<String> getRewinnersNames();

	/** Getter for this post's comments. */
	public abstract List<String> getComments();

	/** Getter for this post's number of upvotes. */
	public abstract int getUpvotesNo();

	/** Getter for this post's number of downvotes. */
	public abstract int getDownvotesNo();

	/**
	 * @brief Adds a rewin from given user to this post. This method is thread-safe.
	 * @param username cannot be null.
	 * @return true on success, false on failure.
	 * @throws NullPointerException if u is null.
	 */
	public abstract boolean addRewin(final String username)
	throws NullPointerException;

	/**
	 * @brief Adds a comment with given content from given user to this post.
	 * @param username cannot be null or this post's author.
	 * @param contents cannot be null or empty.
	 * @throws InvalidCommentException if either this post's author and given user are the same or
	 * contents are empty.
	 * @throws NullPointerException if any parameter is null.
	 */
	public abstract void addComment(final String username, final String contents)
	throws InvalidCommentException, NullPointerException;

	/**
	 * @brief Adds either an upvote or a downvote to this post.
	 * @param username cannot be null or this post's author or a user who's already cast a vote for this post.
	 * @param vote cannot be null.
	 * @throws InvalidVoteException if given user is this post's author or has already cast a vote for this post.
	 * @throws NullPointerException if any parameter is null.
	 */
	public abstract void addVote(final String username, final Vote vote)
	throws InvalidVoteException, NullPointerException;

}
