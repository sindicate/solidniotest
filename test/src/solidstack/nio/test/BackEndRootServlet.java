package solidstack.nio.test;

import solidstack.httpserver.RequestContext;
import solidstack.httpserver.ResponseWriter;
import solidstack.httpserver.Servlet;
import solidstack.nio.Loggers;


public class BackEndRootServlet implements Servlet
{
	public void call( final RequestContext context )
	{
		context.setAsync( true );

		DatabaseWriter.write( "test", new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					context.getResponse().setContentType( "text/html", null );
					ResponseWriter writer = context.getResponse().getWriter();
					writer.write( "Hello World!\n" );
					context.getResponse().finish();
					// FIXME Need to release the socket
				}
				catch( Exception e )
				{
					Loggers.nio.debug( "BackEnd Unhandled exception", e );
				}
			}
		} );

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
