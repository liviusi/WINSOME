package server;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import server.storage.UserStorage;

/**
 * @brief Utility class used to group together the whole backup logic.
 * @author Giacomo Trapani
 */
public class BackupTask implements Runnable
{
	/** Milliseconds to be spent sleeping between backups. */
	public static final int SLEEPINGTIME = 5000;

	/** Users' backup file */
	private final File usersFile;
	/** Users' follows backup file */
	private final File followingFile;
	/** Pointer to user storage */
	private final UserStorage users;

	/** Part of the exception message when NPE is thrown. */
	private static final String NULL_ERROR = " cannot be null.";

	/**
	 * @brief Default constructor.
	 * @param usersFile cannot be null. It is the file the users' information will be stored in.
	 * @param followingFile cannot be null. It is the file the users' following information will be stored in.
	 * @param users cannot be null.
	 * @throws NullPointerException if any parameter is null.
	 */
	public BackupTask(final File usersFile, final File followingFile, final UserStorage users)
	throws NullPointerException
	{
		Objects.requireNonNull(usersFile, "Users' backup file" + NULL_ERROR);
		Objects.requireNonNull(followingFile, "Users' follows backup file" + NULL_ERROR);
		Objects.requireNonNull(users, "User storage" + NULL_ERROR);
		if (usersFile == null || users == null)
			throw new NullPointerException("Constructor parameters cannot be null.");
		this.usersFile = usersFile;
		this.followingFile = followingFile;
		this.users = users;

	}

	public void run()
	{
		while (true)
		{
			try { Thread.sleep(SLEEPINGTIME); }
			catch (InterruptedException shouldTerminate) { return; }
			try
			{
				users.backupUsers(usersFile);
				users.backupFollowing(followingFile);
			}
			catch (IOException e)
			{
				System.err.printf("Fatal error occurred in BackupTask:\n%s\n", e.getMessage());
				System.exit(1);
			}
		}
	}
}
