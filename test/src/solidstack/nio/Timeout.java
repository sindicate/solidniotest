package solidstack.nio;


public class Timeout
{
	private ResponseReader listener;
	private ClientSocket socket;
	private long when;

	public Timeout( ResponseReader listener, ClientSocket handler, long when )
	{
		this.listener = listener;
		this.socket = handler;
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

	public ClientSocket getSocket()
	{
		return this.socket;
	}
}
