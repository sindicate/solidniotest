package solidstack.monetdb.jdbc;

import java.io.IOException;
import java.io.OutputStream;

import solidstack.io.FatalIOException;

public class BlockOutputStream
{
	private OutputStream out;

	// TODO We need UTF-8
	public BlockOutputStream( OutputStream out )
	{
		this.out = out;
	}

	public void writeBlock( String s )
	{
		int len = ( s.length() << 1 ) + 1;
		System.out.println( "-->" + len );
		System.out.println( s );
		try
		{
			this.out.write( len & 255 );
			this.out.write( len >> 8 & 255 );
			this.out.write( s.getBytes() ); // TODO Does encoding
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
	}

	public void flush()
	{
		try
		{
			this.out.flush();
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
	}
}
