package server.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.stream.JsonReader;

import server.post.InvalidCommentException;
import server.post.InvalidGeneratorException;
import server.post.InvalidPostException;
import server.post.InvalidVoteException;
import server.post.Post;
import server.post.RewinPost;
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
	private Map<String, Set<Integer>> postsByAuthor = null;

	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_PARAM_ERROR = " cannot be null.";
	/** Part of the exception message when no posts could be found for a certain id. */
	private static final String INVALID_ID_ERROR = "There are no posts with id ";

	public PostMap()
	{
		if (!Post.isIDGenerated())
		try { Post.generateID(); }
		catch (InvalidGeneratorException concurrentCreation) { throw new ConcurrentModificationException(concurrentCreation); }
		postsBackedUp = new ConcurrentHashMap<>();
		postsToBeBackedUp = new ConcurrentHashMap<>();
		postsByAuthor = new ConcurrentHashMap<>();
		flag = false;
	}

	public PostMap(int value)
	throws InvalidGeneratorException
	{
		Post.generateID(value);
		postsBackedUp = new ConcurrentHashMap<>();
		postsToBeBackedUp = new ConcurrentHashMap<>();
		postsByAuthor = new ConcurrentHashMap<>();
		flag = false;
	}

	public int handleCreatePost(final String author, final String title, final String contents)
	throws InvalidPostException, InvalidGeneratorException, NullPointerException
	{
		Post p = null;
		int postID = -1;
		
		p = new RewinPost(Objects.requireNonNull(author, "Author" + NULL_PARAM_ERROR),
				Objects.requireNonNull(title, "Title" + NULL_PARAM_ERROR),
				Objects.requireNonNull(contents, "Contents" + NULL_PARAM_ERROR));

		postID = p.getID();
		postsToBeBackedUp.put(postID, p);
		if (postsByAuthor.get(author) == null)
			postsByAuthor.put(author, new HashSet<>());
		postsByAuthor.get(author).add(postID);
		return postID;
	}

	public Set<String> handleBlog(final String author)
	throws NullPointerException
	{
		Objects.requireNonNull(author, "Author" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		Set<Integer> postsIDs = null;

		postsIDs = postsByAuthor.get(author);
		if (postsIDs == null) return r;
		postsIDs.forEach(id ->
		{
			Post p = postsBackedUp.get(id);
			if (p == null) p = postsToBeBackedUp.get(id);
			if (p == null) throw new IllegalStateException("Posts' storage is not in a consistent state."); // should never happen
			r.add(p.toString());
		});

		return r;
	}

	public Set<String> handleShowFeed(final String username, final UserStorage users)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();

		users.handleListFollowing(username)
			.stream()
			.map(s -> s.split("\r\n")[0]). // discard tags
			collect(Collectors.toSet())
			.forEach(followingUsername ->
				postsByAuthor.get(followingUsername)
				.stream()
				.map(id -> postsBackedUp.get(id))
				.forEach(post -> r.add(post.getID() + "\r\n" + post.getAuthor() + "\r\n" + post.getTitle()))
			);

		return r;
	}

	public String handleShowPost(final int id)
	throws NoSuchPostException
	{
		Post p = null;

		p = postsBackedUp.get(id);
		if (p == null) p = postsToBeBackedUp.get(id);
		if (p == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

		return p.getTitle() + "\r\n" + p.getContents() + "\r\n" + p.getUpvotesNo() + "\r\n" + p.getDownvotesNo() + "\r\n" + String.join("\r\n", p.getComments());
	}

	public boolean handleDeletePost(final String username, final int id)
	throws NoSuchPostException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		Post p = null;

		p = postsBackedUp.get(id);
		if (p == null) p = postsToBeBackedUp.get(id);
		if (p == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

		if (username.equals(p.getAuthor()))
		{
			postsToBeBackedUp.remove(p.getID());
			postsBackedUp.remove(p.getID());
			postsByAuthor.get(p.getAuthor()).remove(p.getID());
			return true;
		}
		else return false;
	}

	public boolean handleRewin(final String username, final UserStorage users, final int id)
	throws NoSuchPostException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);

		Post p = null;

		p = postsBackedUp.get(id);
		if (p == null) p = postsToBeBackedUp.get(id);
		if (p == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

		if (feedContainsPost(username, users, p))
		{
			if (p.addRewin(username))
			{
				if (postsByAuthor.get(username) == null)
					postsByAuthor.put(username, new HashSet<>());
				postsByAuthor.get(username).add(id);
				return true;
			}
		}
		return false;
	}

	public void handleRate(final String username, final UserStorage users, final int id, final Vote vote)
	throws NoSuchPostException, InvalidVoteException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);

		Post p = null;

		p = postsBackedUp.get(id);
		if (p == null) p = postsToBeBackedUp.get(id);
		if (p == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

		if (feedContainsPost(username, users, p))
			p.addVote(username, vote);
		else throw new InvalidVoteException("Post does not belong to specified user's feed.");
	}

	public void handleAddComment(final String author, final UserStorage users, final int id, final String contents)
	throws InvalidCommentException, NoSuchPostException, NullPointerException
	{
		Objects.requireNonNull(author, "Author" + NULL_PARAM_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_PARAM_ERROR);
		Objects.requireNonNull(contents, "Comment's contents" + NULL_PARAM_ERROR);

		Post p = null;

		p = postsBackedUp.get(id);
		if (p == null) p = postsToBeBackedUp.get(id);
		if (p == null) throw new NoSuchPostException(INVALID_ID_ERROR + id);

		if (feedContainsPost(author, users, p))
		{
			p.addComment(author, contents);
			return;
		}
		else throw new InvalidCommentException("Post does not belong to specified user's feed.");
	}

	public void backupPostsImmutableData(final File backupPostsFile)
	throws FileNotFoundException, IOException, NullPointerException
	{
		Map<Integer, Post> tmp = new HashMap<>(postsToBeBackedUp);
		postsToBeBackedUp = new ConcurrentHashMap<>();
		backupCached(new ExclusionStrategy()
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
		}, backupPostsFile, postsBackedUp, tmp, flag);
		flag = false;
	}

	public void backupPostsMutableData(final File backupPostsMetadataFile)
	throws FileNotFoundException, IOException, NullPointerException
	{
		backupNonCached(new ExclusionStrategy()
		{
			public boolean shouldSkipField(FieldAttributes f)
			{
				// skips everything but "id", "comments", "rewonBy", "upvotedBy" and "downvotedBy" fields specified inside RewinPost class.
				return f.getDeclaringClass() == RewinPost.class && !f.getName().equals("id") && !f.getName().equals("comments")
						&& !f.getName().equals("rewonBy") && !f.getName().equals("upvotedBy") && !f.getName().equals("downvotedBy");
			}

			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
		}, backupPostsMetadataFile, postsBackedUp);
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
			Post p = null;
			for (int i = 0; i < 3; i++)
			{
				name = reader.nextName();
				switch (name)
				{
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
			try { p = new RewinPost(author, title, contents); }
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
			String author = null;
			String contents = null;
			for (int i = 0; i < 5; i++)
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
								if (name.equals("author"))
									author = reader.nextString();
								else if (name.equals("contents"))
									contents = reader.nextString();
							}
							reader.endObject();
						}
						reader.endArray();
						break;
					
					default:
						reader.skipValue();
						break;
				}
			}
			reader.endObject();

			try { map.handleAddComment(author, users, id, contents); }
			catch (InvalidCommentException | NoSuchPostException e) { throw new IllegalArchiveException(INVALID_STORAGE); }
			catch (NullPointerException noComments) { }
		}
		reader.endArray();
		reader.close();
		is.close();

		return map;
	}

	private boolean feedContainsPost(final String username, final UserStorage users, final Post p)
	{
		Set<Post> feed = new HashSet<>();
		try
		{
			users.handleListFollowing(username)
			.stream()
			.map(s -> s.split("\r\n")[0]). // discard tags
			collect(Collectors.toSet())
			.forEach(followingUsername ->
				postsByAuthor.get(followingUsername)
				.stream()
				.forEach(postID -> feed.add(postsBackedUp.get(postID))));
		}
		catch (NoSuchUserException | NullPointerException e) { return false; }
		return feed.contains(p);
	}
}
