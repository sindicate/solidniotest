package solidstack.nio.test;

import solidstack.nio.SocketMachine;


public class TestClient
{
	static public void main( String[] args )
	{
		System.setProperty( "logback.configurationFile", "solidstack/nio/test/logback-testclient.xml" );

		SocketMachine machine = new SocketMachine();
		machine.start();

		RampGenerator generator = new RampGenerator();
//		ManualGenerator generator = new ManualGenerator();

		Runner runner = new Runner( machine );

		generator.setReceiver( runner );
		generator.setGoal( 1 );
//		generator.setRamp( 60 );
		Thread.currentThread().setPriority( Thread.NORM_PRIORITY + 1 );
		generator.run();
	}

//	static public void main( String[] args )
//	{
//		long now = System.currentTimeMillis();
//		long then = now + 10000;
//		int count = 0;
//		while( now < then )
//		{
//			now = System.currentTimeMillis();
//			count++;
//		}
//		System.out.println( count );
//	}
}
