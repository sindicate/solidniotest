package solidstack.nio;

import java.io.IOException;

public interface RequestReader
{
	void incoming( ServerSocket handler ) throws IOException;
}
