package server.storage;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.util.Objects;
import java.util.Set;

import configuration.ServerConfiguration;
import server.post.InvalidGeneratorException;
import server.post.InvalidPostException;
import user.InvalidLoginException;
import user.InvalidLogoutException;
import user.InvalidTagException;
import user.SameUserException;
import user.TagListTooLongException;
import user.WrongCredentialsException;

public class ServerStorage
{
	private UserMap users = null;
	private PostStorage posts = null;

	private ServerStorage(UserMap users, PostStorage posts)
	{
		this.users = users;
		this.posts = posts;
	}

	public void register(final String username, final String password, final Set<String> tags, final byte[] salt)
	throws NullPointerException, RemoteException, UsernameNotValidException, UsernameAlreadyExistsException,
		PasswordNotValidException, InvalidTagException, TagListTooLongException
	{
		users.register(username, password, tags, salt);
	}

	public String handleLoginSetup(final String username)
	throws NoSuchUserException, NullPointerException
	{
		return users.handleLoginSetup(username);
	}

	public void handleLogin(final String username, final SocketChannel clientID, final String hashPassword)
	throws InvalidLoginException, NoSuchUserException, WrongCredentialsException, NullPointerException
	{
		users.handleLogin(username, clientID, hashPassword);
	}

	public void handleLogout(final String username, final SocketChannel clientID)
	throws NoSuchUserException, InvalidLogoutException, NullPointerException
	{
		users.handleLogout(username, clientID);
	}

	public Set<String> handleListUsers(final String username)
	throws NoSuchUserException, NullPointerException
	{
		return users.handleListUsers(username);
	}

	public Set<String> handleListFollowing(final String username)
	throws NoSuchUserException, NullPointerException
	{
		return users.handleListFollowing(username);
	}

	public boolean handleFollowUser(final String followerUsername, final String followedUsername)
	throws SameUserException, NoSuchUserException, NullPointerException
	{
		return users.handleFollowUser(followerUsername, followedUsername);
	}

	public boolean handleUnfollowUser(final String followerUsername, final String followedUsername)
	throws NoSuchUserException, NullPointerException
	{
		return users.handleUnfollowUser(followerUsername, followedUsername);
	}

	public Set<String> handleBlog(final String author)
	throws NoSuchPostException, NullPointerException
	{
		return posts.handleBlog(author);
	}

	public int handleCreatePost(final String author, final String title, final String contents)
	throws InvalidPostException, InvalidGeneratorException, NullPointerException
	{
		return posts.handleCreatePost(author, title, contents);
	}

	public Set<String> handleShowFeed(final String username, final UserStorage users)
	throws NoSuchUserException, NullPointerException
	{
		return posts.handleShowFeed(username, users);
	}

	public static ServerStorage fromJSON(ServerConfiguration configuration)
	{
		Objects.requireNonNull(configuration, "Server's configuration cannot be null.");
		ServerStorage res = null;

		try
		{
			UserMap users = UserMap.fromJSON(new File(configuration.userStorageFilename), new File(configuration.followingStorageFilename));
			res = new ServerStorage(users, PostMap.fromJSON(new File(configuration.postStorageFilename), new File(configuration.postsInteractionsStorageFilename), users));
		}
		catch (IOException | IllegalArchiveException e)
		{
			res = new ServerStorage(new UserMap(), new PostMap());
		}
		catch (InvalidGeneratorException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); }

		return res;
	}
}
