package solidstack.nio;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import solidstack.lang.Assert;
import solidstack.lang.ThreadInterrupted;


// TODO Maximum size
public class SocketPool
{
	private List<Socket> pool = new LinkedList<Socket>();
	private List<Socket> all = new LinkedList<Socket>();
//	private List<SocketChannelHandler> allall = new LinkedList<SocketChannelHandler>();

	synchronized public Socket getSocket()
	{
		if( this.pool.isEmpty() )
			return null;
		Socket handler = this.pool.remove( this.pool.size() - 1 );
		Loggers.nio.trace( "Channel ({}) From pool", handler.getDebugId() );
		return handler;
	}

	// Only called by returnToPool()
	synchronized public void releaseSocket( Socket handler )
	{
		Assert.isTrue( this.all.contains( handler ) );
		this.pool.add( handler );
		notify();
	}

	synchronized public void addSocket( Socket socket )
	{
		this.all.add( socket );
		socket.setPool( this );
//		this.allall.add( handler );
	}

	synchronized public void channelClosed( Socket handler )
	{
//		Assert.isTrue( this.pool.remove( handler ) );
		this.all.remove( handler );
		this.pool.remove( handler );
	}

	synchronized public void channelLost( Socket handler )
	{
//		Assert.isFalse( this.all.remove( handler ) );
		this.all.remove( handler );
		this.pool.remove( handler );
	}

	synchronized public int[] getSocketCount()
	{
		return new int[] { this.all.size(), this.pool.size() };
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
		for( Iterator<Socket> i = this.pool.iterator(); i.hasNext(); )
		{
			Socket handler = i.next();
			if( handler.lastPooled() + 60000 <= now )
			{
				Assert.isTrue( this.all.remove( handler ) );
				handler.poolTimeout();
				i.remove();
			}
		}
	}

	synchronized public Socket waitForSocket()
	{
		Socket result;
		do
		{
			try
			{
				wait();
			}
			catch( InterruptedException e )
			{
				throw new ThreadInterrupted();
			}
			result = getSocket();
		}
		while( result == null );
		return result;
	}
}
