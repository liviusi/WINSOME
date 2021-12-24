package configuration;

public class Constants
{
	static final String SERVERADDRESS_STRING = "SERVERADDRESS";
	static final String PORTNOTCP_STRING = "TCPPORT";
	static final String PORTNOUDP_STRING = "UDPPORT";
	static final String MULTICASTADDRESS_STRING = "MULTICASTADDRESS";
	static final String PORTNOMULTICAST_STRING = "MULTICASTPORT";
	static final String REGISTRYADDRESS_STRING = "REGISTRYHOST";
	static final String PORTNOREGISTRY_STRING = "REGISTRYPORT";
	static final String SOCKETTIMEOUT_STRING = "SOCKETTIMEOUT";

	private Constants() { }

	/**
	 * @param portNoStr string to be parsed, it cannot be null.
	 * @return the string as an integer.
	 * @throws NullPointerException if string is null.
	 * @throws NumberFormatException if string is not a valid number.
	 * @throws IllegalArgumentException if string is not in range [1024; 65535].
	*/
	static int parsePortNo(final String portNoStr) throws NullPointerException, NumberFormatException, IllegalArgumentException
	{
		if (portNoStr == null) throw new NullPointerException();
		final int portNoInt = Integer.parseInt(portNoStr);
		if (portNoInt < 1024 || portNoInt > 65535) throw new IllegalArgumentException("Given port is not in the valid range: [1024; 65535].");
		return portNoInt;
	}
}
