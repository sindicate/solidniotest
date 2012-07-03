package solidstack.nio.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import solidstack.httpserver.nio.AsyncResponse;
import solidstack.lang.SystemException;
import solidstack.lang.ThreadInterrupted;
import solidstack.nio.Loggers;
import solidstack.nio.SocketMachine;

public class DatabaseWriter extends Thread
{
	private SocketMachine machine;
	private PreparedStatement insert;
	private Connection connection;

	private boolean stats;
	static private int written;
	static private int responses;

	// TODO AtomicReference is not really need at this time
	static private List<Element> buffer = new LinkedList<Element>();
	static private Object bufferLock = new Object();

	static
	{
		try
		{
//			Connection connection = DriverManager.getConnection( "jdbc:derby:sample;create=true", "", "" );
//			Connection connection = DriverManager.getConnection( "jdbc:hsqldb:file:hsample", "", "" );
//			Class.forName( "oracle.jdbc.OracleDriver" );
			Connection connection = DriverManager.getConnection( "jdbc:oracle:thin:@192.168.0.109:1521:XE", "RENE", "RENE" );
			Statement statement = connection.createStatement();
			try
			{
				statement.executeUpdate( "DROP TABLE TEST" );
			}
			catch( SQLException e )
			{
				Loggers.nio.debug( e.getMessage() );
			}
			try
			{
				statement.executeUpdate( "CREATE TABLE TEST ( TEXT VARCHAR2( 1000 ) )" );
			}
			catch( SQLException e )
			{
				Loggers.nio.debug( e.getMessage() );
			}
		}
		catch( SQLException e )
		{
			throw new SystemException( e );
		}
//		catch( ClassNotFoundException e )
//		{
//			throw new SystemException( e );
//		}
	}

	public DatabaseWriter( SocketMachine dispatcher, boolean stats )
	{
		this.machine = dispatcher;
		this.stats = stats;
		setPriority( NORM_PRIORITY + 1 );

		try
		{
			this.connection = DriverManager.getConnection( "jdbc:oracle:thin:@192.168.0.109:1521:XE", "RENE", "RENE" );
//			this.connection = DriverManager.getConnection( "jdbc:hsqldb:file:hsample", "", "" );
			this.connection.setAutoCommit( false );
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
			Loggers.nio.debug( getName() + " interrupted" );
			throw new ThreadInterrupted();
		}
	}

	@Override
	public void run()
	{
		// TODO Detect when this thread is down
		long last = System.currentTimeMillis();
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

				List<Element> b = null;
				synchronized( bufferLock )
				{
					if( !buffer.isEmpty() )
					{
						b = buffer;
						buffer = new LinkedList<Element>();
					}
				}
				if( b == null )
					sleep0( 100 );
				else
				{
					for( Element element : b )
					{
						this.insert.setString( 1, element.string );
						this.insert.addBatch();
					}
//					Loggers.nio.debug( "Inserted {} records", b.size() );
					// TODO execute batch when growing too big
					this.insert.executeBatch();
					this.connection.commit();

//					ResultSet result = connection.createStatement().executeQuery( "SELECT COUNT(*) FROM TEST" );
//					result.next();
//					System.out.println( result.getInt( 1 ) );

					// TODO Start a lot of tasks in one burst
					for( Element element : b )
						element.response.ready();

					synchronized( DatabaseWriter.class )
					{
						DatabaseWriter.written += b.size();
						DatabaseWriter.responses += b.size();
					}
				}

				if( this.stats )
				{
					long now = System.currentTimeMillis();
					if( now - last >= 1000 )
					{
						last = now;
						Loggers.nio.debug( "Written: " + DatabaseWriter.written + ", responses: " + DatabaseWriter.responses );
					}
				}
			}
		}
		catch( SQLException e )
		{
			throw new SystemException( e );
		}
	}

	static public void write( String string, AsyncResponse response )
	{
		synchronized( bufferLock )
		{
			buffer.add( new Element( string, response ) );
		}
	}

	static private class Element
	{
		String string;
		AsyncResponse response;

		public Element( String string, AsyncResponse response )
		{
			this.string = string;
			this.response = response;
		}
	}
}
