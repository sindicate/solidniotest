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

		long now = System.currentTimeMillis();

		long rampStartMillis = now;
		int rampBaseRate = rate;
		int rampDelta = this.goal - rampBaseRate;

		long triggerStartMillis = rampStartMillis;
		int triggerCount = 0;

		int sleep = 1000 / rate;
		if( sleep < 10 ) sleep = 10;

		long stats = 0;
//		int triggeredTotal = 0; // TODO Or long?

		while( true )
		{
			now = System.currentTimeMillis();

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
						this.goal -= 100;
						if( this.goal < 0 )
							this.goal = 0;
						rampStartMillis = now; // restart the ramp up
						rampBaseRate = rate;
						rampDelta = this.goal - rate;
						Loggers.nio.debug( "Goal: {}", this.goal );
					}
					else if( ch == 'x' )
					{
						this.goal = 0;
						rampStartMillis = now - this.rampPeriod; // stop the ramp up
//						rampBaseRate = rate;
//						rampDelta = this.goal - rate;
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
				rate = rampBaseRate + (int)( rampDelta * running / this.rampPeriod );
			else
				rate = this.goal;
			if( rate != oldRate )
			{
				if( rate == 0 )
					sleep = 1000;
				else
				{
					sleep = 1000 / rate;
					if( sleep < 10 ) sleep = 10;
				}
			}

			// Determine how many to trigger
			int need = (int)( ( now - triggerStartMillis ) * rate / 1000 );
			int diff = need - triggerCount;

			if( diff > 0 )
			{
				// Trigger
				for( int i = 0; i < diff; i++ )
					this.runner.trigger();
//				triggeredTotal += diff;

				// Adjust
				// TODO Use ten second sliding average
				triggerCount += diff;
				if( triggerCount >= 100000 )
				{
					triggerStartMillis = now;
					triggerCount = 0;
				}
			}

			// Sleep
			sleep( sleep );

			// Stats
			if( now - stats >= 1000 )
			{
				stats += 1000;
				if( now - stats >= 1000 )
					stats = now + 1000;
				this.runner.stats( rate );
			}
		}
	}
}
