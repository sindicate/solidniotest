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

	@Override
	public synchronized Throwable fillInStackTrace()
	{
		return this;
	}
}
