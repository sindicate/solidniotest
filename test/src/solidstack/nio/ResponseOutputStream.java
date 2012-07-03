package solidstack.nio;

import java.io.IOException;
import java.io.OutputStream;


public class ResponseOutputStream extends OutputStream
{
	private OutputStream out;

	public ResponseOutputStream( OutputStream out )
	{
		this.out = out;
	}

	@Override
	public void write( byte[] b, int off, int len ) throws IOException
	{
		this.out.write( b, off, len );
	}

	@Override
	public void write( int b ) throws IOException
	{
		this.out.write( b );
	}

	@Override
	public void close() throws IOException
	{
		flush();
	}

	@Override
	public void flush() throws IOException
	{
		this.out.flush();
	}
}
