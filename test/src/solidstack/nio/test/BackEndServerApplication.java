package solidstack.nio.test;

import solidstack.httpserver.ApplicationContext;

public class BackEndServerApplication extends ApplicationContext
{
	public BackEndServerApplication()
	{
		registerServlet( "", new BackEndRootServlet() );
	}
}
