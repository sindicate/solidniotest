package solidstack.nio.test;

import java.io.IOException;

import solidstack.httpserver.nio.Server;
import solidstack.nio.Dispatcher;


public class BackEndServer
{
	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main( String[] args ) throws IOException
	{
		System.setProperty( "logback.configurationFile", "solidstack/nio/test/logback-backend.xml" );

		Dispatcher selector = new Dispatcher();

		Server server = new Server( selector, 8001 );
		server.setApplication( new BackEndServerApplication() );

		selector.run();
	}
}
