package solidstack.httpserver.nio;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;

import solidstack.httpserver.ApplicationContext;
import solidstack.httpserver.CloseBlockingOutputStream;
import solidstack.httpserver.FatalSocketException;
import solidstack.httpserver.HttpException;
import solidstack.httpserver.HttpHeaderTokenizer;
import solidstack.httpserver.Request;
import solidstack.httpserver.RequestContext;
import solidstack.httpserver.Response;
import solidstack.httpserver.Token;
import solidstack.httpserver.UrlEncodedParser;
import solidstack.lang.SystemException;
import solidstack.nio.AsyncSocketChannelHandler;
import solidstack.nio.Dispatcher;
import solidstack.nio.ReadListener;


public class Server
{
	private int port;
	private ApplicationContext application; // TODO Make this a Map
	private Dispatcher dispatcher;
//	boolean debug;

	public Server( Dispatcher dispatcher, int port ) throws IOException
	{
		this.dispatcher = dispatcher;
		this.port = port;

		dispatcher.listen( new InetSocketAddress( port ), new MyConnectionListener() );
	}

	public void setApplication( ApplicationContext application )
	{
		this.application = application;
	}

	public ApplicationContext getApplication()
	{
		return this.application;
	}

	public Dispatcher getDispatcher()
	{
		return this.dispatcher;
	}

	public class MyConnectionListener implements ReadListener
	{
		public void incoming( AsyncSocketChannelHandler handler ) throws IOException
		{
			Request request = new Request();

			HttpHeaderTokenizer tokenizer = new HttpHeaderTokenizer( handler.getInputStream() );

			String line = tokenizer.getLine();
			String[] parts = line.split( "[ \t]+" );

			request.setMethod( parts[ 0 ] );

			String url = parts[ 1 ];
			if( !parts[ 2 ].equals( "HTTP/1.1" ) )
				throw new HttpException( "Only HTTP/1.1 requests are supported" );

//			if( Server.this.debug )
//				System.out.println( "GET " + url + " HTTP/1.1" );

			String parameters = null;
			int pos = url.indexOf( '?' );
			if( pos >= 0 )
			{
				parameters = url.substring( pos + 1 );
				url = url.substring( 0, pos );

				String[] pars = parameters.split( "&" );
				for( String par : pars )
				{
					pos = par.indexOf( '=' );
					if( pos >= 0 )
						request.addParameter( par.substring( 0, pos ), par.substring( pos + 1 ) );
					else
						request.addParameter( par, null );
				}
			}

			// TODO Fragment too? Maybe use the URI class?

			if( url.endsWith( "/" ) )
				url = url.substring( 0, url.length() - 1 );
			request.setUrl( url );
			request.setQuery( parameters );

			Token field = tokenizer.getField();
			while( !field.isEndOfInput() )
			{
				Token value = tokenizer.getValue();
				if( field.equals( "Cookie" ) ) // TODO Case insensitive?
				{
					String s = value.getValue();
					int pos2 = s.indexOf( '=' );
					if( pos2 >= 0 )
						request.addCookie( s.substring( 0, pos2 ), s.substring( pos2 + 1 ) );
					else
						request.addHeader( field.getValue(), s );
				}
				else
				{
					request.addHeader( field.getValue(), value.getValue() );
				}
				field = tokenizer.getField();
			}

			String contentType = request.getHeader( "Content-Type" );
			if( "application/x-www-form-urlencoded".equals( contentType ) )
			{
				String contentLength = request.getHeader( "Content-Length" );
				if( contentLength != null )
				{
					int len = Integer.parseInt( contentLength );
					UrlEncodedParser parser = new UrlEncodedParser( handler.getInputStream(), len );
					String parameter = parser.getParameter();
					while( parameter != null )
					{
						String value = parser.getValue();
						request.addParameter( parameter, value );
						parameter = parser.getParameter();
					}
				}
			}

			OutputStream out = handler.getOutputStream();
			out = new CloseBlockingOutputStream( out );
			Response response = new Response( request, out );
			RequestContext context = new RequestContext( request, response, getApplication() );
			try
			{
				getApplication().dispatch( context );
			}
			catch( FatalSocketException e )
			{
				throw e;
			}
			catch( Exception e )
			{
				Throwable t = e;
				if( t.getClass().equals( HttpException.class ) && t.getCause() != null )
					t = t.getCause();
//				t.printStackTrace( System.out );
				if( !response.isCommitted() )
				{
					response.reset();
					response.setStatusCode( 500, "Internal Server Error" );
					response.setContentType( "text/plain", "ISO-8859-1" );
					PrintWriter writer = response.getPrintWriter( "ISO-8859-1" );
					t.printStackTrace( writer );
					writer.flush();
				}
				if( e instanceof IOException )
					throw (IOException)e;
				if( e instanceof RuntimeException )
					throw (RuntimeException)e;
				throw new SystemException( e );
				// TODO Is the socket going to be closed?
			}

			if( !context.isAsync() )
			{
				response.finish();

				// TODO Detect Connection: close headers on the request & response
				// TODO A GET request has no body, when a POST comes without content size, the connection should be closed.
				// TODO What about socket.getKeepAlive() and the other properties?

				String length = response.getHeader( "Content-Length" );
				if( length == null )
				{
					String transfer = response.getHeader( "Transfer-Encoding" );
					if( !"chunked".equals( transfer ) )
						handler.close();
				}

				if( request.isConnectionClose() )
					handler.close();
			}
			else
			{
				// TODO Add to timeout manager
			}
		}

		public void timeout() throws IOException
		{
			throw new UnsupportedOperationException();
		}
	}
}
