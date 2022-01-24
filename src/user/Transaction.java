package user;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.google.gson.Gson;

/**
 * @brief Utility class used to denote a Transaction made on WINSOME.
 * @author Giacomo Trapani.
 */
public class Transaction
{
	/** Amount of WINCOINS involved in the transaction. */
	public final double amount;
	/** Timestamp as a formatted string. */
	public final String timestamp;

	/** Used for formatting an Instant to a timestamp with a certain pattern. */
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM. YYYY - HH:mm:ss").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

	/**
	 * @brief Constructor for a new Transaction.
	 * @param amount must be greater than zero.
	 * @throws InvalidAmountException if amount is not greater than zero.
	 */
	public Transaction(final double amount)
	throws InvalidAmountException
	{
		if (amount <= 0) throw new InvalidAmountException("Negative transactions are not supported.");
		this.amount = amount;
		this.timestamp = FORMATTER.format(Instant.now());
	}

	public String toString()
	{
		return String.format("{ \"amount\": \"%f\", \"timestamp\":  \"%s\" }", amount, timestamp);
	}

	/** Parses a JSON formatted string to a Transaction. */
	public static Transaction fromJSON(String jsonString)
	{
		return new Gson().fromJson(jsonString, Transaction.class);
	}
}
