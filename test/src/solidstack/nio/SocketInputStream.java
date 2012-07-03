package solidstack.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import solidstack.httpserver.FatalSocketException;
import solidstack.lang.Assert;


public class SocketInputStream extends InputStream
{
	private Socket socket;
	private ByteBuffer buffer;

	public SocketInputStream( Socket handler )
	{
		this.socket = handler;
		this.buffer = ByteBuffer.allocate( 8192 );
		this.buffer.flip();
	}

	@Override
	synchronized public int read() throws IOException
	{
		if( this.socket == null )
			return -1;
		if( !this.buffer.hasRemaining() )
		{
			readChannel();
			if( !this.buffer.hasRemaining() )
				return -1;
		}

		return (char)this.buffer.get();
	}

	@Override
	synchronized public int read( byte[] b, int off, int len ) throws IOException
	{
		if( this.socket == null )
			return -1;
		if( !this.buffer.hasRemaining() )
		{
			readChannel();
			if( !this.buffer.hasRemaining() )
				return -1;
		}

		if( len > this.buffer.remaining() )
			len = this.buffer.remaining();
		this.buffer.get( b, off, len );
		return len;
	}

	@Override
	synchronized public int available() throws IOException
	{
		return this.buffer.remaining();
	}

	synchronized public boolean endOfFile() throws IOException
	{
		if( this.buffer.hasRemaining() )
			return false;
		readChannel();
		return !this.buffer.hasRemaining();
	}

	// TODO Implement close()?

	static private void logBuffer( int id, ByteBuffer buffer )
	{
		byte[] bytes = buffer.array();
		Loggers.nio.trace( "Channel (" + id + ") " + new String( bytes, 0, buffer.limit() ) );
	}

	// TODO What if it read too much? Like when 2 requests are chained. The handler needs to keep reading.
	private void readChannel()
	{
		SocketChannel channel = this.socket.getChannel();
		int id = DebugId.getId( channel );

		Assert.isFalse( this.buffer.hasRemaining() );
		Assert.isTrue( channel.isOpen() );

		this.buffer.clear();

		try
		{
			int read = channel.read( this.buffer );
			if( Loggers.nio.isTraceEnabled() )
				Loggers.nio.trace( "Channel ({}) read #{} bytes from channel", new Object[] { id, read } );
			while( read == 0 )
			{
				try
				{
					synchronized( this )
					{
						// Prevent losing a notify: listenRead() must be called within the synchronized block
						this.socket.listenRead();
						Loggers.nio.trace( "Channel ({}) Input stream calls wait()", id, read );
						wait();
					}
				}
				catch( InterruptedException e )
				{
					throw new FatalSocketException( e );
				}

				read = channel.read( this.buffer );
				if( Loggers.nio.isTraceEnabled() )
					Loggers.nio.trace( "Channel ({}) read #{} bytes from channel (2)", id, read );
			}

			this.buffer.flip();

			if( read == -1 )
			{
				this.socket.close(); // TODO This should cancel all keys
				this.socket = null;
			}
			else
				logBuffer( id, this.buffer );
		}
		catch( IOException e )
		{
			throw new FatalSocketException( e );
		}
	}
}
