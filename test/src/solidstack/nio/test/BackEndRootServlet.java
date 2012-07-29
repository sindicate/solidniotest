package solidstack.nio.test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import solidstack.httpserver.HttpException;
import solidstack.httpserver.HttpResponse;
import solidstack.httpserver.RequestContext;
import solidstack.httpserver.ResponseOutputStream;
import solidstack.httpserver.Servlet;


public class BackEndRootServlet implements Servlet
{
	public HttpResponse call( final RequestContext context )
	{
		HttpResponse response = new HttpResponse()
		{
			@Override
			public void write( ResponseOutputStream out ) throws HttpException
			{
				try
				{
					out.setContentType( "text/html", null );
					Writer writer = new OutputStreamWriter( out ); // TODO Need charset
					writer.write( "Hello World!\n" );
					writer.flush();
				}
				catch( IOException e )
				{
					throw new HttpException( e );
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
