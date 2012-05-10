package solidstack.nio.test;

import solidstack.httpserver.RequestContext;
import solidstack.httpserver.ResponseWriter;
import solidstack.httpserver.Servlet;
import solidstack.lang.ThreadInterrupted;


public class BackEndRootServlet implements Servlet
{
	public void call( RequestContext context )
	{
		String sleep = context.getRequest().getParameter( "sleep" );
		if( sleep != null )
			try
			{
				Thread.sleep( Integer.parseInt( sleep ) );
			}
			catch( InterruptedException e )
			{
				throw new ThreadInterrupted();
			}

		context.getResponse().setContentType( "text/html", null );
		ResponseWriter writer = context.getResponse().getWriter();
		writer.write( "Hello World!\n" );
	}
}
