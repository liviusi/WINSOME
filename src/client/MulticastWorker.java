package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import api.Colors;

public class MulticastWorker implements Runnable
{
	private MulticastSocket multicastSocket = null;
	private Queue<String> messages = null;
	private AtomicBoolean isLogged = null;
	InetAddress group = null;

	public MulticastWorker(MulticastSocket multicastSocket, InetAddress group, AtomicBoolean isLogged, Queue<String> messages)
	{
		this.multicastSocket = multicastSocket;
		this.isLogged = isLogged;
		this.group = group;
		this.messages = messages;
	}

	public void run()
	{
		while(!Thread.interrupted())
		{
			byte[] bytes = new byte[2048];
			DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
			try { multicastSocket.receive(packet); }
			catch (SocketTimeoutException ignored) { continue; }
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
