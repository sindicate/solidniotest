package solidstack.nio;

import java.util.concurrent.atomic.AtomicBoolean;

import solidstack.httpserver.FatalSocketException;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
public class ServerSocket extends Socket
{
	private NIOServer server;

	private AtomicBoolean running = new AtomicBoolean();


	public ServerSocket( SocketMachine machine )
	{
		super( machine );
	}

	public void setServer( NIOServer server )
	{
		this.server = server;
	}

	@Override
	public void close()
	{
		super.close();
		if( this.server != null )
			this.server.channelClosed( this ); // TODO Ignore if the socket.close() is called twice
	}

	@Override
	void dataIsReady()
	{
		// Not running -> not waiting -> no notify needed
		if( !isRunningAndSet() )
		{
//			if( this.server ) TODO We are missing listenRead now which is needed to detect disconnects
//				acquireRead();
			getMachine().execute( this ); // TODO Also for write
			Loggers.nio.trace( "Channel ({}) Started thread", getDebugId() );
			return;
		}

		super.dataIsReady();
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
	public void run()
	{
		SocketInputStream in = getInputStream();
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
				RequestReader reader = this.server.getReader();
				reader.incoming( this );

				if( !isOpen() )
					break;
				if( getInputStream().available() == 0 )
				{
					listenRead();
					break;
				}

				Loggers.nio.trace( "Channel ({}) Continue reading", getDebugId() );
			}

			Loggers.nio.trace( "Channel ({}) Thread complete", getDebugId() );
		}
		catch( Exception e )
		{
			Loggers.nio.debug( "Channel ({}) Unhandled exception", getDebugId(), e );
			close();
			Loggers.nio.trace( "Channel ({}) Thread aborted", getDebugId() );
		}
		finally
		{
			endOfRunning();
		}
	}
}
