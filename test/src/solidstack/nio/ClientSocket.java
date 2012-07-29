package solidstack.nio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

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
	volatile int readerQueueSize;


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

	int getActive()
	{
		return this.readerQueueSize;
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
					while( true )
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

						ResponseReader reader = writer.getResponseReader();
						boolean full;
						synchronized( ClientSocket.this )
						{
							ClientSocket.this.readerQueue.add( reader );
							full = ++ClientSocket.this.readerQueueSize >= ClientSocket.this.maxWindowSize;
							if( full )
								ClientSocket.this.client.socketWriteFull( ClientSocket.this );
						}

						ResponseOutputStream out = new ResponseOutputStream( getOutputStream() );
						Loggers.nio.trace( "Channel ({}) Writing request", getDebugId() );
						writer.write( out );
						try
						{
							out.close(); // Need close() for the chunkedoutputstream
						}
						catch( IOException e )
						{
							throw new FatalIOException( e );
						}

						if( full )
						{
							complete = true;
							return;
						}
					}
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

	// TODO Not used
	public int windowLeft()
	{
		return 0;
	}

	// TODO Not used
	public boolean windowClosed()
	{
		return false;
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
		try
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
						boolean full = ClientSocket.this.readerQueueSize-- == ClientSocket.this.maxWindowSize;
						// FIXME When window size is 1, socketGotAir is never called
						if( ClientSocket.this.readerQueueSize == 0 )
							ClientSocket.this.client.socketFinished( ClientSocket.this );
						else if( full )
							ClientSocket.this.client.socketGotAir( ClientSocket.this );
					}

					reader.incoming( this );

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
		catch( Exception e )
		{
			Loggers.nio.debug( "Channel ({}) Unhandled exception", getDebugId(), e );
		}
	}
}
