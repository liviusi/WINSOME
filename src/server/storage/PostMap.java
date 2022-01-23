package server.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import server.post.InvalidCommentException;
import server.post.InvalidGeneratorException;
import server.post.InvalidPostException;
import server.post.InvalidVoteException;
import server.post.Post;
import server.post.RewinPost;
import server.post.Post.GainAndCurators;
import server.post.Post.Vote;

/**
 * @brief Post storage backed by a hashmap. This class is thread-safe.
 * @author Giacomo Trapani
 */
public class PostMap extends Storage implements PostStorage
{
	/** Posts already stored inside backup file. */
	private Map<Integer, Post> postsBackedUp = null;
	/** Posts yet to be stored. */
	private Map<Integer, Post> postsToBeBackedUp = null;
	/** Toggled on if this is the first backup and the storage has been recovered from a JSON file. */
	private boolean flag = false;
	/** Toggled on if a post has been deleted since the last backup. */
	private boolean flush = false;
	private Map<String, Set<Integer>> postsByAuthor = null;

	private ReentrantReadWriteLock backupLock = new ReentrantReadWriteLock(true);
	private ReentrantReadWriteLock dataAccessLock = new ReentrantReadWriteLock(true);

	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_PARAM_ERROR = " cannot be null.";
	/** Part of the exception message when no posts could be found for a certain id. */
	private static final String INVALID_ID_ERROR = "There are no posts with id ";

	public PostMap()
	{
		if (!Post.isIDGenerated())
		try { Post.generateID(); }
		catch (InvalidGeneratorException concurrentCreation) { throw new ConcurrentModificationException(concurrentCreation); }
		postsBackedUp = new HashMap<>();
		postsToBeBackedUp = new HashMap<>();
		postsByAuthor = new HashMap<>();
		flag = false;
	}

	public PostMap(int value)
	throws InvalidGeneratorException
	{
		Post.generateID(value);
		postsBackedUp = new HashMap<>();
		postsToBeBackedUp = new HashMap<>();
		postsByAuthor = new HashMap<>();
		flag = false;
	}

