package solidstack.nio.test;

import java.io.OutputStreamWriter;
import java.io.Writer;

import solidstack.httpserver.HttpResponse;
import solidstack.httpserver.RequestContext;
import solidstack.httpserver.ResponseOutputStream;
import solidstack.httpserver.Servlet;
import solidstack.httpserver.nio.AsyncResponse;
import solidstack.nio.Loggers;


public class BackEndRootServlet implements Servlet
{
	public HttpResponse call( final RequestContext context )
	{
		AsyncResponse response = new AsyncResponse()
		{
			@Override
			public void write( ResponseOutputStream out )
			{
				try
				{
					out.setContentType( "text/html", null );
					Writer writer = new OutputStreamWriter( out ); // TODO Need charset
					writer.write( "Hello World!\n" );
					writer.flush();
				}
				catch( Exception e )
				{
					Loggers.nio.debug( "BackEnd Unhandled exception", e );
				}
			}
		};

		DatabaseWriter.write( "test", response );

		return response;

//		String sleep = context.getRequest().getParameter( "sleep" );
//		if( sleep != null )
//			try
//			{
//				Thread.sleep( Integer.parseInt( sleep ) );
//			}
//			catch( InterruptedException e )
//			{
//				throw new ThreadInterrupted();
//			}
	}
}
