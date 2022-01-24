package client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class MulticastInfo
{
	private final String address;
	public final int portNo;

	private static final Gson gson = new Gson();

	private MulticastInfo(final String address, final int portNo)
	{
		this.address = address;
		this.portNo = portNo;
	}

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

	public InetAddress getAddress()
	{
		try { return InetAddress.getByName(address); }
		catch (UnknownHostException e) { return null; }
	}
}