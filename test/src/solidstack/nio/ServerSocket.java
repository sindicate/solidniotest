package solidstack.nio;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import solidstack.httpserver.FatalSocketException;
import solidstack.httpserver.Response;
import solidstack.httpserver.ResponseListener;
import solidstack.io.FatalIOException;


/**
 * Thread that handles an incoming connection.
 *
 * @author Ren� M. de Bloois
 */
public class ServerSocket extends Socket implements Runnable, ResponseListener
{
	private NIOServer server;
	private AtomicBoolean running = new AtomicBoolean();

	LinkedList<Response> responseQueue = new LinkedList<Response>();
	boolean queueRunning;


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
	void readReady()
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

		super.readReady();
	}

	public void add( Response response )
	{
		Loggers.nio.trace( "Channel ({}) Adding response", getDebugId() );
		synchronized( this.responseQueue  )
		{
			this.responseQueue.addLast( response );
			response.setListener( this ); // TODO Is this correct locking wise?
			if( response.isReady() )
				responseIsReady( response );
		}
	}

	@Override
	public void responseIsReady( Response response )
	{
		boolean started = false;
		synchronized( this.responseQueue  )
		{
			response.setReady();
			if( this.responseQueue.getFirst().isReady() )
			{
				if( !this.queueRunning )
				{
					this.queueRunning = true;
					started = true;
					final Response firstResponse = ServerSocket.this.responseQueue.removeFirst();
					getMachine().execute( new Runnable()
					{
						@Override
						public void run()
						{
							Loggers.nio.trace( "Channel ({}) Started response queue", getDebugId() );
							boolean complete = false;
							try
							{
								Response response = firstResponse;
								while( true )
								{
									ResponseOutputStream out = new ResponseOutputStream( getOutputStream() );
									Loggers.nio.trace( "Channel ({}) Writing response", getDebugId() );
									response.write( out );
									try
									{
										out.close(); // Need close() for the chunkedoutputstream
									}
									catch( IOException e )
									{
										throw new FatalIOException( e );
									}
									synchronized( ServerSocket.this.responseQueue )
									{
										Response first = ServerSocket.this.responseQueue.peekFirst();
										if( first == null || !first.isReady() )
										{
											ServerSocket.this.queueRunning = false; // TODO What if exception?
											complete = true;
											try
											{
												getOutputStream().flush(); // TODO Is this ok?
											}
											catch( IOException e )
											{
												throw new FatalIOException( e );
											}
											return;
										}
										response = ServerSocket.this.responseQueue.removeFirst();
									}
								}
							}
							finally
							{
								if( !complete )
									close(); // TODO What about synchronized?
								Loggers.nio.trace( "Channel ({}) Ended response queue", getDebugId() );
							}
						}
					} );
				}
			}
		}
		if( started )
			Loggers.nio.trace( "Channel ({}) Starting response queue", getDebugId() );
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
				Response response = reader.incoming( this );
				if( !isOpen() )
					break;

				if( !response.needsInput() )
					add( response );
				else
					throw new UnsupportedOperationException();

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
