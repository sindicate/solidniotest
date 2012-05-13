package solidstack.nio;


public class Timeout
{
	private ReadListener listener;
	private long when;

	public Timeout( ReadListener listener, long when )
	{
		this.listener = listener;
		this.when = when;
	}

	public long getWhen()
	{
		return this.when;
	}

	public ReadListener getListener()
	{
		return this.listener;
	}
}
