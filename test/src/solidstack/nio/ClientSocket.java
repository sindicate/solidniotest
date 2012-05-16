package solidstack.nio;



public class ClientSocket
{
	private SocketMachine machine;

	private String hostname;
	private int port;

	private int maxConnections;
	private SocketPool pool = new SocketPool();


	public ClientSocket( String hostname, int port, SocketMachine machine )
	{
		this.hostname = hostname;
		this.port = port;
		this.machine = machine;

		machine.registerSocketPool( this.pool ); // TODO Need Client.close() which removes this pool from the dispatcher
	}

	public void setMaxConnections( int maxConnections )
	{
		this.maxConnections = maxConnections;
	}

	public int[] getSocketCount()
	{
		return this.pool.getSocketCount();
	}

	public Socket getSocket()
	{
		Socket socket = this.pool.getSocket();
		if( socket == null )
		{
			// TODO Maybe the pool should make the connections
			// TODO Maybe we need a queue and the pool executes the queue when a connection is released
			if( this.pool.total() >= this.maxConnections )
			{
//				throw new TooManyConnectionsException();
				socket = this.pool.waitForSocket();
			}
			else
			{
				socket = this.machine.connect( this.hostname, this.port );
				this.pool.addSocket( socket );
			}
		}
		return socket;
	}
}
