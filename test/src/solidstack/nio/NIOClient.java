package solidstack.nio;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import solidstack.lang.Assert;
import solidstack.lang.ThreadInterrupted;



public class NIOClient
{
	SocketMachine machine;

	String hostname;
	int port;

	int maxConnections = 100;
	private int maxQueueSize = 100000;
	int maxWindowSize = 1000000;

	List< ClientSocket > writeable = new ArrayList<ClientSocket>();
	List< ClientSocket > writing = new ArrayList<ClientSocket>();
	List< ClientSocket > full = new ArrayList<ClientSocket>();

	// Synchronized together
//	SocketPool pool = new SocketPool();
	private LinkedList<RequestWriter> queue = new LinkedList<RequestWriter>();

	private ConnectingThread thread;

//	private boolean running;

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
//		synchronized( this.pool )
//		{
//			int[] pooled = this.pool.getCounts();
//			int queued = this.queue.size();
//		return new int[] { pooled[ 0 ], pooled[ 1 ], pooled[ 2 ], queued };
//		}
		return new int[] { 0, 0, 0, 0 };
	}

//	public void channelClosed( ClientSocket socket )
//	{
//		synchronized( this.pool )
//		{
//			this.pool.remove( socket );
//		}
//	}
//
//	public void channelLost( ClientSocket socket )
//	{
//		synchronized( this.pool )
//		{
//			this.pool.remove( socket );
//		}
//	}

	synchronized public void socketWriteComplete( ClientSocket socket )
	{
		Assert.isTrue( this.writing.remove( socket ) );
		this.writeable.add( socket );
	}

	synchronized public void socketWriteFull( ClientSocket socket )
	{
		Assert.isTrue( this.writing.remove( socket ) );
		this.full.add( socket );
	}

	synchronized public void socketWriteError( ClientSocket socket )
	{
		Assert.isTrue( this.writing.remove( socket ) );
		socket.doClose();
	}

	synchronized public void socketClosed( ClientSocket socket )
	{
		Assert.isTrue( this.writeable.remove( socket ) || this.writing.remove( socket ) || this.full.remove( socket ) );
	}

	synchronized public void socketGotAir( ClientSocket socket )
	{
		Assert.isTrue( this.full.remove( socket ) );

		this.writing.add( socket );
		socket.asyncProcessWriteQueue();
	}

	public void request( RequestWriter writer )
	{
		Loggers.nio.trace( "Request" );

		// Add to queue
		synchronized( this )
		{
			if( this.queue.size() >= this.maxQueueSize )
				throw new TooManyConnectionsException( "Queue is full" );
			this.queue.add( writer );

			if( this.writing.size() > 0 )
				return;

			if( this.writeable.size() > 0 )
			{
				ClientSocket socket = this.writeable.remove( 0 );
				this.writing.add( socket );
				socket.asyncProcessWriteQueue();
			}
		}
	}

	synchronized public RequestWriter popRequest()
	{
		return this.queue.pollFirst();
	}

	public void timeout()
	{
//		synchronized( this.pool )
//		{
//			this.pool.timeout();
//		}
	}

//	// TODO There is something wrong here, multiple releases for one socket
//	public void release( ClientSocket socket )
//	{
//		RequestWriter queued;
//		synchronized( this.pool )
//		{
//			queued = this.queue.poll();
//			if( queued == null )
//			{
//				this.pool.release( socket );
//				return;
//			}
//		}
//		socket.request( queued );
//
//		while( !socket.windowClosed() )
//		{
//			synchronized( this.pool )
//			{
//				queued = this.queue.poll();
//				if( queued == null )
//					return;
//			}
//			socket.request( queued );
//		}
//	}

	// TODO Replace this with a task
	public class ConnectingThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				Loggers.nio.debug( "Connecting thread started" );

				while( !isInterrupted() )
				{
					int all;
					synchronized( NIOClient.this )
					{
						all = NIOClient.this.writeable.size() + NIOClient.this.writing.size() + NIOClient.this.full.size();
					}
					if( all < NIOClient.this.maxConnections )
						try
						{
							Loggers.nio.debug( "Connecting..." );
							ClientSocket socket = NIOClient.this.machine.connect( NIOClient.this.hostname, NIOClient.this.port );
							Loggers.nio.debug( "New socket connected" );
							socket.setClient( NIOClient.this );
							socket.setMaxWindowSize( NIOClient.this.maxWindowSize );
							synchronized( NIOClient.this )
							{
								NIOClient.this.writing.add( socket );
							}
							socket.asyncProcessWriteQueue();
//							release( socket ); // TODO Possible to do this outside the synchronized block? Or remove this synchronized block?
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
			finally
			{
				Loggers.nio.debug( "Connecting thread stopped" );
			}
		}
	}
}
