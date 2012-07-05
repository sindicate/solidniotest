package solidstack.nio;

import java.util.Set;


public class SocketPool
{
	private MyLinkedList<ClientSocket> pool = new MyLinkedList<ClientSocket>();
	private MyLinkedList<ClientSocket> activePool = new MyLinkedList<ClientSocket>();

	synchronized public ClientSocket acquire()
	{
		ClientSocket socket = this.activePool.peekHead();
		if( socket != null )
			if( socket.windowOpen() )
			{
				this.activePool.stashHead();
				Loggers.nio.trace( "Channel ({}) From active pool", socket.getDebugId() );
				return socket;
			}

		socket = this.pool.moveHeadToStash( this.activePool );
		if( socket == null )
			return null;

		Loggers.nio.trace( "Channel ({}) From idle pool", socket.getDebugId() );
		return socket;
	}

	synchronized public int[] getCounts()
	{
		Set<ClientSocket> sockets = this.activePool.getAll();
		int active = 0;
		for( ClientSocket socket : sockets )
			active += socket.getActive();
		return new int[] { this.pool.all() + this.activePool.all(), this.pool.size() + this.activePool.size(), active };
	}

	synchronized public void releaseActive( ClientSocket socket )
	{
		this.activePool.unstash( socket );
	}

	synchronized public void add( ClientSocket socket )
	{
		this.pool.addHead( socket );
	}

	synchronized public void remove( ClientSocket socket )
	{
		this.pool.remove( socket );
		this.activePool.remove( socket );
	}

	synchronized public int all()
	{
		return this.pool.all() + this.activePool.all();
	}

//	synchronized public void add( ClientSocket socket )
//	{
//		Assert.isNull( this.all.put( socket, new Entry() ) );
//	}
//
//	synchronized public void remove( ClientSocket socket )
//	{
//		Entry entry = this.all.remove( socket );
//		// Assert.notNull( entry ); TODO Maybe re-enable this assertion
//		if( entry != null )
//			if( entry.socket != null ) // Which means that the entry is pooled
//				remove( entry );
//	}
//
//	// Remove from the pool
//	private void remove( Entry entry )
//	{
//		Loggers.nio.trace( "Channel ({}) Remove from pool", entry.socket.getDebugId() );
//
//		Assert.notNull( entry.socket );
//		entry.socket = null; // Entry is no longer pooled
//		if( entry.previous != null )
//			entry.previous.next = entry.next;
//		else
//		{
//			Assert.isTrue( this.tail == entry );
//			this.tail = entry.next;
//		}
//		if( entry.next != null )
//			entry.next.previous = entry.previous;
//		else
//		{
//			Assert.isTrue( this.pool == entry );
//			this.pool = entry.previous;
//			if( this.pool == null )
//				Assert.isNull( this.tail );
//		}
//		this.pooled --;
//	}
//
//	synchronized public void release( ClientSocket socket )
//	{
//		Loggers.nio.trace( "Channel ({}) To pool", socket.getDebugId() );
//
//		Entry entry = this.all.get( socket );
//		Assert.notNull( entry );
//		entry.lastPooled = System.currentTimeMillis();
//
//		Assert.isNull( entry.socket ); // Entry should not already be pooled
//
//		this.pool.addHead( entry );
//		entry.socket = socket;
//		entry.next = null;
//		entry.previous = this.pool;
//		if( this.pool != null )
//			this.pool.next = entry;
//		else
//		{
//			Assert.isNull( this.tail );
//			this.tail = entry;
//		}
//		this.pool = entry;
//		this.pooled ++;
//	}
//
//	synchronized public ClientSocket acquire()
//	{
//		if( this.pool == null )
//			return null;
//		ClientSocket result = this.pool.socket;
//		this.pool.lastPooled = System.currentTimeMillis();
////		this.pool.socket = null;
////		this.pool = this.pool.previous;
////		if( this.pool == null )
////			this.tail = null;
////		this.pooled --;
//		Loggers.nio.trace( "Channel ({}) From pool", result.getDebugId() );
//		return result;
//	}
//
//	synchronized public int pooled()
//	{
//		return this.pooled;
//	}
//
//	synchronized public int[] getCounts()
//	{
//		Entry entry = this.pool;
//		int count = 0;
//		while( entry != null )
//		{
//			count++;
//			entry = entry.previous;
//		}
//		Assert.isTrue( count == this.pooled );
//		return new int[] { this.all.size(), this.pooled };
//	}
//
//	public void timeout()
//	{
//		long now = System.currentTimeMillis();
//
//		Entry tail;
//		Entry entry;
//
//		synchronized( this )
//		{
//			tail = this.tail;
//			entry = tail;
//
//			// TODO Maximum number of timeouts per occurrence or use closer thread
//			while( entry != null && entry.lastPooled + 30000 <= now )
//				entry = entry.next;
//
//			if( entry != null )
//			{
//				// entry is the first one that is not timing out
//				if( entry != tail )
//				{
//					this.tail = entry;
//					entry.previous.next = null;
//					entry.previous = null;
//				}
//				else
//					tail = null;
//			}
//			else
//			{
//				// all the entries are timing out, or pool and tail are already null
//				if( tail != null )
//					this.pool = this.tail = null;
//			}
//
//			entry = tail;
//			while( entry != null )
//			{
//				Entry e = this.all.remove( entry.socket );
//				Assert.isTrue( e == entry, e != null ? e.toString() : "null" );
//				this.pooled --;
//
//				entry = entry.next;
//			}
//		}
//
//		entry = tail;
//		while( entry != null )
//		{
//			entry.socket.poolTimeout();
//			Loggers.nio.trace( "Channel ({}) Timed out from pool", entry.socket.getDebugId() );
//			entry.socket = null; // Not in the pool
//			entry = entry.next;
//		}
//	}
//
//	static class Entry
//	{
//		Entry previous;
//		Entry next;
//		ClientSocket socket;
//		long lastPooled;
//	}
}
