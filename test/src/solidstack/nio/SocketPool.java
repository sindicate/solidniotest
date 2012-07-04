package solidstack.nio;

import java.util.HashMap;

import solidstack.lang.Assert;

public class SocketPool
{
	private Entry pool;
	private Entry tail;
	private int pooled;
	private HashMap<Socket, Entry> all = new HashMap<Socket, Entry>();

	synchronized public void add( Socket socket )
	{
		Assert.isNull( this.all.put( socket, new Entry() ) );
	}

	synchronized public void remove( Socket socket )
	{
		Entry entry = this.all.remove( socket );
		// Assert.notNull( entry ); TODO Maybe re-enable this assertion
		if( entry != null )
			if( entry.socket != null ) // Which means that the entry is pooled
				remove( entry );
	}

	// Remove from the pool
	private void remove( Entry entry )
	{
		Loggers.nio.trace( "Channel ({}) Remove from pool", entry.socket.getDebugId() );

		Assert.notNull( entry.socket );
		entry.socket = null; // Entry is no longer pooled
		if( entry.previous != null )
			entry.previous.next = entry.next;
		else
		{
			Assert.isTrue( this.tail == entry );
			this.tail = entry.next;
		}
		if( entry.next != null )
			entry.next.previous = entry.previous;
		else
		{
			Assert.isTrue( this.pool == entry );
			this.pool = entry.previous;
			if( this.pool == null )
				Assert.isNull( this.tail );
		}
		this.pooled --;
	}

	synchronized public void release( ClientSocket socket )
	{
		Loggers.nio.trace( "Channel ({}) To pool", socket.getDebugId() );

		Entry entry = this.all.get( socket );
		Assert.notNull( entry );
		entry.lastPooled = System.currentTimeMillis();

		Assert.isNull( entry.socket ); // Entry should not already be pooled
		entry.socket = socket;
		entry.next = null;
		entry.previous = this.pool;
		if( this.pool != null )
			this.pool.next = entry;
		else
		{
			Assert.isNull( this.tail );
			this.tail = entry;
		}
		this.pool = entry;
		this.pooled ++;
	}

	synchronized public ClientSocket acquire()
	{
		if( this.pool == null )
			return null;
		ClientSocket result = this.pool.socket;
		this.pool.lastPooled = System.currentTimeMillis();
//		this.pool.socket = null;
//		this.pool = this.pool.previous;
//		if( this.pool == null )
//			this.tail = null;
//		this.pooled --;
		Loggers.nio.trace( "Channel ({}) From pool", result.getDebugId() );
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

	public void timeout()
	{
		long now = System.currentTimeMillis();

		Entry tail;
		Entry entry;

		synchronized( this )
		{
			tail = this.tail;
			entry = tail;

			// TODO Maximum number of timeouts per occurrence or use closer thread
			while( entry != null && entry.lastPooled + 30000 <= now )
				entry = entry.next;

			if( entry != null )
			{
				// entry is the first one that is not timing out
				if( entry != tail )
				{
					this.tail = entry;
					entry.previous.next = null;
					entry.previous = null;
				}
				else
					tail = null;
			}
			else
			{
				// all the entries are timing out, or pool and tail are already null
				if( tail != null )
					this.pool = this.tail = null;
			}

			entry = tail;
			while( entry != null )
			{
				Entry e = this.all.remove( entry.socket );
				Assert.isTrue( e == entry, e != null ? e.toString() : "null" );
				this.pooled --;

				entry = entry.next;
			}
		}

		entry = tail;
		while( entry != null )
		{
			entry.socket.poolTimeout();
			Loggers.nio.trace( "Channel ({}) Timed out from pool", entry.socket.getDebugId() );
			entry.socket = null; // Not in the pool
			entry = entry.next;
		}
	}

	static class Entry
	{
		Entry previous;
		Entry next;
		ClientSocket socket;
		long lastPooled;
	}
}
