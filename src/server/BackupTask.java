package server;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import configuration.ServerConfiguration;
import server.storage.PostStorage;
import server.storage.UserStorage;

/**
 * @brief Utility class used to group together the whole backup logic.
 * @author Giacomo Trapani
 */
public class BackupTask implements Runnable
{
	/** Milliseconds to be spent sleeping between backups. */
	public static final int SLEEPINGTIME = 500;

	/** Users' backup file */
	private final File usersFile;
	/** Users' follows backup file */
	private final File followingFile;
	private final File transactionsFile = new File("./storage/transactions.json");
	/** Pointer to user storage */
	private final UserStorage users;

	private final PostStorage posts;
	private final File postsImmutableFile;
	private final File postsReactionsFile;

	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * @brief Default constructor.
	 * @param usersFile cannot be null. It is the file the users' information will be stored in.
	 * @param followingFile cannot be null. It is the file the users' following information will be stored in.
	 * @param users cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 */
	public BackupTask(final ServerConfiguration configuration, final UserStorage users, final PostStorage posts)
	throws NullPointerException
	{
		Objects.requireNonNull(configuration, "Configuration" + NULL_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		Objects.requireNonNull(posts, "Post storage" + NULL_ERROR);
		this.usersFile = new File(configuration.userStorageFilename);
		this.followingFile = new File(configuration.followingStorageFilename);
		this.postsImmutableFile = new File(configuration.postStorageFilename);
		this.postsReactionsFile = new File(configuration.postsInteractionsStorageFilename);
		this.users = users;
		this.posts = posts;
	}

	public void run()
	{
		while (true)
		{
			try { Thread.sleep(SLEEPINGTIME); }
			catch (InterruptedException shouldTerminate) { return; }
			try
			{
				users.backupUsers(usersFile, followingFile, transactionsFile);
				posts.backupPosts(postsImmutableFile, postsReactionsFile);
			}
			catch (IOException e)
			{
				System.err.printf("Fatal error occurred in BackupTask:\n%s\n", e.getMessage());
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
}
