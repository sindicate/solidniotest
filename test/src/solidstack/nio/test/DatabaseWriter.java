package solidstack.nio.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import solidstack.lang.SystemException;
import solidstack.lang.ThreadInterrupted;
import solidstack.nio.Dispatcher;
import solidstack.nio.Loggers;

public class DatabaseWriter extends Thread
{
	private Dispatcher dispatcher;
	private PreparedStatement insert;
	private Connection connection;

	// TODO AtomicReference is not really need at this time
	static private AtomicReference<List<Element>> buffer = new AtomicReference<List<Element>>( new LinkedList<Element>() );

	static
	{
		try
		{
//			Connection connection = DriverManager.getConnection( "jdbc:derby:sample;create=true", "", "" );
			Connection connection = DriverManager.getConnection( "jdbc:hsqldb:file:hsample", "", "" );
			try
			{
				connection.createStatement().executeUpdate( "CREATE TABLE TEST ( TEXT VARCHAR( 1000 ) )" );
			}
			catch( SQLException e )
			{
				// ignore
			}
		}
		catch( SQLException e )
		{
			throw new SystemException( e );
		}
	}

	public DatabaseWriter( Dispatcher dispatcher )
	{
		this.dispatcher = dispatcher;
		setPriority( NORM_PRIORITY + 1 );

		try
		{
			this.connection = DriverManager.getConnection( "jdbc:hsqldb:file:hsample", "", "" );
			this.insert = this.connection.prepareStatement( "INSERT INTO TEST ( TEXT ) VALUES ( ? )" );
		}
		catch( SQLException e )
		{
			throw new SystemException( e );
		}
	}

	private void sleep0( long millis )
	{
		try
		{
			sleep( millis );
		}
		catch( InterruptedException e )
		{
			throw new ThreadInterrupted();
		}
	}

	@Override
	public void run()
	{
		// TODO Detect when this thread is down
		try
		{
//			long next = System.currentTimeMillis();
			while( !isInterrupted() )
			{
//				long now = System.currentTimeMillis();
//				long diff = next - now;
//				if( diff > 10 )
//				{
//					System.out.println( "Sleep: " + diff );
//					sleep0( diff );
//				}
//				next = now + 1000;

				List<Element> b;
				synchronized( buffer )
				{
					b = buffer.getAndSet( new LinkedList<Element>() );
				}
				if( b.isEmpty() )
					sleep0( 100 );
				else
				{
					for( Element element : b )
					{
						this.insert.setString( 1, element.string );
						this.insert.addBatch();
					}
					Loggers.nio.debug( "Inserted {} records", b.size() );
					// TODO execute batch when growing too big
					this.insert.executeBatch();
					this.connection.commit();

//					ResultSet result = connection.createStatement().executeQuery( "SELECT COUNT(*) FROM TEST" );
//					result.next();
//					System.out.println( result.getInt( 1 ) );

					// TODO Start a lot of tasks in one burst
					for( Element element : b )
						this.dispatcher.execute( element.runnable );
				}
			}
		}
		catch( SQLException e )
		{
			throw new SystemException( e );
		}
	}

	static public void write( String string, Runnable runnable )
	{
		synchronized( buffer )
		{
			buffer.get().add( new Element( string, runnable ) );
		}
	}

	static private class Element
	{
		String string;
		Runnable runnable;

		public Element( String string, Runnable runnable )
		{
			this.string = string;
			this.runnable = runnable;
		}
	}
}
