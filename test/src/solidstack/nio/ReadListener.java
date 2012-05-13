package solidstack.nio;

import java.io.IOException;

public interface ReadListener
{
	void incoming( AsyncSocketChannelHandler handler ) throws IOException;
	void timeout() throws IOException;
}
