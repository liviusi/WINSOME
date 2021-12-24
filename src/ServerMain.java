import java.io.File;

import configuration.*;

/**
 * @brief Server file.
 * @author Giacomo Trapani
 */

public class ServerMain
{
	public static void main(String[] args)
	{
		System.out.println("Server is now running...");
		if (args.length != 1)
		{
			System.err.println("A configuration file must be specified.");
			System.exit(1);
		}
		ServerConfiguration config = null;
		try { config = new ServerConfiguration(new File(args[0])); }
		catch (Exception e) { e.printStackTrace(); }

		System.out.printf("SERVERADDRESS=%s\nTCPPORT=%d\nUDPPORT=%d\n", config.serverAddress.getHostName(), config.portNoTCP, config.portNoUDP);
	}
}