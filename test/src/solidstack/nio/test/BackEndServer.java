package solidstack.nio.test;

import java.io.IOException;

import solidstack.httpserver.nio.Server;
import solidstack.nio.SocketMachine;


public class BackEndServer
{
	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main( String[] args ) throws IOException
	{
		System.setProperty( "logback.configurationFile", "solidstack/nio/test/logback-backend.xml" );

		SocketMachine dispatcher = new SocketMachine();
		dispatcher.setMaxConnections( 500 );

		Server server = new Server( dispatcher, 8001 );
		server.setApplication( new BackEndServerApplication() );

		DatabaseWriter writer1 = new DatabaseWriter( dispatcher );
		DatabaseWriter writer2 = new DatabaseWriter( dispatcher );
		DatabaseWriter writer3 = new DatabaseWriter( dispatcher );
		DatabaseWriter writer4 = new DatabaseWriter( dispatcher );

		writer1.start();
		writer2.start();
		writer3.start();
		writer4.start();

		dispatcher.run();

		writer1.interrupt();
		writer2.interrupt();
		writer3.interrupt();
		writer4.interrupt();
	}
}
