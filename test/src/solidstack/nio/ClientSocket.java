package solidstack.nio;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import solidstack.httpserver.FatalSocketException;
import solidstack.lang.Assert;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
public class ClientSocket extends Socket
{
	static final private int PIPELINE = 1;

	private NIOClient clientSocket;

	private AtomicBoolean running = new AtomicBoolean();

	private boolean writing;
	private LinkedList<ResponseReader> readerQueue = new LinkedList<ResponseReader>();

	public ClientSocket( SocketMachine machine )
	{
		super( machine );
	}

	public void setClient( NIOClient client )
	{
		this.clientSocket = client;
	}

	protected ResponseReader getReader()
	{
		return this.readerQueue.getFirst();
	}

	synchronized public void acquireWriteRead( ResponseReader reader )
	{
		Assert.isFalse( this.writing );
//		Assert.isFalse( this.reading );
		this.writing = true;
		acquireRead( reader );
	}

	synchronized public void acquireWrite()
	{
		Assert.isFalse( this.writing );
		this.writing = true;
	}

	public void releaseWrite()
	{
		boolean returnToPool;
		synchronized( this )
		{
			Assert.isTrue( this.writing );
			this.writing = false;
			returnToPool = this.readerQueue.size() < PIPELINE;
		}
		if( returnToPool )
			returnToPool();
	}

	synchronized public void acquireRead( ResponseReader reader )
	{
//		Assert.isFalse( this.reading );
		this.readerQueue.addLast( reader );
	}

	public void releaseRead( ResponseReader reader )
	{
		boolean returnToPool;
		synchronized( this )
		{
//			Assert.isTrue( this.reading );
			returnToPool = !this.writing && this.readerQueue.size() == PIPELINE; // TODO Is this ok?
			Assert.isTrue( this.readerQueue.removeFirst() == reader );
		}
		if( returnToPool )
			returnToPool();
	}

	protected boolean isRunningAndSet()
	{
		return !this.running.compareAndSet( false, true );
	}

	protected void endOfRunning()
	{
		this.running.set( false );
	}

	@Override
	void dataIsReady()
	{
		// Not running -> not waiting -> no notify needed
		if( !isRunningAndSet() )
		{
			getMachine().execute( this ); // TODO Also for write
			Loggers.nio.trace( "Channel ({}) Started thread", getDebugId() );
			return;
		}

		super.dataIsReady();
	}

	@Override
	public void close()
	{
		super.close();
		this.clientSocket.channelClosed( this );
	}

	void lost()
	{
		super.close();
		this.clientSocket.channelLost( this );
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

	void returnToPool()
	{
		this.clientSocket.releaseSocket( this );
		// TODO Add listenRead to the superclass
		getMachine().listenRead( getKey() ); // TODO The socket needs to be reading, otherwise client disconnects do not come through
	}

	@Override
	public void run()
	{
		SocketInputStream in = getInputStream();
		boolean complete = false;
		try
		{
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

			Loggers.nio.trace( "Channel ({}) Task started", getDebugId() );

			while( true )
			{
				ResponseReader reader = getReader();
				complete = false;
				try
				{
					reader.incoming( this );
					complete = true;
				}
				finally
				{
					if( complete )
					{
						releaseRead( reader );
						Loggers.nio.trace( "Channel ({}) Release reader", getDebugId() );
					}
				}

				if( !isOpen() )
					return;
				if( getInputStream().available() == 0 )
					return;

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
				Loggers.nio.trace( "Channel ({}) Thread aborted", getDebugId() );
			}
			else
				Loggers.nio.trace( "Channel ({}) Thread complete", getDebugId() );
		}
	}
}
