package user;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.google.gson.Gson;

public class Transaction
{
	public final double amount;
	public final String timestamp;

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM. YYYY - HH:mm:ss").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

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

	public static Transaction fromJSON(String jsonString)
	{
		return new Gson().fromJson(jsonString, Transaction.class);
	}
}
