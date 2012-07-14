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

		SocketMachine machine = new SocketMachine();

		Server server = new Server( machine, 8001 );
		server.setApplication( new BackEndServerApplication() );
		server.setMaxConnections( 500 );

		DatabaseWriter writer1 = new DatabaseWriter( machine, true );
		DatabaseWriter writer2 = new DatabaseWriter( machine, false );
		DatabaseWriter writer3 = new DatabaseWriter( machine, false );
		DatabaseWriter writer4 = new DatabaseWriter( machine, false );
		DatabaseWriter writer5 = new DatabaseWriter( machine, false );
		DatabaseWriter writer6 = new DatabaseWriter( machine, false );
		DatabaseWriter writer7 = new DatabaseWriter( machine, false );
		DatabaseWriter writer8 = new DatabaseWriter( machine, false );

		writer1.start();
		writer2.start();
		writer3.start();
		writer4.start();
		writer5.start();
		writer6.start();
		writer7.start();
		writer8.start();

		try
		{
			machine.run();
		}
		finally
		{
			writer1.interrupt();
			writer2.interrupt();
			writer3.interrupt();
			writer4.interrupt();
			writer5.interrupt();
			writer6.interrupt();
			writer7.interrupt();
			writer8.interrupt();
		}
	}
}
