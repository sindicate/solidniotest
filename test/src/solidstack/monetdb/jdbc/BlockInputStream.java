package solidstack.monetdb.jdbc;

import java.io.IOException;
import java.io.InputStream;

import solidstack.io.FatalIOException;

public class BlockInputStream
{
	private InputStream in;
	private boolean last;

	public BlockInputStream( InputStream in )
	{
		this.in = in;
	}

	// TODO We need UTF-8
	public String readBlock()
	{
		try
		{
			int b1 = this.in.read();
			int b2 = this.in.read();
			int len = ( b1 | b2 << 8 ) >> 1;
			this.last = ( b1 & 1 ) != 0 || len < 8190; // TODO This is not right
			System.out.println( "<--" + len );
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

	public String readBlocks()
	{
		String result = readBlock();
		if( this.last )
			return result;
		StringBuilder builder = new StringBuilder( result );
		do
		{
			String s = readBlock();
			builder.append( s );
		}
		while( !this.last );
		return builder.toString();
	}
}
