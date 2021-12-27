import java.io.File;
import java.io.IOException;

import configuration.InvalidConfigException;
import configuration.ServerConfiguration;
import server.RMITask;

/**
 * @brief Server file.
 * @author Giacomo Trapani
 */

public class ServerMain
{
	public static void main(String[] args)
	{
		if (args.length >= 1)
		{
			System.err.println("Usage: java -cp \".:./bin/:./libs/gson-2.8.6.jar\" ServerMain <path/to/config>");
			System.exit(1);
		}
		String configFilename = null;
		if (args.length == 0)
		{
			configFilename = "./configs/server.txt";
			System.out.printf("No config files have been provided. Default will be used: %s.\n", configFilename);
		}
		else configFilename = args[0];

		ServerConfiguration configuration = null;
		try { configuration = new ServerConfiguration(new File(configFilename)); }
		catch (NullPointerException | IOException | InvalidConfigException  e)
		{
			System.err.println("Fatal error occurred while parsing configuration: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("Server is now running...");
		Thread rmi = new Thread(new RMITask(configuration));
		rmi.start();
		
	}
}