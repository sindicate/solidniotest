package solidstack.nio;

import java.io.OutputStream;


public interface RequestWriter
{
	ResponseReader write( OutputStream out );
}
