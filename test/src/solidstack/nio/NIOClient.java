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
	int maxQueueAge = 1000;

	List< ClientSocket > idle = new ArrayList<ClientSocket>();
	List< ClientSocket > writeable = new ArrayList<ClientSocket>();
	List< ClientSocket > writing = new ArrayList<ClientSocket>();
	List< ClientSocket > full = new ArrayList<ClientSocket>();

	static private class Entry
	{
		long queued;
		RequestWriter writer;
		public Entry( RequestWriter writer )
		{
			this.writer = writer;
			this.queued = System.currentTimeMillis();
		}
	}
	LinkedList<Entry> queue = new LinkedList<Entry>();

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

	synchronized public int[] getCounts()
	{
		for( ClientSocket socket : this.idle )
			Assert.isTrue( socket.getActive() == 0 );

		// TODO
		int active = this.writeable.size() + this.writing.size() + this.full.size();
		int total = this.idle.size() + active;
//		int active = this.writing.size();
//		int total = this.idle.size() + this.writeable.size() + active + this.full.size();

		int requests = 0;
		for( ClientSocket socket : this.writeable )
			requests += socket.getActive();
		for( ClientSocket socket : this.writing )
			requests += socket.getActive();
		for( ClientSocket socket : this.full )
			requests += socket.getActive();

		return new int[] { total, active, requests, this.queue.size() };
	}

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
		// TODO
		//Assert.isTrue( this.idle.remove( socket ) || this.writeable.remove( socket ) || this.writing.remove( socket ) || this.full.remove( socket ) );
		this.idle.remove( socket );
		this.writeable.remove( socket );
		this.writing.remove( socket );
		this.full.remove( socket );
	}

	synchronized public void socketGotAir( ClientSocket socket )
	{
		Assert.isTrue( this.full.remove( socket ) );
//		this.writeable.add( socket );
		this.writing.add( socket );
		socket.asyncProcessWriteQueue();
	}

	synchronized public void socketFinished( ClientSocket socket )
	{
		if( this.writeable.remove( socket ) )
			this.idle.add( socket );
		else
			Assert.isTrue( this.writing.contains( socket ) );
	}

	public void request( RequestWriter writer )
	{
		Loggers.nio.trace( "Request" );

		// Add to queue
		synchronized( this )
		{
			if( this.queue.size() >= this.maxQueueSize )
				throw new RequestQueueFullException();
			this.queue.add( new Entry( writer ) );

			if( this.writing.size() > 0 ) // FIXME But what if the writing socket already decided to end?
				return;

			start();
		}
	}

	void start()
	{
		if( this.writeable.size() > 0 )
		{
			ClientSocket socket = this.writeable.remove( 0 );
			this.writing.add( socket );
			socket.asyncProcessWriteQueue();
		}
		else if( this.idle.size() > 0 )
		{
			ClientSocket socket = this.idle.remove( 0 );
			this.writing.add( socket );
			socket.asyncProcessWriteQueue();
		}
	}

	synchronized public RequestWriter popRequest()
	{
		Entry result = this.queue.pollFirst();
		return result != null ? result.writer : null;
	}

	public void timeout()
	{
//		synchronized( this.pool )
//		{
//			this.pool.timeout();
//		}
	}

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
					long age;
					synchronized( NIOClient.this )
					{
						all = NIOClient.this.idle.size() + NIOClient.this.writeable.size() + NIOClient.this.writing.size() + NIOClient.this.full.size();
						Entry entry = NIOClient.this.queue.peekFirst();
						age = entry != null ? entry.queued : 0;
						if( NIOClient.this.queue.size() > 0 && NIOClient.this.writing.size() == 0 )
							NIOClient.this.start();
					}
					if( all < NIOClient.this.maxConnections )
					{
						if( all == 0 || age != 0 && age + NIOClient.this.maxQueueAge < System.currentTimeMillis() )
						{
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
//								release( socket ); // TODO Possible to do this outside the synchronized block? Or remove this synchronized block?
//								Loggers.nio.trace( "Added socket, pool size = {}, expand = {}", NIOClient.this.pool.all(), NIOClient.this.expand.get() );
							}
							catch( ConnectException e )
							{
								Loggers.nio.error( e.toString() );
							}
						}
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
