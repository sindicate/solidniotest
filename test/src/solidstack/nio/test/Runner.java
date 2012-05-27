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
import solidstack.nio.TooManyConnectionsException;

public class Runner
{
	private int counter;
	private SocketMachine machine;
	Client client;
	Request request;
	Runnable runnable;
	private ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();

	private int started; // TODO Or long
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
			started();
		}
		else
			discarded();
	}

	public void stats( int rate )
	{
		int[] sockets = this.client.getSocketCount();
		int[] timeouts = this.client.getTimeouts();
		Loggers.nio.debug( "Rate: " + rate + ", started: " + this.started + ", discarded: " + this.discarded + ", complete: " + this.completed + ", failed: " + this.failed + ", timeout: " + this.timedOut + ", sockets: " + sockets[ 0 ] + ", pooled: " + sockets[ 1 ] + ", to+: " + timeouts[ 0 ] + ", to-: " + timeouts[ 1 ] );
	}

	synchronized public void started()
	{
		this.started ++;
	}

	synchronized public void discarded()
	{
		this.discarded ++;
	}

	synchronized public void completed()
	{
		this.completed ++;
	}

	synchronized public void failed()
	{
		this.failed ++;
	}

	synchronized public void timedOut()
	{
		this.timedOut ++;
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
						timedOut();
					}

					public void process( Response response )
					{
						if( response.getStatus() == 200 )
							completed();
						else
							failed();
					}
				} );
			}
			catch( ConnectException e )
			{
				failed();
				Loggers.nio.debug( e.getMessage() );
			}
			catch( TooManyConnectionsException e )
			{
				failed();
			}
			catch( Exception e )
			{
				failed();
				Loggers.nio.debug( "", e );
			}
		}
	}
}
