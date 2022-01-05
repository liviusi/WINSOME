package server;

import java.io.File;
import java.io.IOException;

import server.storage.UserStorage;

public class BackupTask implements Runnable
{
	public static final int SLEEPINGTIME = 5000; // milliseconds to be spent sleeping

	private final File usersFile;
	private UserStorage users = null;

	public BackupTask(File usersFile, UserStorage users)
	{
		if (usersFile == null || users == null)
			throw new NullPointerException("Constructor parameters cannot be null.");
		this.usersFile = usersFile;
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
				users.backupFollowing(new File("./storage/following.json"));
			}
			catch (IOException e)
			{
				System.err.printf("Fatal error occurred in BackupTask:\n%s\n", e.getMessage());
				System.exit(1);
			}
		}
	}
}
