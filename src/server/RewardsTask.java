package server;

import server.storage.NoSuchUserException;
import server.storage.PostStorage;
import server.storage.UserStorage;
import user.InvalidAmountException;

public class RewardsTask implements Runnable
{
	private UserStorage users = null;
	private PostStorage posts = null;

	public final int interval = 500;

	public RewardsTask(UserStorage users, PostStorage posts)
	{
		this.users = users;
		this.posts = posts;
	}

	public void run()
	{
		while (true)
		{
			try { Thread.sleep(interval); }
			catch (InterruptedException shutdown) { return; }
			try { users.updateRewards(posts.calculateGains(), 70); }
			catch (NoSuchUserException | InvalidAmountException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); } // server is not in a consistent state

		}
	}
}
