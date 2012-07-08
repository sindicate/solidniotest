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
 * @author Ren� M. de Bloois
 */
public class ClientSocket extends Socket implements Runnable
{
	private NIOClient client;
	private int maxWindowSize = 1;

	final private AtomicBoolean running = new AtomicBoolean();

	LinkedList<RequestWriter> writerQueue = new LinkedList<RequestWriter>();
	boolean queueRunning;

	LinkedList<ResponseReader> readerQueue = new LinkedList<ResponseReader>();

	private AtomicInteger active = new AtomicInteger();



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

	synchronized public void request( RequestWriter request )
	{
		this.active.incrementAndGet();

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

	public boolean windowOpen()
	{
		return this.active.get() < this.maxWindowSize;
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
				this.active.decrementAndGet();
				this.client.release( this );

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
