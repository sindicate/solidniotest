package solidstack.nio;

import java.io.IOException;

public interface ResponseReader
{
	void incoming( Socket handler ) throws IOException;
	void timeout( Socket handler ) throws IOException;
}
