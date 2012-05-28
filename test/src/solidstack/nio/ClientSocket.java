package solidstack.nio;

import java.net.ConnectException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import solidstack.lang.Assert;
import solidstack.lang.ThreadInterrupted;



public class ClientSocket
{
	SocketMachine machine;

	String hostname;
	int port;

	private int maxConnections = 100;
	private int maxQueueSize = 10000;

	private List<Socket> pool = new LinkedList<Socket>();
	List<Socket> all = new LinkedList<Socket>();
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

	synchronized public void setMaxConnections( int maxConnections )
	{
		this.maxConnections = maxConnections;
	}

	synchronized public int[] getCounts()
	{
		return new int[] { this.all.size(), this.pool.size(), this.queue.size() };
	}

	// Only called by returnToPool()
	synchronized public void releaseSocket( Socket socket )
	{
		Assert.isTrue( this.all.contains( socket ) );
		if( !this.queue.isEmpty() )
		{
			RequestWriter writer = this.queue.removeFirst();
			request( socket, writer );
		}
		else
			this.pool.add( socket );
//		notify();
	}

	synchronized public void channelClosed( Socket socket )
	{
//		Assert.isTrue( this.pool.remove( handler ) );
		this.all.remove( socket );
		this.pool.remove( socket );
	}

	synchronized public void channelLost( Socket socket )
	{
//		Assert.isFalse( this.all.remove( handler ) );
		this.all.remove( socket );
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

	synchronized public void request( RequestWriter writer ) throws ConnectException
	{
		Socket socket = null;
		if( !this.pool.isEmpty() )
		{
			socket = this.pool.remove( this.pool.size() - 1 );
			Loggers.nio.trace( "Channel ({}) From pool", socket.getDebugId() );
		}
		if( socket == null )
		{
			// TODO Maybe the pool should make the connections
			// TODO Maybe we need a queue and the pool executes the queue when a connection is released
			if( this.all.size() + this.expand.availablePermits() >= this.maxConnections )
			{
				if( this.queue.size() >= this.maxQueueSize )
					throw new TooManyConnectionsException( "Queue is full" );
				this.queue.addLast( writer );
				Loggers.nio.trace( "Request added to queue" );
				return;
//				socket = this.pool.waitForSocket();
			}

			if( this.queue.size() >= this.maxQueueSize )
				throw new TooManyConnectionsException( "Queue is full" );
			this.queue.addLast( writer );
			Loggers.nio.trace( "Request added to queue" );

			this.expand.release();
			return;
		}

		request( socket, writer );
	}

//	synchronized public Socket getSocket() throws ConnectException
//	{
//		Socket socket = null;
//		if( !this.pool.isEmpty() )
//		{
//			socket = this.pool.remove( this.pool.size() - 1 );
//			Loggers.nio.trace( "Channel ({}) From pool", socket.getDebugId() );
//		}
//		if( socket == null )
//		{
//			// TODO Maybe the pool should make the connections
//			// TODO Maybe we need a queue and the pool executes the queue when a connection is released
//			if( this.all.size() >= this.maxConnections )
//			{
//				throw new TooManyConnectionsException();
////				socket = this.pool.waitForSocket();
//			}
//			else
//			{
//				// TODO Connecting synchronously is bad
//				socket = this.machine.connect( this.hostname, this.port );
//				this.all.add( socket );
//				socket.setClientSocket( this );
//			}
//		}
//		return socket;
//	}

	synchronized public void timeout()
	{
		long now = System.currentTimeMillis();
		for( Iterator<Socket> i = this.pool.iterator(); i.hasNext(); )
		{
			Socket socket = i.next();
			if( socket.lastPooled() + 30000 <= now )
			{
				Assert.isTrue( this.all.remove( socket ) );
				socket.poolTimeout();
				i.remove();
			}
		}
	}

//	// TODO This is bad, caller is not synchronized. You get more waiters than there are sockets in the pool
//	synchronized public Socket waitForSocket()
//	{
//		Socket result;
//		do
//		{
//			try
//			{
//				wait();
//			}
//			catch( InterruptedException e )
//			{
//				throw new ThreadInterrupted();
//			}
//			result = getSocket();
//		}
//		while( result == null );
//		return result;
//	}

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
					ClientSocket.this.all.add( socket );
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
