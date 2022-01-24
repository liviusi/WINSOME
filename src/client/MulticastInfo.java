package client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * @brief Utility class used to store a couple (address, port) denoting a valid multicast coordinate.
 * @author Giacomo Trapani.
 */
public class MulticastInfo
{
	private final String address;
	public final int portNo;

	private static final Gson gson = new Gson();

	/** Constructor is private to avoid having non-valid MulticastInfo objects. */
	private MulticastInfo(final String address, final int portNo)
	{
		this.address = address;
		this.portNo = portNo;
	}

	/** Parses a valid MulticastInfo instance from a valid JSON message. */
	public static MulticastInfo fromJSON(String jsonMessage)
	{
		MulticastInfo m = null;
		try { m = gson.fromJson(Objects.requireNonNull(jsonMessage, "Message cannot be null."), MulticastInfo.class); }
		catch (JsonSyntaxException e) { return null; }
		InetAddress address = m.getAddress();
		if (address == null || !address.isMulticastAddress()) return null;
		if (m.portNo < 1024 || m.portNo > 65535) return null;
		return m;
	}

	/** Getter for InetAddress. */
	public InetAddress getAddress()
	{
		try { return InetAddress.getByName(address); }
		catch (UnknownHostException e) { return null; }
	}
}