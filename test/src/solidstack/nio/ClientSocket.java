package solidstack.nio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import solidstack.httpserver.FatalSocketException;
import solidstack.io.FatalIOException;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
public class ClientSocket extends Socket implements Runnable
{
	NIOClient client;
	int maxWindowSize = 4;

	final private AtomicBoolean running = new AtomicBoolean();

	LinkedList<ResponseReader> readerQueue = new LinkedList<ResponseReader>();

	AtomicInteger active = new AtomicInteger();



	public ClientSocket( SocketMachine machine )
	{
		super( machine );
	}

	public void setClient( NIOClient client )
	{
		this.client = client;
	}

	public void setMaxWindowSize( int windowSize )
	{
		this.maxWindowSize = windowSize;
	}

	public int getActive()
	{
		return this.active.get();
	}

	public boolean isActive()
	{
		return getActive() > 0;
	}

	public void asyncProcessWriteQueue()
	{
		getMachine().execute( new Runnable()
		{
			@Override
			public void run()
			{
				Loggers.nio.trace( "Channel ({}) Started request queue", getDebugId() );
				boolean complete = false;
				try
				{
					while( getActive() < ClientSocket.this.maxWindowSize )
					{
						RequestWriter writer;
						synchronized( ClientSocket.this )
						{
							writer = ClientSocket.this.client.popRequest();
							if( writer == null )
							{
								try
								{
									getOutputStream().flush(); // TODO Is this ok?
								}
								catch( IOException e )
								{
									throw new FatalIOException( e );
								}
								ClientSocket.this.client.socketWriteComplete( ClientSocket.this );
								complete = true;
								return;
							}
						}
						ClientSocket.this.active.incrementAndGet();
						ResponseOutputStream out = new ResponseOutputStream( getOutputStream() );
						Loggers.nio.trace( "Channel ({}) Writing request", getDebugId() );
						ResponseReader reader = writer.write( out );
						synchronized( ClientSocket.this )
						{
							ClientSocket.this.readerQueue.add( reader ); // FIXME This may be too late, response may have come back already
						}
						try
						{
							out.close(); // Need close() for the chunkedoutputstream
						}
						catch( IOException e )
						{
							throw new FatalIOException( e );
						}
					}
					ClientSocket.this.client.socketWriteFull( ClientSocket.this ); // FIXME This is too late, responses may have come back already
					complete = true;
				}
				finally
				{
					if( !complete )
						ClientSocket.this.client.socketWriteError( ClientSocket.this );
					Loggers.nio.trace( "Channel ({}) Ended request queue", getDebugId() );
				}
			}
		} );
	}

	public int windowLeft()
	{
		return this.maxWindowSize - this.active.get();
	}

	public boolean windowClosed()
	{
		return this.active.get() >= this.maxWindowSize;
	}

	@Override
	void readReady()
	{
		boolean run = false;
		synchronized( this.running )
		{
			Loggers.nio.trace( "Channel ({}) readReady: Running {} = {}", new Object[] { getDebugId(), DebugId.getId( this.running ), this.running.get() } );
			dontListenRead();
			if( !this.running.get() )
			{
				this.running.set( true );
				run = true;
			}
		}
		if( run )
		{
			// Not running -> not waiting -> no notify needed
			getMachine().execute( this ); // TODO Also for write
			Loggers.nio.trace( "Channel ({}) Started input task", getDebugId() );
			return;
		}
		super.readReady();
	}

	@Override
	public void close()
	{
		super.close();
		this.client.socketClosed( this );
	}

//	void lost()
//	{
//		super.close();
//		this.client.channelLost( this );
//	}

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
				int val = this.active.decrementAndGet();
				if( val + 1 == this.maxWindowSize )
					this.client.socketGotAir( this );
				else if( val == 0 )
					this.client.socketFinished( this );

				if( !isOpen() )
					return;

				synchronized( this.running )
				{
//					Loggers.nio.trace( "Channel ({}) run: Running {} = {}", new Object[] { getDebugId(), DebugId.getId( this.running ), this.running.get() } );
					if( getInputStream().available() == 0 /* && this.coordinator.stop() */ )
					{
						this.running.set( false );
						listenRead(); // TODO The socket needs to be reading, otherwise client disconnects do not come through
						complete = true;
						return;
					}
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
			if( !complete )
			{
				close();
				Loggers.nio.trace( "Channel ({}) Input task aborted", getDebugId() );
			}
			else
			{
				Loggers.nio.trace( "Channel ({}) Input task complete", getDebugId() );
			}
		}
	}
}
