package solidstack.nio.test;

import java.io.IOException;
import java.io.InputStream;

import solidstack.io.FatalIOException;
import solidstack.lang.ThreadInterrupted;
import solidstack.nio.Loggers;

public class RampGenerator
{
	private Runner runner;
	private int goal;
	private int rampPeriod = 30000;

	public void setReceiver( Runner runner )
	{
		this.runner = runner;
	}

	public void setGoal( int goal )
	{
		this.goal = goal;
	}

	public void setRamp( int seconds )
	{
		this.rampPeriod = seconds * 1000;
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

		int rate = 1;

		long rampStartMillis = System.currentTimeMillis();
		int rampBaseRate = rate;
		int rampDelta = this.goal - rampBaseRate;

		long triggerStartMillis = rampStartMillis;
		int triggerCount = 0;

		int sleep = 1000 / rate;
		if( sleep < 10 ) sleep = 10;

		while( true )
		{
			long now = System.currentTimeMillis();

			// Listen to the keyboard
			try
			{
				while( in.available() > 0 )
				{
					int ch = in.read();
					if( ch == 'a' )
					{
						this.goal += 100;
						rampStartMillis = now; // restart the ramp up
						rampBaseRate = rate;
						rampDelta = this.goal - rate;
						Loggers.nio.debug( "Goal: {}", this.goal );
					}
					else if( ch == 'b' )
					{
						if( this.goal <= 100 )
							this.goal = 1;
						else
							this.goal -= 100;
						rampStartMillis = now; // restart the ramp up
						rampBaseRate = rate;
						rampDelta = this.goal - rate;
						Loggers.nio.debug( "Goal: {}", this.goal );
					}
				}
			}
			catch( IOException e )
			{
				throw new FatalIOException( e );
			}

			// Adjust the rate as a function of the goal and the ramp
			long running = now - rampStartMillis;
			int oldRate = rate;
			if( running < this.rampPeriod )
			{
				rate = rampBaseRate + (int)( rampDelta * running / this.rampPeriod );
				sleep = 1000 / rate;
				if( sleep < 10 ) sleep = 10;
			}
			else
				rate = this.goal;
			if( rate != oldRate )
				Loggers.nio.debug( "Rate: {}", rate );

			// Determine how many to trigger
			int need = (int)( ( now - triggerStartMillis ) * rate / 1000 );
			int diff = need - triggerCount;

			for( int i = 0; i < diff; i++ )
				this.runner.trigger(); // FIXME Need ThreadPool

			triggerCount += diff;
			if( triggerCount >= 100000 )
			{
				triggerStartMillis = now;
				triggerCount = 0;
			}

			sleep( sleep );

//			if( now - last >= 1000 )
//			{
//				last += 1000;
//				System.out.println( "Rate: " + rate );
//			}
		}
	}
}
