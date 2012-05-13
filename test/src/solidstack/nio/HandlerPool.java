package solidstack.nio;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import solidstack.lang.Assert;


public class HandlerPool
{
	private List<SocketChannelHandler> pool = new LinkedList<SocketChannelHandler>();
	private List<SocketChannelHandler> all = new LinkedList<SocketChannelHandler>();
//	private List<SocketChannelHandler> allall = new LinkedList<SocketChannelHandler>();

	synchronized public SocketChannelHandler getHandler()
	{
		if( this.pool.isEmpty() )
			return null;
		SocketChannelHandler handler = this.pool.remove( this.pool.size() - 1 );
		Loggers.nio.trace( "Channel ({}) From pool", handler.getDebugId() );
		return handler;
	}

	// Only called by returnToPool()
	synchronized public void putHandler( SocketChannelHandler handler )
	{
		Assert.isTrue( this.all.contains( handler ) );
		this.pool.add( handler );
	}

	synchronized public void addHandler( SocketChannelHandler handler )
	{
		this.all.add( handler );
//		this.allall.add( handler );
	}

	synchronized public void channelClosed( SocketChannelHandler handler )
	{
//		Assert.isTrue( this.pool.remove( handler ) );
		this.all.remove( handler );
		this.pool.remove( handler );
	}

	synchronized public void channelLost( SocketChannelHandler handler )
	{
//		Assert.isFalse( this.all.remove( handler ) );
		this.all.remove( handler );
		this.pool.remove( handler );
	}

	synchronized public int size()
	{
		return this.pool.size();
	}

	synchronized public int total()
	{
		return this.all.size();
	}

//	synchronized public int connected()
//	{
//		int result = 0;
//		for( SocketChannelHandler handler : this.allall )
//			if( handler.isOpen() )
//				result ++;
//		return result;
//	}

	synchronized public void timeout()
	{
		long now = System.currentTimeMillis();
		for( Iterator<SocketChannelHandler> i = this.pool.iterator(); i.hasNext(); )
		{
			SocketChannelHandler handler = i.next();
			if( handler.lastPooled() + 60000 <= now )
			{
				Assert.isTrue( this.all.remove( handler ) );
				handler.poolTimeout();
				i.remove();
			}
		}
	}
}
