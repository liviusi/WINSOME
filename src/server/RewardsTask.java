package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

import configuration.ServerConfiguration;
import server.storage.NoSuchUserException;
import server.storage.PostStorage;
import server.storage.UserStorage;
import user.InvalidAmountException;

public class RewardsTask implements Runnable
{
	private UserStorage users = null;
	private PostStorage posts = null;
	MulticastSocket socket = null;

	private final int interval;
	private final int portNo;
	private final InetAddress multicastAddress;

	public RewardsTask(UserStorage users, PostStorage posts, final ServerConfiguration configuration)
	throws IOException
	{
		this.users = users;
		this.posts = posts;

		socket = new MulticastSocket(configuration.portNoMulticast);
		this.portNo = configuration.portNoMulticast;
		this.multicastAddress = configuration.multicastAddress;
		this.interval = configuration.rewardsInterval;
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
			try { users.updateRewards(posts.calculateGains(), 70); }
			catch (NoSuchUserException | InvalidAmountException shouldNeverBeThrown) { throw new IllegalStateException(shouldNeverBeThrown); } // server is not in a consistent state

			DatagramPacket message = new DatagramPacket(bytes, bytes.length, multicastAddress, portNo);
			try { socket.send(message); }
			catch (IOException e) { throw new RuntimeException(e); }
		}
	}
}
