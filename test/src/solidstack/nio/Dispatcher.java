package solidstack.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import solidstack.io.FatalIOException;
import solidstack.lang.Assert;
import solidstack.lang.ThreadInterrupted;


public class Dispatcher extends Thread
{
	static private int threadId;

	private int maxConnections = 100;

	private Selector selector;
	private Object lock = new Object(); // Used to sequence socket creation and registration
	private ThreadPoolExecutor executor;

	// TODO Build the timeouts on the keys?
	private Map<ReadListener, Timeout> timeouts = new LinkedHashMap<ReadListener, Timeout>(); // TODO Use DelayQueue or other form of concurrent datastructure
	private long nextTimeout;

	private List<HandlerPool> pools = new ArrayList<HandlerPool>();

	private long nextLogging;

	public Dispatcher()
	{
		super( "Dispatcher-" + nextId() );
		setPriority( NORM_PRIORITY + 1 );

		try
		{
			this.selector = Selector.open();
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
		this.executor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
	}

	public void setMaxConnections( int maxConnections )
	{
		this.maxConnections = maxConnections;
	}

	synchronized static private int nextId()
	{
		return ++threadId;
	}

	public void execute( Runnable command )
	{
		// The DefaultThreadFactory will set the priority to NORM_PRIORITY, so no inheritance of the heightened priority of the dispatcher thread.
		this.executor.execute( command );
	}

	public void listen( InetSocketAddress address, ReadListener listener ) throws IOException
	{
		listen( address, 50, listener );
	}

	public void listen( InetSocketAddress address, int backlog, ReadListener listener ) throws IOException
	{
		ServerSocketChannel server = ServerSocketChannel.open();
		server.configureBlocking( false );
		server.socket().bind( address, backlog );

		synchronized( this.lock ) // Prevent register from blocking again
		{
			this.selector.wakeup();
			server.register( this.selector, SelectionKey.OP_ACCEPT, listener );
		}
	}

	public void listenRead( SelectionKey key )
	{
		boolean yes = false;
		synchronized( key )
		{
			int i = key.interestOps();
			if( ( i & SelectionKey.OP_READ ) == 0 )
			{
				key.interestOps( i | SelectionKey.OP_READ );
				yes = true;
			}
		}

		if( yes )
		{
			key.selector().wakeup();
			if( Loggers.nio.isTraceEnabled() )
				Loggers.nio.trace( "Channel ({}) Listening to read", DebugId.getId( key.channel() ) );
		}
	}

	public void listenWrite( SelectionKey key )
	{
		boolean yes = false;
		synchronized( key )
		{
			int i = key.interestOps();
			if( ( i & SelectionKey.OP_WRITE ) == 0 )
			{
				key.interestOps( i | SelectionKey.OP_WRITE );
				yes = true;
			}
		}

		if( yes )
		{
			key.selector().wakeup();
			if( Loggers.nio.isTraceEnabled() )
				Loggers.nio.trace( "Channel ({}) Listening to write", DebugId.getId( key.channel() ) );
		}
	}

	public SocketChannelHandler connect( String hostname, int port ) throws IOException
	{
		SocketChannelHandler handler = new SocketChannelHandler( this );
		connect( hostname, port, handler );
		Loggers.nio.trace( "Channel ({}) New" , handler.getDebugId() );
		return handler;
	}

	public AsyncSocketChannelHandler connectAsync( String hostname, int port )
	{
		AsyncSocketChannelHandler handler = new AsyncSocketChannelHandler( this );
		connect( hostname, port, handler );
		Loggers.nio.trace( "Channel ({}) New" , handler.getDebugId() );
		return handler;
	}

	private void connect( String hostname, int port, SocketChannelHandler handler )
	{
		try
		{
			SocketChannel channel = SocketChannel.open( new InetSocketAddress( hostname, port ) );
			channel.configureBlocking( false );
			SelectionKey key;
			synchronized( this.lock ) // Prevent register from blocking again
			{
				this.selector.wakeup();
				key = channel.register( this.selector, SelectionKey.OP_READ );
			}
			handler.setKey( key );
			key.attach( handler );
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
	}

	public void addTimeout( ReadListener listener, long when )
	{
		synchronized( this.timeouts )
		{
			this.timeouts.put( listener, new Timeout( listener, when ) );
		}
	}

	public void removeTimeout( ReadListener listener )
	{
		synchronized( this.timeouts )
		{
			this.timeouts.remove( listener );
		}
	}

	public void addHandlerPool( HandlerPool pool )
	{
		synchronized( this.pools )
		{
			this.pools.add( pool );
		}
	}

	private void shutdownThreadPool() throws InterruptedException
	{
		Loggers.nio.info( "Shutting down dispatcher" );
		this.executor.shutdown();
		if( this.executor.awaitTermination( 1, TimeUnit.HOURS ) )
			Loggers.nio.info( "Thread pool shut down, interrupting dispatcher thread" );
		else
		{
			Loggers.nio.info( "Thread pool not shut down, interrupting" );
			this.executor.shutdownNow();
			if( !this.executor.awaitTermination( 1, TimeUnit.HOURS ) )
				Loggers.nio.info( "Thread pool could not be shut down" );
		}
	}

	public void shutdown()
	{
		try
		{
			shutdownThreadPool();
			interrupt();
			join();
		}
		catch( InterruptedException e )
		{
			throw new ThreadInterrupted();
		}
	}

	@Override
	public void run()
	{
		Loggers.nio.info( "Dispatcher thread priority: {}", getPriority() );
		try
		{
			while( !Thread.interrupted() )
			{
				synchronized( this.lock )
				{
					// Wait till the connect has done its registration TODO Is this the best way?
					// TODO Make sure this is not optimized away
				}

//				try
//				{
//					Thread.sleep( 100 );
//				}
//				catch( InterruptedException e )
//				{
//					throw new ThreadInterrupted();
//				}

				if( Loggers.nio.isTraceEnabled() )
					Loggers.nio.trace( "Selecting from {} keys", this.selector.keys().size() );
				int selected = this.selector.select( 10000 );
				Loggers.nio.trace( "Selected {} keys", selected );

				Set< SelectionKey > keys = this.selector.selectedKeys();
				boolean canAccept = this.selector.keys().size() < this.maxConnections;
				for( SelectionKey key : keys )
				{
					try
					{
//						if( !key.isValid() )
//						{
//							if( key.attachment() instanceof SocketChannelHandler )
//							{
//								SocketChannelHandler handler = (SocketChannelHandler)key.attachment();
//								handler.close(); // TODO Signal the pool
//							}
//							continue;
//						}

						if( key.isValid() )
							Assert.isTrue( key.channel().isOpen() );

						if( key.isAcceptable() )
							if( canAccept )
							{
								ServerSocketChannel server = (ServerSocketChannel)key.channel();
								SocketChannel channel = server.accept();
								if( channel != null )
								{
									ReadListener listener = (ReadListener)key.attachment();

									channel.configureBlocking( false );
									key = channel.register( this.selector, 0 /* SelectionKey.OP_READ */ ); // TODO Maybe the handler should do that

									ServerSocketChannelHandler handler = new ServerSocketChannelHandler( this, key );
									handler.setListener( listener );
									key.attach( handler );

									Loggers.nio.trace( "Channel ({}) New channel, Readable", handler.getDebugId() );
									handler.dataIsReady();
								}
								else
									Loggers.nio.trace( "Lost accept" );
							}
							else
								Loggers.nio.trace( "Max connections reached, can't accept" );

						if( key.isReadable() )
						{
							// TODO Detect close gives -1 on the read

							final SocketChannel channel = (SocketChannel)key.channel();
							if( Loggers.nio.isTraceEnabled() )
								Loggers.nio.trace( "Channel ({}) Readable", DebugId.getId( channel ) );

							synchronized( key )
							{
								key.interestOps( key.interestOps() ^ SelectionKey.OP_READ );
							}

							SocketChannelHandler handler = (SocketChannelHandler)key.attachment();
							handler.dataIsReady();
						}

						if( key.isWritable() )
						{
							final SocketChannel channel = (SocketChannel)key.channel();
							if( Loggers.nio.isTraceEnabled() )
								Loggers.nio.trace( "Channel ({}) Writable", DebugId.getId( channel ) );

							synchronized( key )
							{
								key.interestOps( key.interestOps() ^ SelectionKey.OP_WRITE );
							}

							SocketChannelHandler handler = (SocketChannelHandler)key.attachment();
							handler.writeIsReady();
						}

						if( key.isConnectable() )
						{
							final SocketChannel channel = (SocketChannel)key.channel();
							if( Loggers.nio.isTraceEnabled() )
								Loggers.nio.trace( "Channel ({}) Connectable", DebugId.getId( channel ) );

							Assert.fail( "Shouldn't come here" );
						}
					}
					catch( CancelledKeyException e )
					{
						if( key.attachment() instanceof SocketChannelHandler )
						{
							SocketChannelHandler handler = (SocketChannelHandler)key.attachment();
							handler.close(); // TODO Signal the pool
						}
					}
				}

				keys.clear();

				long now = System.currentTimeMillis();

				if( Loggers.nio.isDebugEnabled() )
					if( now >= this.nextLogging )
					{
						Loggers.nio.debug( "Active count/keys: {}/{}", this.executor.getActiveCount(), this.selector.keys().size() );
						this.nextLogging = now + 1000;
					}

				if( now >= this.nextTimeout )
				{
					this.nextTimeout = now + 10000;

					Loggers.nio.trace( "Processing timeouts" );

					List<Timeout> timedouts = new ArrayList<Timeout>();
					synchronized( this.timeouts )
					{
						for( Iterator<Timeout> i = this.timeouts.values().iterator(); i.hasNext(); )
						{
							Timeout timeout = i.next();
							if( timeout.getWhen() <= now )
							{
								timedouts.add( timeout );
								i.remove();
							}
						}
					}

					for( Timeout timeout : timedouts )
						timeout.getListener().timeout();

					for( HandlerPool pool : this.pools )
						pool.timeout();

//					// TODO This should not be needed
//					Set<SelectionKey> keys2 = this.selector.keys();
//					Set<SelectableChannel> test = new HashSet<SelectableChannel>();
//					synchronized( keys2 ) // TODO Also synchronize on the selector?
//					{
//						for( SelectionKey key : keys2 )
//						{
//							test.add( key.channel() );
//							if( key.channel() instanceof SocketChannel )
//								Assert.isTrue( ( (SocketChannel)key.channel() ).isConnected() );
//							if( !key.isValid() )
//								if( key.attachment() instanceof SocketChannelHandler )
//									( (SocketChannelHandler)key.attachment() ).lost();
//						}
//
//						Assert.isTrue( test.size() == keys2.size() );
//					}
				}
			}
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}

		if( this.executor.isTerminated() )
			Loggers.nio.info( "Dispatcher ended" );
		else
		{
			Loggers.nio.info( "Dispatcher ended, shutting down thread pool" );
			try
			{
				shutdownThreadPool();
			}
			catch( InterruptedException e )
			{
				throw new ThreadInterrupted();
			}
			Loggers.nio.info( "Thread pool shut down" );
		}
	}
}
