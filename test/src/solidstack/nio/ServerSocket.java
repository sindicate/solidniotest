package solidstack.nio;

import java.nio.channels.SelectionKey;
import java.util.LinkedList;
import java.util.List;


public class ServerSocket
{
	private SocketMachine machine;
	private SelectionKey key;

	private int maxConnections;
	private List<Socket> all = new LinkedList<Socket>();

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

	// TODO Do we need synchronized?
	public boolean canAccept()
	{
		return this.all.size() < this.maxConnections;
	}

	public void addSocket( Socket socket )
	{
		this.all.add( socket );
		socket.setServerSocket( this );
	}

	int getDebugId()
	{
		return this.debugId;
	}

	public void channelClosed( Socket socket )
	{
//		Assert.isTrue( this.all.remove( socket ) ); TODO Enable
		this.all.remove( socket );
	}
}
