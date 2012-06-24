package solidstack.nio;

import java.util.HashMap;

import solidstack.lang.Assert;

public class SocketPool
{
	private Entry pool;
	private int pooled;
	private HashMap<Socket, Entry> all = new HashMap<Socket, Entry>();

	synchronized public void add( Socket socket )
	{
		Assert.isNull( this.all.put( socket, new Entry() ) );
	}

	synchronized public void remove( Socket socket )
	{
		Entry entry = this.all.remove( socket );
		if( entry.socket != null ) // Which means that the entry is pooled
			remove( entry );
	}

	private void remove( Entry entry )
	{
		Assert.notNull( entry.socket );
		entry.socket = null; // Entry is no longer pooled
		if( entry.previous != null )
			entry.previous.next = entry.next;
		if( entry.next != null )
			entry.next.previous = entry.previous;
		else
		{
			Assert.isTrue( this.pool == entry );
			this.pool = entry.previous;
		}
		this.pooled --;
	}

	synchronized public void release( Socket socket )
	{
		Entry entry = this.all.get( socket );
		Assert.notNull( entry );
		Assert.isNull( entry.socket ); // Entry should not already be pooled
		entry.socket = socket;
		entry.next = null;
		entry.previous = this.pool;
		if( this.pool != null )
			this.pool.next = entry;
		this.pool = entry;
		this.pooled ++;
	}

	synchronized public Socket acquire()
	{
		if( this.pool == null )
			return null;
		Socket result = this.pool.socket;
		this.pool.socket = null;
		this.pool = this.pool.previous;
		this.pooled --;
		return result;
	}

	synchronized public int size()
	{
		return this.all.size();
	}

	synchronized public int pooled()
	{
		return this.pooled;
	}

	synchronized public int[] getCounts()
	{
		Entry entry = this.pool;
		int count = 0;
		while( entry != null )
		{
			count++;
			entry = entry.previous;
		}
		Assert.isTrue( count == this.pooled );
		return new int[] { this.all.size(), this.pooled };
	}

	synchronized public void timeout()
	{
		long now = System.currentTimeMillis();
		Entry entry = this.pool;
		int count = 0;
		while( entry != null )
		{
			Socket socket = entry.socket;
			if( socket.lastPooled() + 30000 <= now )
			{
				Assert.isTrue( this.all.remove( socket ) == entry );
				remove( entry ); // This does not modify entry.previous
				socket.poolTimeout(); // TODO Do this outside the synchronized block
			}
			count++;

			entry = entry.previous;
		}
		Loggers.nio.debug( "Considered {} pooled sockets for timeout", count );
	}

	static class Entry
	{
		Entry previous;
		Entry next;
		Socket socket;
	}
}
