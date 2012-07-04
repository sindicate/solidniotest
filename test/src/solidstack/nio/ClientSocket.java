package solidstack.nio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import solidstack.httpserver.FatalSocketException;
import solidstack.io.FatalIOException;
import solidstack.lang.Assert;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
public class ClientSocket extends Socket implements Runnable
{
	static final private int PIPELINE = 2;

	private NIOClient client;

	private AtomicBoolean running = new AtomicBoolean();

	private boolean writing;
	LinkedList<RequestWriter> writerQueue = new LinkedList<RequestWriter>();
	boolean queueRunning;

	LinkedList<ResponseReader> readerQueue = new LinkedList<ResponseReader>();

	private int active;

	public ClientSocket( SocketMachine machine )
	{
		super( machine );
	}

	public void setClient( NIOClient client )
	{
		this.client = client;
	}

	synchronized public void request( RequestWriter request )
	{
		this.active ++;

		if( this.queueRunning )
		{
			this.writerQueue.addLast( request );
			return;
		}
		this.queueRunning = true;

		RequestWriter w;
		if( this.writerQueue.isEmpty() )
			w = request;
		else
		{
			this.writerQueue.addLast( request );
			w = this.writerQueue.removeFirst();
		}
		final RequestWriter firstWriter = w;
		getMachine().execute( new Runnable()
		{
			@Override
			public void run()
			{
				Loggers.nio.trace( "Channel ({}) Started request queue", getDebugId() );
				boolean complete = false;
				try
				{
					RequestWriter writer = firstWriter;
					while( true )
					{
						ResponseOutputStream out = new ResponseOutputStream( getOutputStream() );
						Loggers.nio.trace( "Channel ({}) Writing request", getDebugId() );
						ResponseReader reader = writer.write( out );
						synchronized( ClientSocket.this )
						{
							ClientSocket.this.readerQueue.add( reader );
						}
						try
						{
							out.close(); // Need close() for the chunkedoutputstream
						}
						catch( IOException e )
						{
							throw new FatalIOException( e );
						}
						synchronized( ClientSocket.this )
						{
							writer = ClientSocket.this.writerQueue.pollFirst();
							if( writer == null )
							{
								complete = true;
								try
								{
									getOutputStream().flush(); // TODO Is this ok?
								}
								catch( IOException e )
								{
									throw new FatalIOException( e );
								}
								ClientSocket.this.queueRunning = false;
								return;
							}
						}
					}
				}
				finally
				{
					if( !complete )
						close(); // TODO What about synchronized?
					Loggers.nio.trace( "Channel ({}) Ended request queue", getDebugId() );
				}
			}
		} );
	}

//	protected ResponseReader getReader()
//	{
//		Loggers.nio.trace( "Channel ({}) Get reader", getDebugId() );
//		return this.readerQueue.getFirst();
//	}

//	synchronized public void acquireWriteRead( ResponseReader reader )
//	{
//		Assert.isFalse( this.writing );
////		Assert.isFalse( this.reading );
//		this.writing = true;
//		acquireRead( reader );
//	}

	synchronized public void acquireWrite()
	{
		Assert.isFalse( this.writing );
		this.writing = true;
	}

//	public void releaseWrite()
//	{
//		boolean returnToPool;
//		synchronized( this )
//		{
//			Assert.isTrue( this.writing );
//			this.writing = false;
//			returnToPool = this.readerQueue.size() < PIPELINE;
//		}
//		if( returnToPool )
//			returnToPool();
//	}

//	synchronized public void acquireRead( ResponseReader reader )
//	{
////		Assert.isFalse( this.reading );
//		this.readerQueue.addLast( reader );
//	}

//	public void releaseRead( ResponseReader reader )
//	{
//		boolean returnToPool;
//		synchronized( this )
//		{
////			Assert.isTrue( this.reading );
//			returnToPool = !this.writing && this.readerQueue.size() == PIPELINE; // TODO Is this ok?
//			Assert.isTrue( this.readerQueue.removeFirst() == reader );
//		}
//		if( returnToPool )
//			returnToPool();
//	}

	protected boolean isRunningAndSet()
	{
		return !this.running.compareAndSet( false, true );
	}

	protected void endOfRunning()
	{
		this.running.set( false );
	}

	@Override
	void readReady()
	{
		// Not running -> not waiting -> no notify needed
		if( !isRunningAndSet() )
		{
			getMachine().execute( this ); // TODO Also for write
			Loggers.nio.trace( "Channel ({}) Started thread", getDebugId() );
			return;
		}

		super.readReady();
	}

	@Override
	public void close()
	{
		super.close();
		this.client.channelClosed( this );
	}

	void lost()
	{
		super.close();
		this.client.channelLost( this );
	}

	void poolTimeout()
	{
		Loggers.nio.trace( "Channel ({}) PoolTimeout", getDebugId() );
		super.close();
	}

	// TODO Make this package private
	public void timeout()
	{
		Loggers.nio.trace( "Channel ({}) Timeout", getDebugId() );
		close();
	}

//	void returnToPool()
//	{
//		this.client.releaseSocket( this );
//		// TODO Add listenRead to the superclass
//		listenRead(); // TODO The socket needs to be reading, otherwise client disconnects do not come through
//	}

	@Override
	public void run()
	{
		boolean complete = false;
		try
		{
			Loggers.nio.trace( "Channel ({}) Input task started", getDebugId() );

			SocketInputStream in = getInputStream();
			try
			{
				if( in.endOfFile() )
				{
					Loggers.nio.debug( "Connection closed" );
					return;
				}
			}
			catch( FatalSocketException e )
			{
				Loggers.nio.debug( "Connection forcibly closed" );
				return;
			}

			while( true )
			{
				ResponseReader reader;
				synchronized( this )
				{
					reader = this.readerQueue.removeFirst();
				}
				reader.incoming( this );
				this.active --;

				if( !isOpen() )
					return;
				if( getInputStream().available() == 0 )
				{
					complete = true;
					return;
				}

				Loggers.nio.trace( "Channel ({}) Continue reading", getDebugId() );
			}
		}
		catch( Exception e )
		{
			Loggers.nio.debug( "Channel ({}) Unhandled exception", getDebugId(), e );
		}
		finally
		{
			endOfRunning();
			if( !complete )
			{
				close();
				Loggers.nio.trace( "Channel ({}) Input task aborted", getDebugId() );
			}
			else
			{
				listenRead(); // TODO The socket needs to be reading, otherwise client disconnects do not come through
				Loggers.nio.trace( "Channel ({}) Input task complete", getDebugId() );
			}
		}
	}
}
