package solidstack.nio;

import java.nio.channels.SelectionKey;
import java.util.LinkedList;
import java.util.List;


public class NIOServer
{
	private SocketMachine machine;
	private SelectionKey key;

	private int maxConnections;
	private List<Socket> all = new LinkedList<Socket>();

	private RequestReader reader;

	private int debugId;


	public NIOServer( SocketMachine machine )
	{
		this.machine = machine;
	}

	void setKey( SelectionKey key )
	{
		this.key = key;
		this.debugId = DebugId.getId( key.channel() );
	}

	public void setReader( RequestReader reader )
	{
		this.reader = reader;
		this.machine.listenAccept( this.key );
	}

	protected RequestReader getReader()
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

	public void addSocket( ServerSocket socket )
	{
		this.all.add( socket );
		socket.setServer( this );
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
