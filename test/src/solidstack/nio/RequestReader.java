package solidstack.nio;

import java.io.IOException;



public interface RequestReader
{
	Response incoming( ServerSocket handler ) throws IOException;
}
