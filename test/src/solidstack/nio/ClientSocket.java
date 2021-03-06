package solidstack.nio;

import java.net.ConnectException;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import solidstack.lang.ThreadInterrupted;



public class ClientSocket
{
	SocketMachine machine;

	String hostname;
	int port;

	private int maxConnections = 100;
	private int maxQueueSize = 10000;

	private SocketPool pool = new SocketPool();
	Semaphore expand = new Semaphore( 0 );

	private LinkedList<RequestWriter> queue = new LinkedList<RequestWriter>();
	private ConnectingThread thread;

	public ClientSocket( String hostname, int port, SocketMachine machine )
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

	// Only called by returnToPool()
	public void releaseSocket( Socket socket )
	{
		RequestWriter writer = null;
		synchronized( this.queue )
		{
			if( !this.queue.isEmpty() )
				writer = this.queue.removeFirst();
		}
		if( writer != null )
			request( socket, writer );
		else
			this.pool.release( socket );
	}

	public void channelClosed( Socket socket )
	{
		this.pool.remove( socket );
	}

	public void channelLost( Socket socket )
	{
		this.pool.remove( socket );
	}

	private void request( Socket socket, RequestWriter writer )
	{
		socket.doubleAcquire(); // Need 2 releases: this request and the received response
		boolean complete = false;
		try
		{
			writer.write( socket );
			complete = true;
		}
		finally
		{
			if( complete )
				socket.release();
			else
				socket.close();
		}
	}

	public void request( RequestWriter writer ) throws ConnectException
	{
		Socket socket = this.pool.acquire();
		if( socket != null )
			Loggers.nio.trace( "Channel ({}) From pool", socket.getDebugId() );
		else
		{
			// TODO Maybe the pool should make the connections
			// TODO Maybe we need a queue and the pool executes the queue when a connection is released
			// FIXME This if should be synchronized
			if( this.pool.size() + this.expand.availablePermits() >= this.maxConnections )
			{
				synchronized( this.queue )
				{
					if( this.queue.size() >= this.maxQueueSize )
						throw new TooManyConnectionsException( "Queue is full" );
					this.queue.addLast( writer );
				}
				Loggers.nio.trace( "Request added to queue" );
				return;
//				socket = this.pool.waitForSocket();
			}

			synchronized( this.queue )
			{
				if( this.queue.size() >= this.maxQueueSize )
					throw new TooManyConnectionsException( "Queue is full" );
				this.queue.addLast( writer );
			}
			Loggers.nio.trace( "Request added to queue" );

			this.expand.release();
			return;
		}

		request( socket, writer );
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
			while( !isInterrupted() )
			{
				try
				{
					ClientSocket.this.expand.acquire();
				}
				catch( InterruptedException e1 )
				{
					throw new ThreadInterrupted();
				}
				try
				{
					Socket socket = ClientSocket.this.machine.connect( ClientSocket.this.hostname, ClientSocket.this.port );
					ClientSocket.this.pool.add( socket );
					socket.setClientSocket( ClientSocket.this );
					releaseSocket( socket );
				}
				catch( ConnectException e )
				{
					ClientSocket.this.expand.drainPermits();
					Loggers.nio.error( e.toString() );
				}
			}
		}
	}
}
