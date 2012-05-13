package solidstack.nio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicReference;

import solidstack.httpserver.FatalSocketException;
import solidstack.lang.Assert;


// TODO Improve performance?
public class SocketChannelOutputStream extends OutputStream
{
	private SocketChannelHandler handler;
	private ByteBuffer buffer;
//	private AtomicBoolean block = new AtomicBoolean();
	private AtomicReference<Thread> block = new AtomicReference<Thread>();

	public SocketChannelOutputStream( SocketChannelHandler handler )
	{
		this.handler = handler;
		this.buffer = ByteBuffer.allocate( 8192 );
	}

	@Override
	public void write( int b )
	{
		if( !this.block.compareAndSet( null, Thread.currentThread() ) )
			Assert.fail( "Channel (" + this.handler.getDebugId() + ") " + this.block.get().getName() );

		Assert.isTrue( this.buffer.hasRemaining() );
		this.buffer.put( (byte)b );
		if( !this.buffer.hasRemaining() )
			writeChannel();

		this.block.set( null );
	}

	@Override
	public void write( byte[] b, int off, int len )
	{
		if( !this.block.compareAndSet( null, Thread.currentThread() ) )
			Assert.fail( "Channel (" + this.handler.getDebugId() + ") " + this.block.get().getName() );

		while( len > 0 )
		{
			int l = len;
			if( l > this.buffer.remaining() )
				l = this.buffer.remaining();
			this.buffer.put( b, off, l );
			off += l;
			len -= l;
			if( !this.buffer.hasRemaining() )
				writeChannel();
		}

		this.block.set( null );
	}

	@Override
	public void flush() throws IOException
	{
		if( !this.block.compareAndSet( null, Thread.currentThread() ) )
			Assert.fail( "Channel (" + this.handler.getDebugId() + ") " + this.block.get().getName() );

		if( this.buffer.position() > 0 )
			writeChannel();

		this.block.set( null );
	}

	@Override
	public void close() throws IOException
	{
		flush();
		this.handler.close();
	}

	static protected void logBuffer( int id, ByteBuffer buffer )
	{
//		StringBuilder log = new StringBuilder();
		byte[] bytes = buffer.array();
//		int end = buffer.limit();
//		for( int i = 0; i < end; i++ )
//		{
//			String s = "00" + Integer.toHexString( bytes[ i ] );
//			log.append( s.substring( s.length() - 2 ) );
//			log.append( ' ' );
//		}
//		Loggers.nio.trace( log.toString() );
		Loggers.nio.trace( "Channel (" + id + ") " + new String( bytes, 0, buffer.limit() ) );
	}

	protected void writeChannel()
	{
		SocketChannel channel = this.handler.getChannel();
		int id = DebugId.getId( channel );

		Assert.isTrue( channel.isOpen(), "Channel is closed" );
		Assert.isTrue( channel.isConnected() );
		this.buffer.flip();
		Assert.isTrue( this.buffer.hasRemaining() );

		try
		{
//			logBuffer( id, this.buffer );
			int written = channel.write( this.buffer );
			if( Loggers.nio.isTraceEnabled() )
				Loggers.nio.trace( "Channel ({}) written #{} bytes to channel (1)", id, written );
			while( this.buffer.hasRemaining() )
			{
				try
				{
					synchronized( this )
					{
						// Prevent losing a notify: listenWriter() must be called within the synchronized block
						this.handler.getDispatcher().listenWrite( this.handler.getKey() );
						wait();
					}
				}
				catch( InterruptedException e )
				{
					throw new FatalSocketException( e );
				}

//				logBuffer( id, this.buffer );
				written = channel.write( this.buffer );
				if( Loggers.nio.isTraceEnabled() )
					Loggers.nio.trace( "Channel ({}) written #{} bytes to channel (2)", id, written );
			}

			this.buffer.clear();
		}
		catch( IOException e )
		{
			throw new FatalSocketException( e );
		}
	}
}
