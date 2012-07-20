package solidstack.nio;

import java.net.ConnectException;
import java.util.LinkedList;

import solidstack.lang.ThreadInterrupted;



public class NIOClient
{
	SocketMachine machine;

	String hostname;
	int port;

	int maxConnections = 100;
	private int maxQueueSize = 100000;
	int maxWindowSize = 1000000;

	// Synchronized together
	SocketPool pool = new SocketPool();
	private LinkedList<RequestWriter> queue = new LinkedList<RequestWriter>();

	private ConnectingThread thread;

	public NIOClient( String hostname, int port, SocketMachine machine )
	{
		this.hostname = hostname;
		this.port = port;
		this.machine = machine;

		machine.registerClientSocket( this ); // TODO Need Client.close() which removes this pool from the dispatcher

		this.thread = new ConnectingThread();
		this.thread.start();
	}

	public void setMaxConnections( int maxConnections )
	{
		this.maxConnections = maxConnections;
	}

	public void setMaxWindowSize( int windowSize )
	{
		this.maxWindowSize = windowSize;
	}

	public int[] getCounts()
	{
		synchronized( this.pool )
		{
			int[] pooled = this.pool.getCounts();
			int queued = this.queue.size();
			return new int[] { pooled[ 0 ], pooled[ 1 ], pooled[ 2 ], queued };
		}
	}

	public void channelClosed( ClientSocket socket )
	{
		synchronized( this.pool )
		{
			this.pool.remove( socket );
		}
	}

	public void channelLost( ClientSocket socket )
	{
		synchronized( this.pool )
		{
			this.pool.remove( socket );
		}
	}

	public boolean request( RequestWriter writer )
	{
		Loggers.nio.trace( "Request" );

		ClientSocket socket;
		synchronized( this.pool )
		{
			socket = this.pool.acquire();
			if( socket == null )
			{
				if( this.queue.size() >= this.maxQueueSize )
					throw new TooManyConnectionsException( "Queue is full" );
				this.queue.add( writer );
			}
		}

		if( socket != null )
		{
			socket.request( writer );
			return true;
		}

		Loggers.nio.trace( "Request added to queue" );

		// TODO Maybe the pool should make the connections
		// TODO Maybe we need a queue and the pool executes the queue when a connection is released
		// FIXME This if should be synchronized

		return false;
	}

	public void timeout()
	{
		synchronized( this.pool )
		{
			this.pool.timeout();
		}
	}

	// TODO There is something wrong here, multiple releases for one socket
	public void release( ClientSocket socket )
	{
		RequestWriter queued;
		synchronized( this.pool )
		{
			queued = this.queue.poll();
			if( queued == null )
			{
				this.pool.release( socket );
				return;
			}
		}
		socket.request( queued );

		while( !socket.windowClosed() )
		{
			synchronized( this.pool )
			{
				queued = this.queue.poll();
				if( queued == null )
					return;
			}
			socket.request( queued );
		}
	}

	// TODO Replace this with a task
	public class ConnectingThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				while( !isInterrupted() )
				{
					int all;
					synchronized( NIOClient.this.pool )
					{
						all = NIOClient.this.pool.all();
					}
					if( all < NIOClient.this.maxConnections )
						try
						{
							ClientSocket socket = NIOClient.this.machine.connect( NIOClient.this.hostname, NIOClient.this.port );
							socket.setClient( NIOClient.this );
							socket.setMaxWindowSize( NIOClient.this.maxWindowSize );
							synchronized( NIOClient.this.pool )
							{
								NIOClient.this.pool.add( socket );
							}
							release( socket ); // TODO Possible to do this outside the synchronized block? Or remove this synchronized block?
//							Loggers.nio.trace( "Added socket, pool size = {}, expand = {}", NIOClient.this.pool.all(), NIOClient.this.expand.get() );
						}
						catch( ConnectException e )
						{
							Loggers.nio.error( e.toString() );
						}
					sleep( 1000 );
				}
			}
			catch( InterruptedException e )
			{
				throw new ThreadInterrupted();
			}
		}
	}
}
