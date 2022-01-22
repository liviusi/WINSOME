package server.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.stream.JsonReader;

import cryptography.Passwords;
import server.post.Post.GainAndCurators;
import user.*;

/**
 * @brief User storage backed by a hashmap. This class is thread-safe.
 * @author Giacomo Trapani
 */
public class UserMap extends Storage implements UserRMIStorage, UserStorage
{
	/** Users already stored inside backup file. */
	private Map<String, User> usersBackedUp = null; // already backed up
	/** Users yet to be stored. */
	private Map<String, User> usersToBeBackedUp = null;
	/** Toggled on if this is the first backup and the storage has been recovered from a JSON file. */
	private boolean flag = false;
	/** Maps a tag to the set of the users currently interested in it. */
	private Map<Tag, Set<User>> interestsMap = null;
	/** Maps a user to the set of users currently following it. */
	private Map<User, Set<User>> followersMap = null;

	/** Empty string. */
	private static final String EMPTY_STRING = "";
	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_PARAM_ERROR = " cannot be null.";
	/** Part of the exception message when a user cannot be find in the storage. */
	private static final String NOT_SIGNED_UP = " has yet to sign up.";

	/** Default constructor. */
	public UserMap()
	{
		usersBackedUp = new ConcurrentHashMap<>();
		usersToBeBackedUp = new ConcurrentHashMap<>();
		flag = false;
		interestsMap = new ConcurrentHashMap<>();
		followersMap = new ConcurrentHashMap<>();
	}

	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(password, "Password" + NULL_PARAM_ERROR);
		Objects.requireNonNull(tags, "Tags" + NULL_PARAM_ERROR);
		Objects.requireNonNull(salt, "Salt" + NULL_PARAM_ERROR);

		final String EMPTY_ERROR = " cannot be empty.";
		String emptyStringHashed = null;
		final User u;

