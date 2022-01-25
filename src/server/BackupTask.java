package server;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import configuration.ServerConfiguration;
import server.storage.PostStorage;
import server.storage.UserStorage;

/**
 * Utility class used to group together the whole backup logic as a single task.
 * @author Giacomo Trapani.
 */
public class BackupTask implements Runnable
{
	/** Time between backups. */
	private final int sleepingTime;
	/** Users' backup file. */
	private final File usersFile;
	/** Users' follows' backup file. */
	private final File followingFile;
	/** Users' transactions' backup file. */
	private final File transactionsFile;
	/** Pointer to User storage. */
	private final UserStorage users;
	/** Pointer to Post storage. */
	private final PostStorage posts;
	/** Posts' immutable data backup file. */
	private final File postsImmutableFile;
	/** Posts' mutable data backup file. */
	private final File postsReactionsFile;

	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * Default constructor.
	 * @param configuration cannot be null.
	 * @param posts cannot be null.
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
		this.transactionsFile = new File(configuration.transactionsFilename);
		this.postsImmutableFile = new File(configuration.postStorageFilename);
		this.postsReactionsFile = new File(configuration.postsInteractionsStorageFilename);
		this.sleepingTime = configuration.backupInterval;
		this.users = users;
		this.posts = posts;
	}

	public void run()
	{
		System.out.println("Backup task is now running!");
		while (!Thread.currentThread().isInterrupted())
		{
			try { Thread.sleep(sleepingTime); }
			catch (InterruptedException shouldTerminate) { return; }
			try
			{
				users.backupUsers(usersFile, followingFile, transactionsFile);
				posts.backupPosts(postsImmutableFile, postsReactionsFile);
			}
			catch (IOException e)
			{
				System.err.println("Fatal error occurred in BackupTask: now aborting...");
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
}
