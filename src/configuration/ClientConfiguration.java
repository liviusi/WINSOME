package configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ClientConfiguration
{
	public ClientConfiguration(final File config) throws NullPointerException, IOException, FileNotFoundException
	{
		if (config == null) throw new NullPointerException("Configuration file cannot be null.");

		final Properties properties = new Properties();
		final FileInputStream fis = new FileInputStream(config);
		properties.load(fis);
		fis.close();
	}
}
