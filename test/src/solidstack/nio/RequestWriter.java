package solidstack.nio;

import java.io.OutputStream;


public interface RequestWriter
{
	ResponseReader getResponseReader();
	void write( OutputStream out );
}
