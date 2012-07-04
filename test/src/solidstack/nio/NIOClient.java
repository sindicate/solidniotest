package solidstack.nio;

import java.net.ConnectException;
import java.util.LinkedList;

import solidstack.lang.ThreadInterrupted;



public class NIOClient
{
	SocketMachine machine;

	String hostname;
	int port;

	private int maxConnections = 100;
	private int maxQueueSize = 10000;

	SocketPool pool = new SocketPool();
	Integer expand = new Integer( 0 );

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

	public int[] getCounts()
	{
		int[] pooled = this.pool.getCounts();
		int queued;
		synchronized( this.queue )
		{
			queued = this.queue.size();
		}
		return new int[] { pooled[ 0 ], pooled[ 1 ], queued };
	}

	public void releaseSocket( ClientSocket socket )
	{
		this.pool.release( socket );

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
				if( !request( queued, true ) )
					return;

				synchronized( this.queue )
				{
					queued = this.queue.pollFirst();
				}
			}
			Loggers.nio.trace( "End processing request queue" );
		}
	}

	public void channelClosed( Socket socket )
	{
		this.pool.remove( socket );
	}

	public void channelLost( Socket socket )
	{
		this.pool.remove( socket );
	}

	public void request( RequestWriter writer ) throws ConnectException
	{
		Loggers.nio.trace( "Request received" );
		request( writer, false );
	}

	private boolean request( RequestWriter writer, boolean existing )
	{
		ClientSocket socket = this.pool.acquire();
		if( socket != null )
		{
			socket.request( writer );
			return true;
		}

		if( existing )
		{
			synchronized( this.queue )
			{
				this.queue.addFirst( writer );
			}
			return false;
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
			if( this.pool.size() + this.expand < this.maxConnections )
			{
				if( this.expand == 0 )
					this.expand.notify();
				this.expand ++;
			}
		}

		return false;
	}

	public void timeout()
	{
		this.pool.timeout();
	}

	public class ConnectingThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				while( !isInterrupted() )
				{
					int expand;
					synchronized( NIOClient.this.expand )
					{
						if( NIOClient.this.expand == 0 )
							NIOClient.this.expand.wait();
						expand = NIOClient.this.expand;
					}
					if( expand > 0 ) // Check again for spurious notifies
					{
						try
						{
							ClientSocket socket = NIOClient.this.machine.connect( NIOClient.this.hostname, NIOClient.this.port );
							synchronized( NIOClient.this.expand )
							{
								NIOClient.this.pool.add( socket );
								NIOClient.this.expand --;
							}
							socket.setClient( NIOClient.this );
							releaseSocket( socket );
						}
						catch( ConnectException e )
						{
							synchronized( NIOClient.this.expand )
							{
								NIOClient.this.expand = 0;
							}
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
