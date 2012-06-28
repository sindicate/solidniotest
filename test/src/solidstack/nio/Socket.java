package solidstack.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
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

	private AtomicBoolean running = new AtomicBoolean();

	private int debugId;
	private boolean writing;
	private LinkedList<ResponseReader> readerQueue = new LinkedList<ResponseReader>();

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

	protected ResponseReader getReader()
	{
		if( this.server )
			return this.serverSocket.getReader();
		return this.readerQueue.getFirst();
	}

	public int getDebugId()
	{
		return this.debugId;
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
			returnToPool = this.readerQueue.size() < 4;
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
			returnToPool = !this.writing && this.readerQueue.size() == 4; // TODO Is this ok?
			Assert.isTrue( this.readerQueue.removeFirst() == reader );
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
//			if( this.server ) TODO We are missing listenRead now which is needed to detect disconnects
//				acquireRead();
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
				ResponseReader reader = getReader();
				complete = false;
				try
				{
					reader.incoming( this );
					complete = true;
				}
				finally
				{
					if( complete && !this.server )
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
