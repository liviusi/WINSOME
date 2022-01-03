package server.rmi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import server.user.User;

public interface UserStorage
{
	public User getUser(String username);

	public void backupUsers(File file)
	throws FileNotFoundException, IOException;

	public Set<String> getAllUsersWithSameInterestsAs(String username);
}