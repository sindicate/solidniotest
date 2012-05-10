package solidstack.nio.test;

import java.io.IOException;
import java.io.InputStream;

import solidstack.io.FatalIOException;
import solidstack.lang.ThreadInterrupted;
import solidstack.nio.Loggers;

public class ManualGenerator
{
	private Runner runner;
	private int rate;

	public void setReceiver( Runner runner )
	{
		this.runner = runner;
	}

	public void setRate( int rate )
	{
		this.rate = rate;
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
		InputStream in = System.in;

		int rate = this.rate;
		long start = System.currentTimeMillis();
		long base = start;
		int done = 0;
		int sleep = 1000 / rate;
		if( sleep < 10 ) sleep = 10;
		while( true )
		{
			sleep( sleep );

			try
			{
				while( in.available() > 0 )
				{
					int ch = in.read();
					if( ch == 'a' )
					{
						rate += 100;
						Loggers.nio.debug( "Rate: {}", rate );
						System.out.println( "Rate: " + rate );
					}
					else if( ch == 'b' )
					{
						rate -= 100;
						Loggers.nio.debug( "Rate: {}", rate );
						System.out.println( "Rate: " + rate );
					}
				}
			}
			catch( IOException e )
			{
				throw new FatalIOException( e );
			}

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

//			if( now - last >= 1000 )
//			{
//				last += 1000;
//				System.out.println( "Rate: " + rate );
//			}
		}
	}
}
