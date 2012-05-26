package solidstack.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

	private SocketPool pool;
	private long lastPooled;

	private ResponseReader reader;
	private AtomicBoolean running = new AtomicBoolean();

	private int debugId;
	private AtomicInteger latch = new AtomicInteger( 0 ); // For assertions


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

	public void setPool( SocketPool pool )
	{
		this.pool = pool;
	}

	public void setReader( ResponseReader reader )
	{
		this.reader = reader;
	}

	protected ResponseReader getReader()
	{
		return this.reader;
	}

	int getDebugId()
	{
		return this.debugId;
	}

	public void acquire()
	{
		Assert.isTrue( this.latch.compareAndSet( 0, 1 ) );
	}

	public void doubleAcquire()
	{
		Assert.isTrue( this.latch.compareAndSet( 0, 2 ) );
	}

	public void release()
	{
		int l = this.latch.decrementAndGet();
		if( l == 0 )
			returnToPool();
		else
			if( l != 1 )
			{
				close();
				Assert.fail( "Expected 1, was " + l );
			}
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
				acquire();
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
		if( this.pool != null )
			this.pool.channelClosed( this );
	}

	void lost()
	{
		close0();
		if( this.pool != null )
			this.pool.channelLost( this );
	}

	void poolTimeout()
	{
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

	void returnToPool()
	{
		if( this.pool != null )
		{
			this.pool.releaseSocket( this );
			this.lastPooled = System.currentTimeMillis();
		}
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
				release();
				Loggers.nio.trace( "Channel ({}) Thread complete", getDebugId() );
			}
		}
	}
}
