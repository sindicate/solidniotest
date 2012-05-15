package solidstack.nio;


public class ServerSocket extends Socket
{
	public ServerSocket( SocketMachine machine )
	{
		super( true, machine );
	}

	@Override
	public void setReader( ResponseReader reader )
	{
		super.setReader( reader );
		getMachine().listenAccept( getKey() );
	}
}
