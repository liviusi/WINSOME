package server.storage;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

import api.rmi.InvalidTagException;
import api.rmi.PasswordNotValidException;
import api.rmi.TagListTooLongException;
import api.rmi.UserRMIStorage;
import api.rmi.UsernameAlreadyExistsException;
import api.rmi.UsernameNotValidException;
import cryptography.Passwords;
import server.post.Post.GainAndCurators;
import server.user.*;

/**
 * User storage backed by a hashmap. This class is thread-safe.
 * @author Giacomo Trapani.
 */
public class UserMap extends Storage implements UserRMIStorage, UserStorage
{
	/** Users already stored inside backup file. */
	private Map<String, User> usersBackedUp = null; // already backed up
	/** Users yet to be stored. */
	private Map<String, User> usersToBeBackedUp = null;
	/** Toggled on if this is the first backup and the storage has been recovered from a JSON file. */
	private boolean usersFirstBackupAndNonEmptyStorage = false;
	/** Maps a tag to the set of the users currently interested in it. */
	private Map<Tag, Set<User>> interestsMap = null;
	/** Maps a user to the set of users currently following it. */
	private Map<User, Set<User>> followersMap = null;

	/** Used to allow for every method to run concurrently as long as a backup is not occurring. */
	private ReentrantReadWriteLock backupLock = new ReentrantReadWriteLock(true);
	/** Used to allow for every method not directly editing this class' fields to run concurrently. */
	private ReentrantReadWriteLock dataAccessLock = new ReentrantReadWriteLock(true);

	/** Empty string. */
	private static final String EMPTY_STRING = "";
	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";
	/** Part of the exception message when a user cannot be find in the storage. */
	private static final String NOT_SIGNED_UP = " has yet to sign up.";

	/** Default constructor. */
	public UserMap()
	{
		usersBackedUp = new HashMap<>();
		usersToBeBackedUp = new HashMap<>();
		usersFirstBackupAndNonEmptyStorage = false;
		interestsMap = new HashMap<>();
		followersMap = new HashMap<>();
	}

	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(password, "Password" + NULL_ERROR);
		Objects.requireNonNull(tags, "Tags" + NULL_ERROR);
		Objects.requireNonNull(salt, "Salt" + NULL_ERROR);

		final String EMPTY_ERROR = " cannot be empty.";
		String emptyStringHashed = null;
		final User u;

