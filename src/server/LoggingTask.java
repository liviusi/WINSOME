package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import configuration.ServerConfiguration;

/**
 * Utility class used to group together the whole logging logic as a single task.
 * @author Giacomo Trapani.
 */
public class LoggingTask implements Runnable
{
	/** Pointer to the queue messages are to be read from. */
	private BlockingQueue<String> messages = null;
	File logFile = null;

	public LoggingTask(BlockingQueue<String> messages, ServerConfiguration configuration)
	{
		this.messages = Objects.requireNonNull(messages, "Queue cannot be null.");;
		this.logFile = new File(Objects.requireNonNull(configuration, "Configuration cannot be null.").logFilename);
	}

	public void run()
	{
		logFile.getParentFile().mkdirs();
		try (final FileWriter fw = new FileWriter(logFile, true); final BufferedWriter writer = new BufferedWriter(fw))
		{
			System.out.println("Logging task is now running!");
			while (!Thread.currentThread().isInterrupted())
			{
				String message = null;
				try
				{
					while ((message = messages.poll(100, TimeUnit.MILLISECONDS)) == null)
						continue;
					// a message has been retrieved
					try { writer.write(message); }
					catch (IOException e)
					{
						System.err.println("Fatal I/O error occurred: now aborting...");
						e.printStackTrace();
						System.exit(1);
					}
				}
				catch (InterruptedException shutdown) { break; }
			}
		}
		catch (IOException e)
		{
			System.err.println("Fatal I/O error occurred: now aborting...");
			e.printStackTrace();
			System.exit(1);
		}
	}
}
