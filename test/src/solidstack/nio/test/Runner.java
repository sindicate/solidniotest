package solidstack.nio.test;

import java.net.ConnectException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import solidstack.httpclient.Request;
import solidstack.httpclient.Response;
import solidstack.httpclient.ResponseProcessor;
import solidstack.httpclient.nio.Client;
import solidstack.nio.Loggers;
import solidstack.nio.SocketMachine;

public class Runner
{
	private int counter;
	private SocketMachine machine;
	Client client;
	Request request;
	Runnable runnable;
	private ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();

	private int started;
	private int discarded;
	int completed;
	int timedOut;
	int failed;

	private long last = System.currentTimeMillis();

	public Runner( SocketMachine machine )
	{
		this.machine = machine;
//		this.client = new Client( "192.168.0.105", 8001, dispatcher );
		this.client = new Client( "localhost", 8001, machine );
		this.client.setMaxConnections( 300 );
		this.request = new Request( "/" );
//		this.request.setHeader( "Host", "www.nu.nl" );
		this.runnable = new MyRunnable();
	}

	public void trigger()
	{
//		System.out.println( "triggered " + this.counter++ );

		if( this.executor.getActiveCount() < 100 )
		{
			this.executor.execute( this.runnable );
			this.started ++;
		}
		else
			this.discarded ++;

		long now = System.currentTimeMillis();
		if( now - this.last >= 1000 )
		{
			this.last += 1000;

			int[] sockets = this.client.getSocketCount();
			Loggers.nio.debug( "Complete: " + this.completed + ", failed: " + this.failed + ", discarded: " + this.discarded + ", timeout: " + this.timedOut + ", sockets: " + sockets[ 0 ] + ", pooled: " + sockets[ 1 ] );
		}
	}

	public class MyRunnable implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				Runner.this.client.request( Runner.this.request, new ResponseProcessor()
				{
					public void timeout()
					{
						Runner.this.timedOut ++;
					}

					public void process( Response response )
					{
						if( response.getStatus() == 200 )
							Runner.this.completed ++;
						else
							Runner.this.failed ++;
					}
				} );
			}
			catch( RuntimeException e )
			{
				// TODO TooManyConnectionsException should that be failed or discarded?
				Runner.this.failed ++;
				Loggers.nio.debug( "", e );
			}
			catch( ConnectException e )
			{
				Runner.this.failed ++;
				Loggers.nio.debug( e.getMessage() );
			}
		}
	}
}
