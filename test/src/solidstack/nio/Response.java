package solidstack.nio;

import java.io.OutputStream;


/*
 * Types of responses:
 * 1. Ready, does not need input anymore ->
 * 2. Ready, needs input still
 * 3. Not ready, does not need input anymore
 */
abstract public class Response
{
	private boolean ready;
	private ServerSocket socket;

	abstract public void write( OutputStream out );

	public boolean needsInput()
	{
		return false;
	}

	public boolean isReady()
	{
		return this.ready;
	}

	public void ready()
	{
		this.socket.responseIsReady( this );
	}

	public void setReady()
	{
		this.ready = true;
	}
}
