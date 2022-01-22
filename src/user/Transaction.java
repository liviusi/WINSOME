package user;

import java.time.Instant;

import com.google.gson.Gson;

public class Transaction
{
	public final double amount;
	public final Instant instant;

	public Transaction(double amount)
	throws InvalidAmountException
	{
		if (amount <= 0) throw new InvalidAmountException("Negative transactions are not supported.");
		this.amount = amount;
		instant = Instant.now();
	}

	public String toString()
	{
		return String.format("{ \"amount\": \"%d\", \"time\":  \"%s\" }", amount, instant.toString());
	}

	public static Transaction fromJSON(String jsonString)
	{
		return new Gson().fromJson(jsonString, Transaction.class);
	}
}
