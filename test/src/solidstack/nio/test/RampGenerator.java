package solidstack.nio.test;

import solidstack.lang.ThreadInterrupted;

public class RampGenerator
{
	private Runner runner;
	private int rate;
	private int ramp = 30000;

	public void setReceiver( Runner runner )
	{
		this.runner = runner;
	}

	public void setRate( int rate )
	{
		this.rate = rate;
	}

	public void setRamp( int seconds )
	{
		this.ramp = seconds * 1000;
	}

	private void sleep( long millis )
	{
		try
		{
			Thread.sleep( millis );
		}
		catch( InterruptedException e )
		{
			throw new ThreadInterrupted();
		}
	}

	public void run()
	{
		int rate = 1;
		long start = System.currentTimeMillis();
		long base = start;
		int done = 0;
		int sleep = 1000 / rate;
		if( sleep < 10 ) sleep = 10;
		long last = start;
		while( true )
		{
			sleep( sleep );

			long now = System.currentTimeMillis();
			int need = (int)( ( now - base ) * rate / 1000 );
			int diff = need - done;

			for( int i = 0; i < diff; i++ )
				this.runner.trigger(); // FIXME Need ThreadPool

			done += diff;
			if( done >= 1000 )
			{
				base = now;
				done = 0;
			}

			long running = now - start;
			if( running < this.ramp )
			{
				rate = (int)( this.rate * running / this.ramp );
				sleep = 1000 / rate;
				if( sleep < 10 ) sleep = 10;
			}
			else
				rate = this.rate;

//			if( now - last >= 1000 )
//			{
//				last += 1000;
//				System.out.println( "Rate: " + rate );
//			}
		}
	}
}
