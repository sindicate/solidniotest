package solidstack.nio;

import java.io.IOException;

import solidstack.httpserver.Response;



public interface RequestReader
{
	Response incoming( ServerSocket handler ) throws IOException;
}
