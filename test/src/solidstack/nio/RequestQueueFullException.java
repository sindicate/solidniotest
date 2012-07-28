package solidstack.nio;

public class RequestQueueFullException extends RuntimeException
{
	public RequestQueueFullException()
	{
		super();
	}

	public RequestQueueFullException( String message )
	{
		super( message );
	}

	@Override
	public synchronized Throwable fillInStackTrace()
	{
		return this; // TODO Temporary
	}
}
