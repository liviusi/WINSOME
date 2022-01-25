package server.post;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract class defining a post in WINSOME.
 * @author Giacomo Trapani.
 */
public abstract class Post
{
	/** Enum used to denote either an upvote or a downvote to a certain post. */
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
		 * Instantiates a vote given its numerical value.
		 * @param value must belong to { -1, 1 }.
		 * @return instantiated vote on success, null otherwise.
		 */
		public static Vote fromValue(int value)
		throws InvalidVoteException
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

				default:
					throw new InvalidVoteException("Value specified does not correspond to a valid vote.");
			}
			return v;
		}
	}

	/**
	 * Container class for the couple (gain, curators). A user x is defined a curator of another user y if and only if it
	 * has either commented or upvoted a post by x recently.
	*/
	public static class GainAndCurators
	{
		/** Amount of WINCOINS involved. */
		public final double gain;
		/** Usernames of each and every curator. */
		private final Set<String> curators;

		/** Default constructor. */
		public GainAndCurators(double gain, Set<String> curators)
		{
			this.curators = curators;
			this.gain = gain;
		}

		/** Retrieves the set of curators. */
		public Set<String> getCurators()
		{
			Set<String> r = new HashSet<>();
			r.addAll(curators);
			return r;
		}
	}

	/** Generator for new posts' IDs. */
	private static AtomicInteger generatorID = null;

	/** Returns true if and only if the generator has been properly initialized. */
	public static boolean isIDGenerated()
	{
		return generatorID != null;
	}

	/**
	 * Instantiates generator.
	 * @throws InvalidGeneratorException if ID has already been generated.
	*/
	public static void generateID()
	throws InvalidGeneratorException
	{
		if (generatorID != null)
			throw new InvalidGeneratorException("ID has already been generated.");
		generatorID = new AtomicInteger(0);
	}

	/**
	 * Instantiates generator given an initial value.
	 * @throws InvalidGeneratorException if ID has already been generated.
	*/
	public static void generateID(final int value)
	throws InvalidGeneratorException
	{
		if (generatorID != null)
			throw new InvalidGeneratorException("ID has already been generated");
		generatorID = new AtomicInteger(value);
	}

	/**
	 * Getter for next valid ID. This method is thread-safe.
	 * @throws InvalidGeneratorException if ID has yet to be generated.
	*/
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

	/** Getter for this post's gain and curators as a couple. */
	public abstract GainAndCurators getGainAndCurators();

	/**
	 * Adds a rewin from given user to this post. This method is thread-safe.
	 * @param username cannot be null.
	 * @return true on success, false on failure.
	 * @throws NullPointerException if u is null.
	 */
	public abstract boolean addRewin(final String username)
	throws NullPointerException;

	/**
	 * Adds a comment with given content from given user to this post.
	 * @param username cannot be null or this post's author.
	 * @param contents cannot be null or empty.
	 * @throws InvalidCommentException if either this post's author and given user are the same or
	 * contents are empty.
	 * @throws NullPointerException if any parameter is null.
	 */
	public abstract void addComment(final String username, final String contents)
	throws InvalidCommentException, NullPointerException;

	/**
	 * Adds either an upvote or a downvote to this post.
	 * @param username cannot be null or this post's author or a user who's already cast a vote for this post.
	 * @param vote cannot be null.
	 * @throws InvalidVoteException if given user is this post's author or has already cast a vote for this post.
	 * @throws NullPointerException if any parameter is null.
	 */
	public abstract void addVote(final String username, final Vote vote)
	throws InvalidVoteException, NullPointerException;

}
