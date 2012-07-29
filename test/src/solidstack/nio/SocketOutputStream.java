package solidstack.nio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicReference;

import solidstack.httpserver.FatalSocketException;
import solidstack.lang.Assert;


// TODO Improve performance?
public class SocketOutputStream extends OutputStream
{
	private Socket socket;
	private ByteBuffer buffer;
//	private AtomicBoolean block = new AtomicBoolean();
	private AtomicReference<Thread> thread = new AtomicReference<Thread>();

	public SocketOutputStream( Socket socket )
	{
		this.socket = socket;
		this.buffer = ByteBuffer.allocate( 8192 );
	}

	synchronized public void acquire()
	{
		if( !this.thread.compareAndSet( null, Thread.currentThread() ) )
		{
			Thread thread = this.thread.get();
			if( thread != null )
			{
				Throwable t = new Throwable();
				t.setStackTrace( thread.getStackTrace() );
				Loggers.nio.error( "Stacktrace of conflicting thread", t );
				Assert.fail( "Channel (" + this.socket.getDebugId() + ") " + thread.getName() );
			}
			else
				Assert.fail( "Channel (" + this.socket.getDebugId() + ") ???" );
		}
	}

	synchronized private void test()
	{
		if( this.thread.get() != Thread.currentThread() )
		{
			Thread thread = this.thread.get();
			if( thread != null )
			{
				Throwable t = new Throwable();
				t.setStackTrace( this.thread.get().getStackTrace() );
				Loggers.nio.error( "Stacktrace of conflicting thread", t );
			}
			Assert.fail();
		}
	}

	synchronized public void release()
	{
		this.thread.set( null );
	}

	@Override
	public void write( int b )
	{
		test();
		Assert.isTrue( this.buffer.hasRemaining() );
		this.buffer.put( (byte)b );
		if( !this.buffer.hasRemaining() )
			writeChannel();
	}

	@Override
	public void write( byte[] b, int off, int len )
	{
		if( len == 0 )
			return;

		test();
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
	}

	@Override
	public void flush() throws IOException
	{
		test();
		if( this.buffer.position() > 0 )
			writeChannel();
	}

	@Override
	public void close() throws IOException
	{
		flush();
		this.socket.close();
	}

	static private void logBuffer( int id, ByteBuffer buffer )
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

	private void writeChannel()
	{
		SocketChannel channel = this.socket.getChannel();
		int id = DebugId.getId( channel );

		if( !channel.isOpen() )
			Assert.fail( "Channel (" + id + ") is closed" );
		if( !channel.isConnected() )
			Assert.fail( "Channel (" + id + ") is not connected" );
		this.buffer.flip();
		Assert.isTrue( this.buffer.hasRemaining() );

		try
		{
			logBuffer( id, this.buffer );
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
						this.socket.getMachine().listenWrite( this.socket.getKey() );
						wait();
					}
				}
				catch( InterruptedException e )
				{
					throw new FatalSocketException( e );
				}

				logBuffer( id, this.buffer );
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
