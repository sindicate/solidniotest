package solidstack.nio;


public class Timeout
{
	private ResponseReader listener;
	private Socket handler;
	private long when;

	public Timeout( ResponseReader listener, Socket handler, long when )
	{
		this.listener = listener;
		this.handler = handler;
		this.when = when;
	}

	public long getWhen()
	{
		return this.when;
	}

	public ResponseReader getListener()
	{
		return this.listener;
	}

	public Socket getHandler()
	{
		return this.handler;
	}
}
