package api;

public enum ResponseCode
{

	OK(200, "200 OK!\r\n"),
	CREATED(201, "201 Created!\r\n"),
	BAD_REQUEST(400, "400 Bad request!\r\n"),
	FORBIDDEN(403, "403 Forbidden!\r\n"),
	NOT_FOUND(404, "404 Not found!\r\n");

	private int value = -1;
	private String description = null;
	

	private ResponseCode(int value, String description)
	{
		this.value = value;
		this.description = description;
	}

	public int getValue()
	{
		return value;
	}

	public String getDescription()
	{
		return description;
	}

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
