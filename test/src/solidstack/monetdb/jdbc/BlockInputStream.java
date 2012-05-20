package solidstack.monetdb.jdbc;

import java.io.IOException;
import java.io.InputStream;

import solidstack.io.FatalIOException;

public class BlockInputStream
{
	private InputStream in;

	public BlockInputStream( InputStream in )
	{
		this.in = in;
	}

	public String readBlock()
	{
		try
		{
			int b1 = this.in.read();
			int b2 = this.in.read();
			int len = ( b1 | b2 << 8 ) >> 1;
			System.out.println( len );
			byte[] block = new byte[ len ];
			int read = this.in.read( block );
			if( read < len )
			{
				int pos = read;
				do
				{
					read = this.in.read( block, pos, block.length - pos );
					pos += read;
				}
				while( pos < len );
			}
			String result = new String( block ); // TODO This does decoding
			System.out.println( result );
			return result;
		}
		catch( IOException e )
		{
			throw new FatalIOException( e );
		}
	}
}
