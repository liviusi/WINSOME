package user;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.google.gson.Gson;

public class Transaction
{
	public final double amount;
	public final Instant instant;

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM. YYYY - HH:mm:ss").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

	public Transaction(double amount)
	throws InvalidAmountException
	{
		if (amount <= 0) throw new InvalidAmountException("Negative transactions are not supported.");
		this.amount = amount;
		instant = Instant.now();
	}

	public String toString()
	{
		return String.format("{ \"amount\": \"%f\", \"time\":  \"%s\" }", amount, instant.toString());
	}

	public String toFormattedString()
	{
		return String.format("{ \"amount\": \"%f\", \"timestamp\":  \"%s\" }", amount, FORMATTER.format(instant));
	}

	public static Transaction fromJSON(String jsonString)
	{
		return new Gson().fromJson(jsonString, Transaction.class);
	}
}
