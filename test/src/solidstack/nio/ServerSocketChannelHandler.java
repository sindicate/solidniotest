package solidstack.nio;

import java.nio.channels.SelectionKey;

import solidstack.httpserver.ApplicationContext;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
public class ServerSocketChannelHandler extends AsyncSocketChannelHandler
{
	/**
	 * Constructor.
	 *
	 * @param socket The incoming connection.
	 * @param applicationContext The {@link ApplicationContext}.
	 */
	public ServerSocketChannelHandler( Dispatcher dispatcher, SelectionKey key )
	{
		super( dispatcher );

		setKey( key );
	}

	@Override
	public void dataIsReady()
	{
		// Not running -> not waiting -> no notify needed
		if( !isRunningAndSet() )
		{
			acquire();
			getDispatcher().execute( this ); // TODO Also for write
			Loggers.nio.trace( "Channel ({}) Started thread", getDebugId() );
			return;
		}

		super.dataIsReady();
	}

//	@Override
//	public void run()
//	{
//		boolean complete = false;
//		try
//		{
//			Loggers.nio.trace( "Channel ({}) Task started", getDebugId() );
//
//			SelectionKey key = getKey();
//			while( true )
//			{
//				// TODO Try catch/finally?
//				getListener().incoming( this );
//
//				if( isOpen() )
//				{
//					if( getInputStream().available() == 0 )
//					{
//						complete = true;
//						return;
//					}
//					Assert.fail( "Channel (" + getDebugId() + ") Shouldn't come here (yet): available = " + getInputStream().available() );
//				}
//				else
//				{
//					complete = true;
//					return;
//				}
//			}
//		}
//		catch( Exception e )
//		{
//			Loggers.nio.debug( "Channel ({}) Unhandled exception", getDebugId(), e );
//		}
//		finally
//		{
//			endOfRunning();
//			if( !complete )
//			{
//				close();
//				Loggers.nio.trace( "Channel ({}) Thread aborted", getDebugId() );
//			}
//			else
//			{
//				release(); // After endOfRunning()
//				Loggers.nio.trace( "Channel ({}) Thread complete", getDebugId() );
//			}
//		}
//	}
}
