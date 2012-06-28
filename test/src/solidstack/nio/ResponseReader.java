package solidstack.nio;

import java.io.IOException;

public interface ResponseReader
{
	void incoming( ClientSocket handler ) throws IOException;
	void timeout( ClientSocket handler ) throws IOException;
}