		if (username.isEmpty()) throw new UsernameNotValidException("Username" + EMPTY_ERROR);
		emptyStringHashed = Passwords.hashPassword(EMPTY_STRING.getBytes(StandardCharsets.US_ASCII), salt);
		if (emptyStringHashed.equals(password)) throw new PasswordNotValidException("Password" + EMPTY_ERROR);
		u = new User(username, password, tags, salt);

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.writeLock().lock();
				if (usersToBeBackedUp.containsKey(username) || usersBackedUp.containsKey(username)) // username already exists
					throw new UsernameAlreadyExistsException("Username has already been taken.");
				usersToBeBackedUp.put(username, u);
				u.getTags().forEach(t -> 
					{
						Set<User> tmp = new HashSet<>(); tmp.add(u);
						Set<User> value = null;
						if ((value = interestsMap.get(t)) == null) interestsMap.put(t, tmp);
						else value.add(u);
				});
				followersMap.put(u, new HashSet<>());
			}
			finally { dataAccessLock.writeLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public String usernameToUserString(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username cannot be null");

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				return getUserByName(username).toString();
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public Set<String> recoverFollowers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);

		final User u;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				return followersMap.get(u)
					.stream()
					.map(follower ->
						follower.toString())
				.collect(Collectors.toSet());
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public void updateRewards(Map<String, GainAndCurators> gains, double authorPercentage)
	throws IllegalArgumentException, InvalidAmountException, NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(gains, "Gains" + NULL_ERROR);
		if (authorPercentage <= 0 || authorPercentage >= 100) throw new IllegalArgumentException("Author percentage is not a valid percentage.");

		User u = null;
		Transaction t = null;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				for (Entry<String, GainAndCurators> entry: gains.entrySet())
				{
					final String username = entry.getKey();
					final double gain = entry.getValue().gain;
					final Set<String> curators = entry.getValue().getCurators();

					if (gain == 0) continue;
					if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
					t = new Transaction((gain * authorPercentage) / 100);
					u.addTransaction(t);

					for (String s: curators)
					{
						if ((u = getUserByName(s)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
						t = new Transaction((gain * (100 - authorPercentage)) / (100 * curators.size()));
						u.addTransaction(t);
					}
				}
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);

		final User u;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				return u.saltDecoded;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_ERROR);
		Objects.requireNonNull(hashPassword, "Hashed password" + NULL_ERROR);

		final User u;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				u.login(clientID, hashPassword);
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);
		Objects.requireNonNull(clientID, "Client ID" + NULL_ERROR);

		final User u;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				u.logout(clientID);
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);

		Set<String> r = new HashSet<>();
		final User u;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				u.getTags().forEach(t ->
					interestsMap.get(t).forEach(tUser ->
					{
						if (!tUser.username.equals(username)) r.add(tUser.toString());
					})
				);
				return r;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public Set<String> handleListFollowing(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);

		Set<String> r = new HashSet<>();
		final User u;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				u.getFollowing().forEach(following ->
				{
					try { r.add(getUserByName(following).toString()); }
					catch (NullPointerException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); } // storage is in an inconsistent state
				});
				return r;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws SameUserException, NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(followerUsername, "Follower user's username" + NULL_ERROR);
		Objects.requireNonNull(followedUsername, "Followed user's username" + NULL_ERROR);

		final User followerUser;
		final User followedUser;
		boolean result = false;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.writeLock().lock();
				if ((followerUser = getUserByName(followerUsername)) == null) throw new NoSuchUserException(followerUsername + NOT_SIGNED_UP);
				if ((followedUser = getUserByName(followedUsername)) == null) throw new NoSuchUserException(followedUsername + NOT_SIGNED_UP);
				result = followerUser.follow(followedUser);
				if (result)
				{
					Set<User> tmp = new HashSet<>(); tmp.add(followerUser);
					followersMap.compute(followedUser, (k, v) -> v == null ? tmp : Stream.concat(tmp.stream(), v.stream()).collect(Collectors.toSet()));
				}
				return result;
			}
			finally { dataAccessLock.writeLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public boolean handleUnfollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(followerUsername, "Follower user's username" + NULL_ERROR);
		Objects.requireNonNull(followedUsername, "Followed user's username" + NULL_ERROR);

		final User followerUser;
		final User followedUser;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.writeLock().lock();
				if ((followerUser = getUserByName(followerUsername)) == null) throw new NoSuchUserException(followerUsername + NOT_SIGNED_UP);
				if ((followedUser = getUserByName(followedUsername)) == null) throw new NoSuchUserException(followedUsername + NOT_SIGNED_UP);
				if (followerUser.unfollow(followedUser))
				{
					followersMap.get(followedUser).remove(followerUser);
					return true;
				}
				return false;
			}
			finally { dataAccessLock.writeLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public Set<String> handleGetWallet(final String username)
	throws NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);

		Set<String> r = new HashSet<>();
		final User u;
		List<Transaction> transactions = null;

		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				transactions = u.getTransactions();
				r.add(Double.toString(transactions.stream().mapToDouble(t -> t.amount).sum()) + "\r\n");
				for (Transaction t: transactions) r.add(t.toString());
				return r;
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
	}

	public String handleGetWalletInBitcoin(final String username)
	throws IOException, NoSuchUserException, NullPointerException
	{
		Objects.requireNonNull(username, "Username" + NULL_ERROR);

		final User u;
		double rate = -1;
		final String randomGenURL = "https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new";

		try (InputStream ir = new URL(randomGenURL).openStream(); InputStreamReader isr = new InputStreamReader(ir); BufferedReader in = new BufferedReader(isr))
		{
			try { rate = Double.parseDouble(in.readLine()); }
			catch (NumberFormatException shouldNeverBeThrown) { throw new IOException(shouldNeverBeThrown); }
		}
		try
		{
			backupLock.readLock().lock();
			try
			{
				dataAccessLock.readLock().lock();
				if ((u = getUserByName(username)) == null) throw new NoSuchUserException(username + NOT_SIGNED_UP);
				return Double.toString(u.getTransactions().stream().mapToDouble(t -> t.amount).sum() * rate);
			}
			finally { dataAccessLock.readLock().unlock(); }
		}
		finally { backupLock.readLock().unlock(); }
		
	}

	public void backupUsers(final File usersImmutableDataFile, final File followingFile, final File transactionsFile)
	throws FileNotFoundException, IOException, NullPointerException
	{
		Objects.requireNonNull(usersImmutableDataFile, "Users' immutable data file" + NULL_ERROR);
		Objects.requireNonNull(followingFile, "Users' following file" + NULL_ERROR);
		Objects.requireNonNull(transactionsFile, "Users' transactions' file" + NULL_ERROR);

		try
		{
			backupLock.writeLock().lock();
			backupCached(new ExclusionStrategy()
			{
				public boolean shouldSkipField(FieldAttributes f)
				{
					// skips "following" and "transactions" fields specified inside User class.
					return f.getDeclaringClass() == User.class && (f.getName().equals("following") || f.getName().equals("transactions"));
				}

				public boolean shouldSkipClass(Class<?> clazz)
				{
					return false;
				}
			}, usersImmutableDataFile, usersBackedUp, usersToBeBackedUp, usersFirstBackupAndNonEmptyStorage);
			usersFirstBackupAndNonEmptyStorage = false;
			usersToBeBackedUp = new HashMap<>();

			backupNonCached(new ExclusionStrategy()
			{
				public boolean shouldSkipField(FieldAttributes f)
				{
					// skips everything except "username" and "following".
					return f.getDeclaringClass() == User.class && !f.getName().equals("following") &&
							!f.getName().equals("username");
				}
				
				public boolean shouldSkipClass(Class<?> clazz)
				{
					return false;
				}
			}, followingFile, usersBackedUp);

			backupNonCached(new ExclusionStrategy()
			{
				public boolean shouldSkipField(FieldAttributes f)
				{
					// skips everything except "username" and "transactions".
					return f.getDeclaringClass() == User.class && !f.getName().equals("transactions") &&
							!f.getName().equals("username");
				}
				
				public boolean shouldSkipClass(Class<?> clazz)
				{
					return false;
				}
			}, transactionsFile, usersBackedUp);
		}
		finally { backupLock.writeLock().unlock(); }
	}

	/**
	 * Instantiates UserMap given a backup of its users, one of their follows and one of their transactions all written according to JSON syntax.
	 * @param usersFile cannot be null, must be a valid user backup.
	 * @param followingFile cannot be null, must be a valid backup of users' follows.
	 * @return Instantiated map on success.
	 * @throws FileNotFoundException FileNotFoundException if the file exists but is a directory rather than a regular file, does not exist but cannot be created,
	 * or cannot be opened for any other reason.
	 * @throws IOException if I/O error(s) occur.
	 * @throws IllegalArchiveException if either usersFile or followingFile is not a valid archive (i.e. it does not follow JSON syntax or
	 * this class' IR).
	 */
	public static UserMap fromJSON(final File usersFile, final File followingFile, final File transactionsFile)
	throws FileNotFoundException, IOException, IllegalArchiveException
	{
		final String INVALID_STORAGE = "The files to be parsed are not a valid storage.";
		Gson generator = new Gson();

		UserMap map = new UserMap();
		map.usersFirstBackupAndNonEmptyStorage = true;

		try (final InputStream is = new FileInputStream(usersFile); final JsonReader reader = new JsonReader(new InputStreamReader(is)))
		{
			reader.setLenient(true);
			try { reader.beginArray(); }
			catch (EOFException emptyFile)
			{
				FileChannel.open(followingFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
				FileChannel.open(transactionsFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
				throw new IllegalArchiveException(INVALID_STORAGE);
			}
			while (reader.hasNext())
			{
				reader.beginObject();
				String name = null;
				String username = null;
				String hashPassword = null;
				byte[] saltDecoded = null;
					Set<String> tags = null;
				User u = null;
				while (reader.hasNext())
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
				catch (InvalidTagException | TagListTooLongException illegalJSON)
				{
					FileChannel.open(usersFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
					FileChannel.open(followingFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
					FileChannel.open(transactionsFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
					throw new IllegalArchiveException(INVALID_STORAGE);
				}
				map.usersBackedUp.put(username, u);
				map.followersMap.put(u, new HashSet<>());
			}
			reader.endArray();
		}
		try (final InputStream is = new FileInputStream(followingFile); final JsonReader reader = new JsonReader(new InputStreamReader(is)))
		{
			reader.setLenient(true);
			try { reader.beginArray(); }
			catch (EOFException emptyFile)
			{
				FileChannel.open(usersFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
				FileChannel.open(transactionsFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
				throw new IllegalArchiveException(INVALID_STORAGE);
			}
			while (reader.hasNext())
			{
				reader.beginObject();
				String name = null;
				String username = null;
				Set<String> following = new HashSet<>();
				while(reader.hasNext())
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
							{
								FileChannel.open(usersFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
								FileChannel.open(followingFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
								FileChannel.open(transactionsFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
								throw new IllegalArchiveException(INVALID_STORAGE);
							}
						}
						catch (NullPointerException | NoSuchUserException | SameUserException illegalJSON)
						{
							FileChannel.open(usersFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
							FileChannel.open(followingFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
							FileChannel.open(transactionsFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
							throw new IllegalArchiveException(INVALID_STORAGE);
						}
					}
				}
				reader.endObject();
			}
			reader.endArray();
		}
		try (final InputStream is = new FileInputStream(transactionsFile); final JsonReader reader = new JsonReader(new InputStreamReader(is)))
		{
			reader.setLenient(true);
			try { reader.beginArray(); }
			catch (EOFException emptyFile)
			{
				FileChannel.open(usersFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
				FileChannel.open(followingFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
				throw new IllegalArchiveException(INVALID_STORAGE);
			}
			while (reader.hasNext())
			{
				reader.beginObject();
				String name = null;
				String username = null;
				JsonObject object = new JsonObject();
				while (reader.hasNext())
				{
					name = reader.nextName();
					boolean flag = false;
					if (name.equals("username")) username = reader.nextString();
					else if (name.equals("transactions"))
					{
						reader.beginArray();
						while (reader.hasNext())
						{
							flag = true;
							reader.beginObject();
							while (reader.hasNext())
							{
								name = reader.nextName();
								if (name.equals("amount")) object.addProperty(name, reader.nextDouble());
								else if (name.equals("timestamp")) object.addProperty(name, reader.nextString());
								else throw new IllegalArchiveException(INVALID_STORAGE);
							}
							reader.endObject();
							if (flag) try { map.getUserByName(username).addTransaction(generator.fromJson(object, Transaction.class)); }
							catch (NullPointerException illegalJSON)
							{
								FileChannel.open(usersFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
								FileChannel.open(followingFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
								FileChannel.open(transactionsFile.toPath(), StandardOpenOption.WRITE).truncate(0).close();
								throw new IllegalArchiveException(INVALID_STORAGE);
							}
						}
						reader.endArray();
					}
					else reader.skipValue();
				}
				reader.endObject();
			}
			reader.endArray();
		}

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

	private User getUserByName(final String username)
	{
		User u = usersBackedUp.get(username);
		if (u == null) u = usersToBeBackedUp.get(username);
		return u;
	}
}
