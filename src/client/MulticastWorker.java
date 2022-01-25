package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import api.Colors;

/**
 * @brief Class implementing the task of receiving a new message over multicast.
 * @author Giacomo Trapani.
 */
public class MulticastWorker implements Runnable
{
	private MulticastSocket multicastSocket = null;
	/** Pointer to a thread-safe queue of Strings storing the messages yet to be read. */
	private Queue<String> messages = null;
	/** Toggled on if a user is currently logged in from the client this class has been instantiated by. */
	private AtomicBoolean isLogged = null;
	InetAddress group = null;

	private static final String NULL_ERROR = " cannot be null.";

	/** Default constructor */
	public MulticastWorker(MulticastSocket multicastSocket, InetAddress group, AtomicBoolean isLogged, Queue<String> messages)
	{
		this.multicastSocket = Objects.requireNonNull(multicastSocket, "MulticastSocket" + NULL_ERROR);
		this.isLogged = Objects.requireNonNull(isLogged, "AtomicBoolean" + NULL_ERROR);
		this.group = Objects.requireNonNull(group, "Address" + NULL_ERROR);
		this.messages = Objects.requireNonNull(messages, "Queue of messages" + NULL_ERROR);
	}

	public void run()
	{
		while(!Thread.currentThread().isInterrupted()) // an interrupt is to be sent from the main thread before terminating
		{
			byte[] bytes = new byte[2048];
			DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
			try { multicastSocket.receive(packet); }
			catch (SocketTimeoutException ignored) { continue; } // it's ok
			catch (IOException e)
			{
				System.out.println(Colors.ANSI_RED + "Fatal I/O error occurred: now aborting..." + Colors.ANSI_RESET);
				e.printStackTrace();
				System.exit(1);
			}
			String s = new String(packet.getData(), StandardCharsets.US_ASCII);
			if (isLogged.get() && !s.isEmpty() && s.matches(".*[a-zA-Z]+.*")) messages.offer(s);

		}
		try { multicastSocket.leaveGroup(group); }
		catch (IOException ignored) { }
		multicastSocket.close();
		return;
	}
}
