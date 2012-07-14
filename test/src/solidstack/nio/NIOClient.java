package solidstack.nio;

import java.net.ConnectException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import solidstack.lang.ThreadInterrupted;



public class NIOClient
{
	SocketMachine machine;

	String hostname;
	int port;

	private int maxConnections = 100;
	private int maxQueueSize = 10000;
	int maxWindowSize = 1000000;

	SocketPool pool = new SocketPool();

	private ConcurrentLinkedQueue<RequestWriter> queue = new ConcurrentLinkedQueue<RequestWriter>();
	private AtomicInteger queueSize = new AtomicInteger(); // queue.size() itself is bad

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
		int[] pooled = this.pool.getCounts();
		int queued = this.queueSize.get();
		return new int[] { pooled[ 0 ], pooled[ 1 ], pooled[ 2 ], queued };
	}

	public void channelClosed( ClientSocket socket )
	{
		this.pool.remove( socket );
	}

	public void channelLost( ClientSocket socket )
	{
		this.pool.remove( socket );
	}

	public boolean request( RequestWriter writer )
	{
		Loggers.nio.trace( "Request" );
		ClientSocket socket = this.pool.acquire();
		if( socket != null )
		{
			socket.request( writer );
			return true;
		}

		if( this.queueSize.get() >= this.maxQueueSize )
			throw new TooManyConnectionsException( "Queue is full" );
		this.queue.add( writer );
		this.queueSize.incrementAndGet();
		Loggers.nio.trace( "Request added to queue" );

		// TODO Maybe the pool should make the connections
		// TODO Maybe we need a queue and the pool executes the queue when a connection is released
		// FIXME This if should be synchronized

		return false;
	}

	public void timeout()
	{
		this.pool.timeout();
	}

	public void release( ClientSocket socket )
	{
		boolean release = true;
		while( !socket.windowClosed() )
		{
			RequestWriter queued = this.queue.poll();
			if( queued == null )
				break;

			release = false;
			this.queueSize.decrementAndGet();
			socket.request( queued );
		}
		if( release )
			this.pool.release( socket );
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
					if( NIOClient.this.pool.all() < NIOClient.this.maxConnections )
						try
						{
							ClientSocket socket = NIOClient.this.machine.connect( NIOClient.this.hostname, NIOClient.this.port );
							socket.setClient( NIOClient.this );
							socket.setMaxWindowSize( NIOClient.this.maxWindowSize );
							NIOClient.this.pool.add( socket );
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