		if (username.isEmpty()) throw new UsernameNotValidException("Username" + EMPTY_ERROR);
		emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.US_ASCII), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password" + EMPTY_ERROR);
		u = new User(username, password, tags, salt);
		if (usersToBeBackedUp.containsKey(username) || usersBackedUp.containsKey(username)) // username already exists
			throw new UsernameAlreadyExistsException("Username has already been taken.");
		usersToBeBackedUp.put(username, u);
		u.getTags().forEach(t -> 
		{
			Set<User> tmp = new HashSet<>();
			tmp.add(u);
			interestsMap.compute(t, (k, v) -> v == null ? tmp : Stream.concat(tmp.stream(), v.stream()).collect(Collectors.toSet()));
		});
		followersMap.put(u, new HashSet<>());
	}

	public Set<String> recoverFollowers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
		
		return followersMap.get(u)
			.stream()
			.map(follower ->
				follower.toString())
			.collect(Collectors.toSet());
	}

	public void updateRewards(Map<String, GainAndCurators> gains, double authorPercentage)
	throws InvalidAmountException, NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(gains, "Gains" + NULL_PARAM_ERROR);
		if (authorPercentage < 0 || authorPercentage > 100) throw new IllegalArgumentException("Author percentage is not a valid percentage.");

		User u = null;

		for (Entry<String, GainAndCurators> entry: gains.entrySet())
		{
			final String username = entry.getKey();
			final double gain = entry.getValue().gain;
			if (gain == 0) continue;
			final Set<String> curators = entry.getValue().getCurators();

			u = usersBackedUp.get(username);
			if (u == null) u = usersToBeBackedUp.get(username);
			if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);

			u.addTransaction(new Transaction(gain * authorPercentage / 100));
			for (String s : curators)
			{
				u = usersBackedUp.get(s);
				if (u == null) u = usersToBeBackedUp.get(s);
				if (u == null) throw new NoSuchUserException(s + NOT_SIGNED_UP);

				u.addTransaction(new Transaction(gain * (100 - authorPercentage) / 100));
			}
		}

	}

	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		String saltDecoded = null;
		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
		saltDecoded = u.saltDecoded;

		return saltDecoded;
	}

	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_PARAM_ERROR);
		Objects.requireNonNull(hashPassword, "Hashed password" + NULL_PARAM_ERROR);

		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
		u.login(clientID, hashPassword);
	}

	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_PARAM_ERROR);

		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
		u.logout(clientID);
	}

	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
		u.getTags().forEach(t ->
			interestsMap.get(t).forEach(tUser ->
			{
				if (!tUser.username.equals(username))
				{
					r.add(tUser.toString());
				}
			})
		);

		return r;
	}

	public Set<String> handleListFollowing(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		User u = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
		u.getFollowing().forEach(following ->
			{
				try { r.add(usersBackedUp.get(following).toString()); }
				catch (NullPointerException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); } // storage is in an inconsistent state
			}
		);

		return r;
		
	}

	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws SameUserException, NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(followerUsername, "Follower user's username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(followedUsername, "Followed user's username" + NULL_PARAM_ERROR);

		final String NO_USER_ERROR = "No user could be found for given name: ";
		User followerUser = null;
		User followedUser = null;
		boolean result = false;

		followerUser = usersBackedUp.get(followerUsername);
		followedUser = usersBackedUp.get(followedUsername);
		if (followerUser == null)
			throw new NoSuchUserException(NO_USER_ERROR + followerUsername);
		if (followedUser == null)
			throw new NoSuchUserException(NO_USER_ERROR + followedUsername);
		result = followerUser.follow(followedUser);
		if (result)
		{
			Set<User> tmp = new HashSet<>();
			tmp.add(followerUser);
			followersMap.compute(followedUser, (k, v) -> v == null ? tmp : Stream.concat(tmp.stream(), v.stream()).collect(Collectors.toSet()));
		}

		return result;
	}

	public boolean handleUnfollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(followerUsername, "Follower user's username" + NULL_PARAM_ERROR);
		Objects.requireNonNull(followedUsername, "Followed user's username" + NULL_PARAM_ERROR);

		final String NO_USER_ERROR = "No user could be found for given name: ";
		final User followerUser;
		User followedUser = null;
		boolean result = false;

		followerUser = usersBackedUp.get(followerUsername);
		followedUser = usersBackedUp.get(followedUsername);
		if (followerUser == null)
			throw new NoSuchUserException(NO_USER_ERROR + followerUsername);
		if (followedUser == null)
			throw new NoSuchUserException(NO_USER_ERROR + followedUsername);
		result = followerUser.unfollow(followedUser);
		if (result)
			followersMap.compute(followedUser, (k, v) -> v.stream().filter(user -> !user.equals(followerUser)).collect(Collectors.toSet()));

		return result;
	}

	public Set<String> handleGetWallet(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		Set<String> r = new HashSet<>();
		User u = null;
		List<Transaction> transactions = null;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);

		transactions = u.getTransactions();
		r.add(Double.toString(transactions.stream().mapToDouble(t -> t.amount).sum()) + "\r\n");
		for (Transaction t: transactions) r.add(t.toFormattedString());

		return r;
	}

	public String handleGetWalletInBitcoin(final String username)
	throws IOException, NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_PARAM_ERROR);

		User u = null;
		double rate = -1;

		u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		if (u == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);

		double wallet = u.getTransactions().stream().mapToDouble(t -> t.amount).sum();

		final String randomGenURL = "https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new";

		BufferedReader in = new BufferedReader(new InputStreamReader(new URL(randomGenURL).openStream()));
		rate = Double.parseDouble(in.readLine());
		in.close();
		
		return Double.toString(wallet * rate);
	}

	public void backupUsers(final File usersFile)
	throws FileNotFoundException, IOException
	{
		Map<String, User> tmp = new HashMap<>(usersToBeBackedUp);
		usersToBeBackedUp = new ConcurrentHashMap<>();
		backupCached(new ExclusionStrategy()
		{
			public boolean shouldSkipField(FieldAttributes f)
			{
				// skips "following" field specified inside User class.
				return f.getDeclaringClass() == User.class && f.getName().equals("following");
			}

			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
		}, usersFile, usersBackedUp, tmp, flag);
		flag = false;
	}

	public void backupFollowing(File followingFile)
	throws FileNotFoundException, IOException
	{
		backupNonCached(new ExclusionStrategy()
		{
			public boolean shouldSkipField(FieldAttributes f)
			{
				// skips everything except username and following field
				return f.getDeclaringClass() == User.class && !f.getName().equals("following") &&
						!f.getName().equals("username");
			}
			
			public boolean shouldSkipClass(Class<?> clazz)
			{
				return false;
			}
		}, followingFile, usersBackedUp);

	}

	/**
	 * @brief Instantiates UserMap given a backup of its users and one of their follows both written according to json syntax.
	 * @param usersFile cannot be null, must be a valid user backup.
	 * @param followingFile cannot be null, must be a valid backup of users' follows.
	 * @return Instantiated map on success.
	 * @throws FileNotFoundException FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws IllegalArchiveException if either usersFile or followingFile is not a valid archive (i.e. it does not follow json syntax or it does not
	 * this class' IR).
	 */
	public static UserMap fromJSON(final File usersFile, final File followingFile)
	throws FileNotFoundException, IOException, IllegalArchiveException
	{
		final String INVALID_STORAGE = "The files to be parsed are not a valid storage.";

		UserMap map = new UserMap();
		map.flag = true;

		InputStream is = new FileInputStream(usersFile);
		JsonReader reader = new JsonReader(new InputStreamReader(is));

		reader.setLenient(true);
		reader.beginArray();
		while (reader.hasNext())
		{
			reader.beginObject();
			String name = null;
			String username = null;
			String hashPassword = null;
			byte[] saltDecoded = null;
			Set<String> tags = null;
			User u = null;
			for (int i = 0; i < 4; i++)
			{
				name = reader.nextName();
				switch (name)
				{
					case "username":
						username = reader.nextString();
						break;

					case "hashPassword":
						hashPassword = reader.nextString();
						break;

					case "saltDecoded":
						saltDecoded = Base64.getDecoder().decode(reader.nextString());
						break;

					case "tags":
						reader.beginArray();
						tags = new HashSet<>();
						while (reader.hasNext())
						{
							reader.beginObject();
							String tmp = reader.nextName();
							if (!tmp.equals("name")) break;
							tags.add(reader.nextString());
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
			try { u = new User(username, hashPassword, tags, saltDecoded); }
			catch (InvalidTagException | TagListTooLongException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
			map.usersBackedUp.put(username, u);
			map.followersMap.put(u, new HashSet<>());
		}
		reader.endArray();
		reader.close();
		is.close();

		is = new FileInputStream(followingFile);
		reader = new JsonReader(new InputStreamReader(is));

		reader.setLenient(true);
		reader.beginArray();
		while (reader.hasNext())
		{
			reader.beginObject();
			String name = null;
			String username = null;
			Set<String> following = new HashSet<>();
			for (int i = 0; i < 2; i++)
			{
				name = reader.nextName();
				if (name.equals("username"))
					username = reader.nextString();
				else if (name.equals("following"))
				{
					reader.beginArray();
					while (reader.hasNext())
						following.add(reader.nextString());
					reader.endArray();
				}
				for (String s: following)
				{
					try
					{
						if (!map.handleFollowUser(username, s))
							throw new IllegalArchiveException(INVALID_STORAGE);
					}
					catch (NullPointerException | NoSuchUserException | SameUserException illegalJSON) { throw new IllegalArchiveException(INVALID_STORAGE); }
				}
			}
			reader.endObject();
		}
		reader.endArray();
		reader.close();
		is.close();

		for (User u: map.usersBackedUp.values())
		{
			for (Tag t: u.getTags())
			{
				if (map.interestsMap.get(t) == null)
					map.interestsMap.put(t, new HashSet<>());
				map.interestsMap.get(t).add(u);
			}
			for (String s: u.getFollowing())
			{
				User followed = map.usersBackedUp.get(s);
				if (map.followersMap.get(followed) == null)
					map.followersMap.put(followed, new HashSet<>());
				map.followersMap.get(followed).add(u);
			}
		}
		return map;
	}
}
