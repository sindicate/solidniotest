package solidstack.nio;

import java.net.ConnectException;
import java.util.LinkedList;
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
	AtomicInteger expand = new AtomicInteger();

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
		int[] pooled = this.pool.getCounts();
		int queued;
		synchronized( this.queue )
		{
			queued = this.queue.size();
		}
		return new int[] { pooled[ 0 ], pooled[ 1 ], pooled[ 2 ], queued };
	}

	// TODO Implement assertions that checks for nesting
	public void processQueue()
	{
		RequestWriter queued = null;
		synchronized( this.queue )
		{
			queued = this.queue.pollFirst();
		}
		if( queued != null )
		{
			Loggers.nio.trace( "Processing request queue" );
			while( queued != null )
			{
				if( !retry( queued ) )
				{
					synchronized( this.queue )
					{
						this.queue.addFirst( queued );
					}
					Loggers.nio.trace( "Request re-added to queue" );
					return;
				}

				synchronized( this.queue )
				{
					queued = this.queue.pollFirst();
				}
			}
			Loggers.nio.trace( "End processing request queue" );
		}
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
			this.pool.release( socket );
			processQueue();
			return true;
		}

		synchronized( this.queue )
		{
			if( this.queue.size() >= this.maxQueueSize )
				throw new TooManyConnectionsException( "Queue is full" );
			this.queue.addLast( writer );
		}
		Loggers.nio.trace( "Request added to queue" );

		// TODO Maybe the pool should make the connections
		// TODO Maybe we need a queue and the pool executes the queue when a connection is released
		// FIXME This if should be synchronized

		synchronized( this.expand )
		{
			if( this.pool.all() + this.expand.get() < this.maxConnections )
			{
//				Loggers.nio.trace( "Adding socket, pool size = {}, expand = {}", this.pool.all(), this.expand.get() );
				int val = this.expand.incrementAndGet();
				if( val == 1 )
					this.expand.notify();
			}
		}

		return false;
	}

	private boolean retry( RequestWriter writer )
	{
		ClientSocket socket = this.pool.acquire();
		if( socket == null )
			return false;

		socket.request( writer );
		this.pool.release( socket );
		return true;
	}

	public void timeout()
	{
		this.pool.timeout();
	}

	public void release( ClientSocket socket )
	{
		this.pool.release( socket );
		processQueue();
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
					synchronized( NIOClient.this.expand )
					{
						if( NIOClient.this.expand.get() == 0 )
							NIOClient.this.expand.wait();
					}
					if( NIOClient.this.expand.get() > 0 ) // Check again for spurious notifies
					{
						try
						{
							ClientSocket socket = NIOClient.this.machine.connect( NIOClient.this.hostname, NIOClient.this.port );
							socket.setClient( NIOClient.this );
							socket.setMaxWindowSize( NIOClient.this.maxWindowSize );
							synchronized( NIOClient.this.expand )
							{
								NIOClient.this.pool.add( socket );
								NIOClient.this.expand.decrementAndGet();
//								Loggers.nio.trace( "Added socket, pool size = {}, expand = {}", NIOClient.this.pool.all(), NIOClient.this.expand.get() );
							}
							processQueue();
						}
						catch( ConnectException e )
						{
							NIOClient.this.expand.set( 0 );
							Loggers.nio.error( e.toString() );
						}
					}
				}
			}
			catch( InterruptedException e )
			{
				throw new ThreadInterrupted();
			}
		}
	}
}
