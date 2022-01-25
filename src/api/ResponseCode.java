package api;

/**
 * A ResponseCode is issued by the server after handling a client's request.
 * @author Giacomo Trapani.
 */
public enum ResponseCode
{
	OK(200, "200\r\n"),
	CREATED(201, "201\r\n"),
	BAD_REQUEST(400, "400\r\n"),
	FORBIDDEN(403, "403\r\n"),
	NOT_FOUND(404, "404\r\n");

	/** Numeric value of the code. */
	private int value = -1;
	/** Used to represent the code as a string. */
	private String description = null;

	/**
	 * Private constructor used to initialize a ResponseCode.
	 * @param description Used to describe the code.
	 */
	private ResponseCode(int value, String description)
	{
		this.value = value;
		this.description = description;
	}

	/** 
	 * Getter for the value.
	 * @return code's value.
	*/
	public int getValue()
	{
		return value;
	}

	/**
	 * Getter for the description.
	 * @return code's description.
	*/
	public String getDescription()
	{
		return description;
	}

	/**
	 * Instantiates a ResponseCode given its numerical code.
	 * @param code must be in { 200, 201, 400, 403, 404 }.
	 * @return ResponseCode corresponding to code, null if there are none. <br>
	 * 200 -> OK <br>
	 * 201 -> CREATED <br>
	 * 400 -> BAD REQUEST <br>
	 * 403 -> FORBIDDEN <br>
	 * 404 -> NOT FOUND <br>
	 */
	public static ResponseCode fromCode(int code)
	{
		ResponseCode r = null;
		switch (code)
		{
			case 200:
				r = OK;
				break;

			case 201:
				r = CREATED;
				break;

			case 400:
				r = BAD_REQUEST;
				break;

			case 403:
				r = FORBIDDEN;
				break;

			case 404:
				r = NOT_FOUND;
				break;
	
			default:
				break;
		}
		return r;
	}

}
