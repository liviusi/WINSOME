package api;

public enum ResponseCode
{

	OK("200\r\n"),
	CREATED("201\r\n"),
	BAD_REQUEST("400\r\n"),
	FORBIDDEN("403\r\n"),
	NOT_FOUND("404\r\n");

	private String description = null;
	

	private ResponseCode(String description)
	{
		this.description = description;
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