	public Map<String, GainAndCurators> calculateGains()
	{
		Map<String, GainAndCurators> map = new HashMap<>();

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				for (Post p: postsBackedUp.values())
				map.put(p.getAuthor(), p.getGainAndCurators());
				for (Post p: postsToBeBackedUp.values())
				map.put(p.getAuthor(), p.getGainAndCurators());
				return map;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public int handleCreatePost(final String author, final String title, final String contents)
	throws InvalidPostException, InvalidGeneratorException, NullPointerException
	{
		Post p = null;
		int postID = -1;
		
		p = new RewinPost(Objects.requireNonNull(author, "Author" + NULL_PARAM_ERROR),
				Objects.requireNonNull(title, "Title" + NULL_PARAM_ERROR),
				Objects.requireNonNull(contents, "Contents" + NULL_PARAM_ERROR));
		Set<Integer> tmp = new HashSet<>();

		postID = p.getID();
		tmp.add(postID);

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.writeLock().lock();
				postsToBeBackedUp.put(postID, p);
				postsByAuthor.compute(author, (k, v) -> v == null ? tmp : Stream.concat(tmp.stream(), v.stream()).collect(Collectors.toSet()));
				return postID;
			}
			finally { dataAccessLock.writeLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public Set<String> handleBlog(final String author)
	throws NullPointerException
	{
		Objects.requireNonNull(author, "Author" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		Set<Integer> postsIDs = null;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				postsIDs = postsByAuthor.get(author);
				if (postsIDs == null) return r;
				postsIDs.forEach(id ->
				{
					final Post p;
					if ((p = getPostByID(id)) == null) throw new IllegalStateException("Posts' storage is not in a consistent state."); // should never happen
					r.add(postToPreview(p));
				});

				return r;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public Set<String> handleShowFeed(final String username, final UserStorage users)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				users.handleListFollowing(username)
					.stream()
					.map(s -> new Gson().fromJson(s, JsonObject.class).get("username").getAsString())
					.forEach(followingUsername ->
						Optional.ofNullable(postsByAuthor.get(followingUsername)).orElseGet(HashSet<Integer>::new)
						.forEach(id -> r.add(postToPreview(getPostByID(id))))
					);
				return r;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public String handleShowPost(final int id)
	throws NoSuchPostException
	{
		final Post p;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((p = getPostByID(id)) == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);
				return postToShow(p);
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public boolean handleDeletePost(final String username, final int id)
	throws NoSuchPostException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		final Post p;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.writeLock().lock();
				if ((p = getPostByID(id)) == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

				if (username.equals(p.getAuthor()))
				{
					postsToBeBackedUp.remove(p.getID());
					postsBackedUp.remove(p.getID());
					postsByAuthor.get(p.getAuthor()).remove(p.getID());
					flush = true;
					return true;
				}
				return false;
			}
			finally { dataAccessLock.writeLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public boolean handleRewin(final String username, final UserStorage users, final int id)
	throws NoSuchPostException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);

		final Post p;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.writeLock().lock();
				if ((p = getPostByID(id)) == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);
				if (feedContainsPost(username, users, p))
				{
					if (p.addRewin(username))
					{
						Set<Integer> tmp = new HashSet<>(); tmp.add(id);
						postsByAuthor.compute(username, (k, v) -> v == null ? tmp : Stream.concat(v.stream(), tmp.stream()).collect(Collectors.toSet()));
						return true;
					}
				}
				return false;
			}
			finally { dataAccessLock.writeLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public void handleRate(final String username, final UserStorage users, final int id, final Vote vote)
	throws NoSuchPostException, InvalidVoteException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);

		final Post p;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((p = getPostByID(id)) == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

				if (feedContainsPost(username, users, p)) p.addVote(username, vote);
				else throw new InvalidVoteException("Post does not belong to specified user's feed.");
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public void handleAddComment(final String author, final UserStorage users, final int id, final String contents)
	throws InvalidCommentException, NoSuchPostException, NullPointerException
	{
		Objects.requireNonNull(author, "Author" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);
		Objects.requireNonNull(contents, "Comment's contents" + NULL_PARAM_ERROR);

		final Post p;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((p = getPostByID(id)) == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);
				if (feedContainsPost(author, users, p))
				{
					p.addComment(author, contents);
					return;
				}
				else throw new InvalidCommentException("Post does not belong to specified user's feed.");
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public void backupPosts(final File backupPostsImmutableDataFile, final File backupPostsMutableDataFile)
	throws FileNotFoundException, IOException, NullPointerException
	{
		Objects.requireNonNull(backupPostsImmutableDataFile, "File" + NULL_PARAM_ERROR);
		Objects.requireNonNull(backupPostsMutableDataFile, "File" + NULL_PARAM_ERROR);

		ExclusionStrategy strat = null;

		strat = new ExclusionStrategy()
		{
			public boolean shouldSkipField(FieldAttributes f)
			{
				// skips "comments", "rewonBy", "upvotedBy" and "downvotedBy" fields specified inside RewinPost class.
				return f.getDeclaringClass() == RewinPost.class && !f.getName().equals("id") && !f.getName().equals("author") &&
					!f.getName().equals("title") && !f.getName().equals("contents");
			}

			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
		};

		try
		{
			backupLock.writeLock().lock();
			if (flush)
			{
				flush = false;
				backupNonCached(strat, backupPostsImmutableDataFile, postsBackedUp);
			}
			backupCached(strat, backupPostsImmutableDataFile, postsBackedUp, postsToBeBackedUp, flag);
			postsToBeBackedUp = new HashMap<>();
			flag = false;

			backupNonCached(new ExclusionStrategy()
			{
				public boolean shouldSkipField(FieldAttributes f)
				{
					// skips everything but "id", "comments", "rewonBy", "upvotedBy", "downvotedBy", "iterations", "newVotes", "newCommentsBy"
					// and "newCurators" fields specified inside RewinPost class.
					return f.getDeclaringClass() == RewinPost.class && !f.getName().equals("id") && !f.getName().equals("comments")
							&& !f.getName().equals("rewonBy") && !f.getName().equals("upvotedBy") && !f.getName().equals("downvotedBy")
							&& !f.getName().equals("iterations")  && !f.getName().equals("newVotes")
							&& !f.getName().equals("newCommentsBy") && !f.getName().equals("newCurators");
				}

				public boolean shouldSkipClass(Class<?> clazz)
				{
					return false;
				}
			}, backupPostsMutableDataFile, postsBackedUp);
		}
		finally { backupLock.writeLock().unlock(); }
	}

	public static PostMap fromJSON(final File backupPostsFile, final File backupPostsMetadataFile, final UserStorage users)
	throws FileNotFoundException, IOException, IllegalArchiveException, InvalidGeneratorException
	{
		final String INVALID_STORAGE = "The files to be parsed are not a valid storage.";

		PostMap map = new PostMap();
		map.flag = true;

		InputStream is = new FileInputStream(backupPostsFile);
		JsonReader reader = new JsonReader(new InputStreamReader(is));

		reader.setLenient(true);
		reader.beginArray();
		while (reader.hasNext())
		{
			reader.beginObject();
			String name = null;
			String author = null;
			String title = null;
			String contents = null;
			int id = -1;
			Post p = null;
			for (int i = 0; i < 4; i++)
			{
				name = reader.nextName();
				switch (name)
				{
					case "id":
						id = Integer.parseInt(reader.nextString());
						break;

					case "author":
						author = reader.nextString();
						break;

					case "title":
						title = reader.nextString();
						break;

					case "contents":
						contents = reader.nextString();
						break;

					default:
						reader.skipValue();
						break;
				}
			}
			reader.endObject();
			try
			{
				p = new RewinPost(author, title, contents);
				if (id != p.getID()) throw new IllegalArchiveException("Archive is not in a consistent state.");
			}
			catch (InvalidPostException | InvalidGeneratorException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
			map.postsBackedUp.put(p.getID(), p);
			if (map.postsByAuthor.get(author) == null)
				map.postsByAuthor.put(author, new HashSet<>());
			map.postsByAuthor.get(author).add(p.getID());
		}
		reader.endArray();
		reader.close();
		is.close();

		is = new FileInputStream(backupPostsMetadataFile);
		reader = new JsonReader(new InputStreamReader(is));


		reader.setLenient(true);
		reader.beginArray();
		while (reader.hasNext())
		{
			reader.beginObject();
			String name = null;
			int id = -1;
			List<String> rewinners = new ArrayList<>();
			List<String> upvoters = new ArrayList<>();
			List<String> downvoters = new ArrayList<>();
			List<String> commentAuthors = new ArrayList<>();
			List<String> commentContents = new ArrayList<>();
			for (int i = 0; i < 9; i++)
			{
				name = reader.nextName();
				switch (name)
				{
					case "id":
						id = reader.nextInt();
						break;

					case "comments":
						reader.beginArray();
						while (reader.hasNext())
						{
							reader.beginObject();
							for (int j = 0; j < 2; j++)
							{
								name = reader.nextName();
								if (name.equals("author")) commentAuthors.add(reader.nextString());
								else if (name.equals("contents")) commentContents.add(reader.nextString());
							}
							reader.endObject();
						}
						reader.endArray();
						break;
					
					case "rewonBy":
						reader.beginArray();
						while (reader.hasNext())
							rewinners.add(reader.nextString());
						reader.endArray();
						break;
					
					case "upvotedBy":
						reader.beginArray();
						while (reader.hasNext())
							upvoters.add(reader.nextString());
						reader.endArray();
						break;
					
					case "downvotedBy":
						reader.beginArray();
						while (reader.hasNext())
							downvoters.add(reader.nextString());
						reader.endArray();
						break;
					
					default:
						reader.skipValue();
						break;
				}
			}
			reader.endObject();

			if (commentAuthors.size() != commentContents.size()) throw new IllegalArchiveException(INVALID_STORAGE);
			for (int i = 0; i < commentAuthors.size(); i++)
			{
				try { map.handleAddComment(commentAuthors.get(i), users, id, commentContents.get(i)); }
				catch (InvalidCommentException | NoSuchPostException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
			}
			for (String r : rewinners)
			{
				try { map.handleRewin(r, users, id); }
				catch (NoSuchPostException e) { throw new IllegalArchiveException(INVALID_STORAGE); }
			}
			for (String r : upvoters)
			{
				try { map.handleRate(r, users, id, Vote.UPVOTE); }
				catch (NoSuchPostException | InvalidVoteException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
			}
			for (String r : downvoters)
			{
				try { map.handleRate(r, users, id, Vote.DOWNVOTE); }
				catch (NoSuchPostException | InvalidVoteException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
			}
		}
		reader.endArray();
		reader.close();
		is.close();

		return map;
	}

	private boolean feedContainsPost(final String username, final UserStorage users, final Post p)
	{
		Integer ID = p.getID();
		boolean result = false;
		try
		{
			Iterator<Set<Integer>> it = users.handleListFollowing(username)
				.stream()
				.map(s -> new Gson().fromJson(s, JsonObject.class).get("username").getAsString())
				.map(followingUsername -> Optional.ofNullable(postsByAuthor.get(followingUsername)).orElseGet(HashSet<Integer>::new))
				.iterator();
			while (it.hasNext() && !result)
			{
				Set<Integer> tmp = it.next();
				for (Integer i : tmp)
				{
					if (result) break;
					result = i.equals(ID);
				}
			}
		}
		catch (NoSuchUserException | NullPointerException e) { return false; }
		return result;
	}

	private static String postToPreview(final Post p)
	{
		return String.format("{ \"%s\": \"%d\",\n \"%s\": \"%s\",\n \"%s\": \"%s\"}", "id", p.getID(), "author", p.getAuthor(), "title", p.getTitle());
	}

	private static String postToShow(final Post p)
	{
		return String.format("{ \"%s\": \"%d\",\n\"%s\": \"%s\",\n\"%s\": \"%s\",\n\"%s\": \"%d\",\n\"%s\": \"%d\",\n\"%s\": [%s],\n\"%s\": [%s]}",
				"id", p.getID(), "title", p.getTitle(), "contents", p.getContents(), "upvotes", p.getUpvotesNo(), "downvotes", p.getDownvotesNo(),
				"rewonBy", String.join(", ", p.getRewinnersNames()), "comments", String.join(", ", p.getComments()));
	}

	private Post getPostByID(final int id)
	{
		Post p = postsBackedUp.get(id);
		if (p == null) p = postsToBeBackedUp.get(id);
		return p;
	}
}
