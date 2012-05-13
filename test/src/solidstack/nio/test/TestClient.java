package solidstack.nio.test;

import solidstack.nio.SocketMachine;

public class TestClient
{
	static public void main( String[] args )
	{
		System.setProperty( "logback.configurationFile", "solidstack/nio/test/logback-testclient.xml" );

		SocketMachine dispatcher = new SocketMachine();
		dispatcher.start();

		RampGenerator generator = new RampGenerator();
//		ManualGenerator generator = new ManualGenerator();

		Runner runner = new Runner( dispatcher );

		generator.setReceiver( runner );
		generator.setGoal( 100 );
//		generator.setRamp( 60 );
		generator.run();
	}
}
