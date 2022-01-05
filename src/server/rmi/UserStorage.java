package server.rmi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import server.user.User;

public interface UserStorage
{
	public User getUser(final String username);

	public void backupUsers(final File file)
	throws FileNotFoundException, IOException;

	public void backupFollowing(final File file)
	throws FileNotFoundException, IOException;

	public Set<String> getAllUsersWithSameInterestsAs(final String username);

	public boolean addFollower(final String followed, final String follower);
}