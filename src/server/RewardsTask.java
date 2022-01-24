package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import configuration.ServerConfiguration;
import server.storage.NoSuchUserException;
import server.storage.PostStorage;
import server.storage.UserStorage;
import user.InvalidAmountException;

/**
 * @brief Utility class used to group together the whole rewards' logic as a single task.
 * @author Giacomo Trapani.
 */
public class RewardsTask implements Runnable
{
	/** Pointer to user storage. */
	private UserStorage users = null;
	/** Pointer to post storage. */
	private PostStorage posts = null;
	/** Socket to be instantiated for multicast. */
	MulticastSocket socket = null;

	/** Interval to be spent idly before reissuing rewards. */
	private final int interval;
	/** Port number for multicast. */
	private final int portNo;
	/** Address for multicast. */
	private final InetAddress multicastAddress;

	private final double authorPercentage;

	/**
	 * @brief Default constructor.
	 * @param users cannot be null.
	 * @param posts cannot be null.
	 * @param configuration cannot be null.
	 * @throws IOException refer to MulticastSocket(int port).
	 * @throws NullPointerException if any parameter is null.
	 */
	public RewardsTask(final ServerConfiguration configuration, UserStorage users, PostStorage posts)
	throws IOException, NullPointerException
	{
		Objects.requireNonNull(configuration, "Configuration cannot be null.");
		this.users = Objects.requireNonNull(users, "Storage cannot be null.");
		this.posts = Objects.requireNonNull(posts, "Storage cannot be null.");

		socket = new MulticastSocket(configuration.portNoMulticast);
		this.portNo = configuration.portNoMulticast;
		this.multicastAddress = configuration.multicastAddress;
		this.interval = configuration.rewardsInterval;
		this.authorPercentage = configuration.rewardsAuthorPercentage;
	}

	public void run()
	{
		byte[] bytes = "Rewards have now been calculated!".getBytes(StandardCharsets.US_ASCII);
		System.out.println(multicastAddress.getHostAddress() + ":" + portNo);
		while (true)
		{
			try { Thread.sleep(interval); }
			catch (InterruptedException shutdown)
			{
				socket.close();
				return;
			}
			try { users.updateRewards(posts.calculateGains(), authorPercentage); }
			catch (NoSuchUserException | InvalidAmountException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); } // server is not in a consistent state

			DatagramPacket message = new DatagramPacket(bytes, bytes.length, multicastAddress, portNo);
			try { socket.send(message); }
			catch (IOException e) { throw new RuntimeException(e); }
		}
	}
}
