package solidstack.httpclient.nio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import solidstack.httpclient.ChunkedInputStream;
import solidstack.httpclient.Request;
import solidstack.httpclient.Response;
import solidstack.httpclient.ResponseProcessor;
import solidstack.httpserver.HttpBodyInputStream;
import solidstack.httpserver.HttpException;
import solidstack.httpserver.HttpHeaderTokenizer;
import solidstack.httpserver.Token;
import solidstack.io.FatalIOException;
import solidstack.nio.ClientSocket;
import solidstack.nio.ResponseReader;
import solidstack.nio.Socket;
import solidstack.nio.SocketMachine;


public class Client
{
	SocketMachine machine;

	private ClientSocket socket;

	// TODO Non blocking request when waiting on a connections?
	public Client( String hostname, int port, SocketMachine machine )
	{
		this.machine = machine;

		this.socket = machine.createClientSocket( hostname, port );
	}

	public void setMaxConnections( int maxConnections )
	{
		this.socket.setMaxConnections( maxConnections );
	}

	public int[] getSocketCount()
	{
		return this.socket.getSocketCount();
	}

	public void request( Request request, final ResponseProcessor processor ) throws ConnectException
	{
		Socket socket = this.socket.getSocket();

		socket.doubleAcquire(); // Need 2 releases: this request and the received response
		boolean complete = false;
		try
		{
			MyResponseReader reader = new MyResponseReader( processor );
			socket.setReader( reader );

			this.machine.addTimeout( reader, socket, System.currentTimeMillis() + 10000 );

			sendRequest( request, socket.getOutputStream() );

			complete = true;
		}
		finally
		{
			if( complete )
				socket.release();
			else
				socket.close();
		}
	}

	// TODO Add to timeout manager
	public class MyResponseReader implements ResponseReader
	{
		final private ResponseProcessor processor;

		public MyResponseReader( ResponseProcessor processor )
		{
			this.processor = processor;
		}

		public void incoming( Socket socket ) throws IOException
		{
			Response response = receiveResponse( socket.getInputStream() );
			InputStream in = response.getInputStream();
			this.processor.process( response );
//			this.processor = null;
			drain( in, null );

			// TODO Is this the right spot? How to coordinate this with the timeout event?
			// TODO Maybe just timeout the socket and remove the timeout in the release.
			Client.this.machine.removeTimeout( this );
		}

		public void timeout( Socket handler ) throws IOException
		{
			if( this.processor != null )
			{
				this.processor.timeout();
				handler.timeout();
			}
		}
	}

	static private final byte[] GET = "GET ".getBytes();
	static private final byte[] HTTP = " HTTP/1.1\r\n".getBytes();
	static private final byte[] NEWLINE = "\r\n".getBytes();
	static private final byte[] COLON = ": ".getBytes();

	private void sendRequest( Request request, OutputStream out )
	{
		try
		{
			out.write( GET );
			String path = request.getPath();
			if( path.length() > 0 )
				out.write( path.getBytes() );
			else
				out.write( '/' );
			out.write( HTTP );
			for( Map.Entry< String, List< String > > entry : request.getHeaders().entrySet() )
				for( String value : entry.getValue() )
				{
					out.write( entry.getKey().getBytes() );
					out.write( COLON );
					out.write( value.getBytes() );
					out.write( NEWLINE );
				}
			out.write( NEWLINE );
			out.flush(); // TODO Flush/close or what?
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
	}

	Response receiveResponse( InputStream in )
	{
		Response result = new Response();

		HttpHeaderTokenizer tokenizer = new HttpHeaderTokenizer( in );

		String line = tokenizer.getLine();
		String[] parts = line.split( "[ \t]+" );

		if( !parts[ 0 ].equals( "HTTP/1.1" ) )
			throw new HttpException( "Only HTTP/1.1 responses are supported" );

		result.setHttpVersion( parts[ 0 ] );
		result.setStatus( Integer.parseInt( parts[ 1 ] ) );
		result.setReason( parts[ 2 ] );

		Token field = tokenizer.getField();
		while( !field.isEndOfInput() )
		{
			Token value = tokenizer.getValue();
//			System.out.println( "    "+ field.getValue() + " = " + value.getValue() );
			result.addHeader( field.getValue(), value.getValue() );
			field = tokenizer.getField();
		}

		String length = result.getHeader( "Content-Length" );
		if( length != null )
		{
			int l = Integer.parseInt( length );
			result.setInputStream( new HttpBodyInputStream( in, l ) );
		}
		else
		{
			String encoding = result.getHeader( "Transfer-Encoding" );
			if( "chunked".equals( encoding ) )
//				result.setInputStream( in );
				result.setInputStream( new ChunkedInputStream( in ) );
		}

		// TODO Which error codes do not contain a body?

		return result;

		// TODO Detect Connection: close headers on the request & response
		// TODO What about socket.getKeepAlive() and the other properties?

//		String length = getHeader( "Content-Length" );
//		Assert.notNull( length );
//		int l = Integer.parseInt( length );
//		this.bodyIn = new HttpBodyInputStream( in, l );

//		if( length == null )
//		{
//			String transfer = response.getHeader( "Transfer-Encoding" );
//			if( !"chunked".equals( transfer ) )
//				this.socket.close();
//		}
//
//		if( !this.socket.isClosed() )
//			if( request.isConnectionClose() )
//				this.socket.close();
//		if( this.socket.isClosed() )
//			return;
//		if( !this.socket.isThreadPerConnection() )
//			if( in.available() <= 0 )
//				return;
	}

	void drain( InputStream in, PrintStream out )
	{
		if( in == null )
			return;
		try
		{
			int i = in.read();
			while( i >= 0 )
			{
				if( out != null )
					out.print( (char)i );
				i = in.read();
			}
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
	}
}
