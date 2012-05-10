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

		Dispatcher dispatcher = new Dispatcher();

		Server server = new Server( dispatcher, 8001 );
		server.setApplication( new BackEndServerApplication() );

		new DatabaseWriter( dispatcher ).start();
		new DatabaseWriter( dispatcher ).start();
		new DatabaseWriter( dispatcher ).start();
		new DatabaseWriter( dispatcher ).start();

		dispatcher.run();
	}
}
