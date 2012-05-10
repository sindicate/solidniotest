package solidstack.nio.test;

import java.io.IOException;

import solidstack.httpserver.ApplicationContext;

public class MiddleServerApplication extends ApplicationContext
{
	public MiddleServerApplication() throws IOException
	{
		registerServlet( "", new MiddleRootServlet() );
	}
}
