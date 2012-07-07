package solidstack.nio;

// TODO Not used
public class TaskCoordinator
{
	private boolean running;
	private volatile boolean signalLost;

	synchronized public boolean signal()
	{
		if( this.running )
		{
			this.signalLost = true;
			return false;
		}
		this.running = true;
		return true;
	}

	synchronized public void ponr()
	{
		this.signalLost = false;
	}

	synchronized public boolean stop()
	{
		return !this.signalLost;
	}

	synchronized public void stopped()
	{
		this.running = false;
	}
}
