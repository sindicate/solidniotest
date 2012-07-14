package solidstack.nio;

import java.util.Set;

import solidstack.nio.MyLinkedList.Entry;


public class SocketPool
{
	private MyLinkedList<ClientSocket> pool = new MyLinkedList<ClientSocket>();
	private MyLinkedList<ClientSocket> activePool = new MyLinkedList<ClientSocket>();

	synchronized public ClientSocket acquire()
	{
		ClientSocket socket = this.activePool.peekHead();
		if( socket != null )
		{
			int left = socket.windowLeft();
			if( left > 0 )
			{
				if( left == 1 )
					this.activePool.stashHead();
				Loggers.nio.trace( "Channel ({}) From active pool", socket.getDebugId() );
				return socket;
			}
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
		return new int[] { this.pool.all() + this.activePool.all(), this.activePool.all(), active };
	}

	synchronized public void release( ClientSocket socket )
	{
		if( socket.isActive() )
		{
			if( !socket.windowClosed() )
				this.activePool.unstash( socket );
		}
		else
			this.activePool.moveTo( socket, this.pool );
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

	public void timeout()
	{
		Entry<ClientSocket> entry = this.pool.timeout();
		while( entry != null )
		{
			entry.item.poolTimeout();
			Loggers.nio.trace( "Channel ({}) Timed out from pool", entry.item.getDebugId() );
			entry = entry.next;
		}
	}
}
