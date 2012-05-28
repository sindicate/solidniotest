package solidstack.nio;

public class TooManyConnectionsException extends RuntimeException
{
	public TooManyConnectionsException()
	{
		super();
	}

	public TooManyConnectionsException( String message )
	{
		super( message );
	}
}
