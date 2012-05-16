package solidstack.nio;


public class ServerSocket extends Socket
{
	private int maxConnections;
	private SocketPool pool = new SocketPool();

	public ServerSocket( SocketMachine machine )
	{
		super( true, machine );
	}

	@Override
	public void setReader( ResponseReader reader )
	{
		super.setReader( reader );
		getMachine().listenAccept( getKey() );
	}

	public void setMaxConnections( int maxConnections )
	{
		this.maxConnections = maxConnections;
	}

	public boolean canAccept()
	{
		return this.pool.total() < this.maxConnections;
	}

	public void addSocket( Socket socket )
	{
		this.pool.addSocket( socket );
	}
}
