package solidstack.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import solidstack.io.FatalIOException;


/**
 * Thread that handles an incoming connection.
 *
 * @author René M. de Bloois
 */
abstract public class Socket implements Runnable
{
	private SocketMachine machine;
	private SelectionKey key;
	private SocketInputStream in;
	private SocketOutputStream out;

	private AtomicBoolean running = new AtomicBoolean();

	private int debugId;
	private boolean writing;
	private LinkedList<ResponseReader> readerQueue = new LinkedList<ResponseReader>();

	public Socket( SocketMachine machine )
	{
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

	public int getDebugId()
	{
		return this.debugId;
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

	void dataIsReady()
	{
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

	abstract public void run();
}
