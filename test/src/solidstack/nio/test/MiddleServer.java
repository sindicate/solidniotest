package solidstack.nio.test;

import java.io.IOException;

import solidstack.httpserver.nio.Server;
import solidstack.nio.Dispatcher;


public class MiddleServer
{
	static public Dispatcher dispatcher;

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main( String[] args ) throws IOException
	{
		System.setProperty( "logback.configurationFile", "solidstack/nio/test/logback-middle.xml" );

		dispatcher = new Dispatcher();

		Server server = new Server( dispatcher, 8002 );
		server.setApplication( new MiddleServerApplication() );

		dispatcher.run();
	}
}
