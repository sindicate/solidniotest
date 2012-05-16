package solidstack.nio;

import java.nio.channels.SelectionKey;


public class ServerSocket
{
	private SocketMachine machine;
	private SelectionKey key;

	private int maxConnections;
	private SocketPool pool = new SocketPool();
	private ResponseReader reader;

	private int debugId;


	public ServerSocket( SocketMachine machine )
	{
		this.machine = machine;
	}

	void setKey( SelectionKey key )
	{
		this.key = key;
		this.debugId = DebugId.getId( key.channel() );
	}

	public void setReader( ResponseReader reader )
	{
		this.reader = reader;
		this.machine.listenAccept( this.key );
	}

	protected ResponseReader getReader()
	{
		return this.reader;
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

	int getDebugId()
	{
		return this.debugId;
	}
}
