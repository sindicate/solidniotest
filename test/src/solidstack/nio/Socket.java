package solidstack.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import solidstack.httpserver.FatalSocketException;
import solidstack.io.FatalIOException;
import solidstack.lang.Assert;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
public class Socket implements Runnable
{
	private boolean server;
	private SocketMachine machine;
	private SelectionKey key;
	private SocketInputStream in;
	private SocketOutputStream out;

	private ClientSocket clientSocket;
	private ServerSocket serverSocket;
	private long lastPooled;

	private ResponseReader reader;
	private AtomicBoolean running = new AtomicBoolean();

	private int debugId;
	private boolean writing;
	private boolean reading;


	public Socket( boolean server, SocketMachine machine )
	{
		this.server = server;
		this.machine = machine;

		this.in = new SocketInputStream( this );
		this.out = new SocketOutputStream( this );

		this.debugId = -1;
	}

	void setKey( SelectionKey key )
	{
		this.key = key;
		this.debugId = DebugId.getId( key.channel() );
	}

	public void setClientSocket( ClientSocket clientSocket )
	{
		this.clientSocket = clientSocket;
	}

	public void setServerSocket( ServerSocket serverSocket )
	{
		this.serverSocket = serverSocket;
	}

	public void setReader( ResponseReader reader )
	{
		this.reader = reader;
	}

	protected ResponseReader getReader()
	{
		return this.reader;
	}

	public int getDebugId()
	{
		return this.debugId;
	}

	synchronized public void acquireReadWrite()
	{
		Assert.isFalse( this.writing );
		Assert.isFalse( this.reading );
		this.writing = this.reading = true;
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
			returnToPool = !this.reading;
		}
		if( returnToPool )
			returnToPool();
	}

	synchronized public void acquireRead()
	{
		Assert.isFalse( this.reading );
		this.reading = true;
	}

	public void releaseRead()
	{
		boolean returnToPool;
		synchronized( this )
		{
			Assert.isTrue( this.reading );
			this.reading = false;
			returnToPool = !this.writing;
		}
		if( returnToPool )
			returnToPool();
	}

	public SocketInputStream getInputStream()
	{
		return this.in;
	}

	public SocketOutputStream getOutputStream()
	{
		return this.out;
	}

	public SocketMachine getMachine()
	{
		return this.machine;
	}

	SocketChannel getChannel()
	{
		return (SocketChannel)this.key.channel();
	}

	SelectionKey getKey()
	{
		return this.key;
	}

	protected boolean isRunningAndSet()
	{
		return !this.running.compareAndSet( false, true );
	}

	protected void endOfRunning()
	{
		this.running.set( false );
	}

	void dataIsReady()
	{
		// Not running -> not waiting -> no notify needed
		if( !isRunningAndSet() )
		{
			if( this.server )
				acquireRead();
			getMachine().execute( this ); // TODO Also for write
			Loggers.nio.trace( "Channel ({}) Started thread", getDebugId() );
			return;
		}

		synchronized( this.in )
		{
			this.in.notify();
		}
		Loggers.nio.trace( "Channel ({}) Signalled inputstream", getDebugId() );
	}

	void writeIsReady()
	{
		synchronized( this.out )
		{
			this.out.notify();
		}
		Loggers.nio.trace( "Channel ({}) Signalled outputstream", getDebugId() );
	}

	public boolean isOpen()
	{
		return this.key.channel().isOpen();
	}

	public void close()
	{
		close0();
		if( this.clientSocket != null )
			this.clientSocket.channelClosed( this );
		if( this.serverSocket != null )
			this.serverSocket.channelClosed( this ); // TODO Ignore if the socket.close() is called twice
	}

	void lost()
	{
		close0();
		if( this.clientSocket != null )
			this.clientSocket.channelLost( this );
		if( this.serverSocket != null )
			throw new UnsupportedOperationException();
	}

	void poolTimeout()
	{
		Loggers.nio.trace( "Channel ({}) PoolTimeout", getDebugId() );
		close0();
	}

	// TODO Make this package private
	public void timeout()
	{
		Loggers.nio.trace( "Channel ({}) Timeout", getDebugId() );
		close();
	}

	private void close0()
	{
		this.key.cancel();
		if( isOpen() )
		{
			Loggers.nio.trace( "Channel ({}) Closed", getDebugId() );
			try
			{
				this.key.channel().close();
			}
			catch( IOException e )
			{
				throw new FatalIOException( e );
			}
		}
	}

	long lastPooled()
	{
		return this.lastPooled;
	}

	void pooled()
	{
		this.lastPooled = System.currentTimeMillis();
	}

	void returnToPool()
	{
		if( this.clientSocket != null )
			this.clientSocket.releaseSocket( this );
		this.machine.listenRead( this.key ); // TODO The socket needs to be reading, otherwise client disconnects do not come through
	}

	public void run()
	{
		boolean complete = false;
		try
		{
			try
			{
				if( this.in.endOfFile() )
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
				getReader().incoming( this );

				if( isOpen() )
				{
					if( getInputStream().available() == 0 )
					{
						complete = true;
						return;
					}
					Assert.fail( "Channel (" + getDebugId() + ") Shouldn't come here (yet): available = " + getInputStream().available() );
				}
				else
				{
					complete = true;
					return;
				}
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
			{
				releaseRead();
				Loggers.nio.trace( "Channel ({}) Thread complete", getDebugId() );
			}
		}
	}
}
