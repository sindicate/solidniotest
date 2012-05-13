package solidstack.hyperdb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;

import solidstack.httpclient.Request;
import solidstack.httpclient.Response;
import solidstack.httpclient.ResponseProcessor;
import solidstack.httpclient.nio.Client;
import solidstack.io.FatalIOException;
import solidstack.nio.Dispatcher;

public class Test
{

// TODO Wow, test this
//	HTTP/1.1 301 MOVED PERMANENTLY
//	Location: http://www./

	static public void main( String[] args ) throws IOException, InterruptedException
	{
//		SocketChannel channel = SocketChannel.open( new InetSocketAddress( "www.nu.nl", 80 ) );
//		Assert.isTrue( channel.isConnected() );
//		channel.configureBlocking( false );
//		byte[] bytes = "Dit is een string".getBytes();
//		ByteBuffer buffer = ByteBuffer.wrap( bytes );
//		channel.write( buffer );
//		channel.close();
//
//		Assert.fail();

		Dispatcher dispatcher = new Dispatcher();
		dispatcher.start();

		Client client = new Client( "www.nu.nl", 80, dispatcher );

		//Host: www.nu.nl
		//Connection: keep-alive
		//Cache-Control: max-age=0
		//User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.162 Safari/535.19
		//Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
		//Accept-Encoding: gzip,deflate,sdch
		//Accept-Language: en-US,nl-NL;q=0.8,en;q=0.6,nl;q=0.4
		//Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.3
		//If-Modified-Since: Fri, 27 Apr 2012 17:52:18 GMT

		Request request = new Request( "/" );
		request.setHeader( "Host", "www.nu.nl" );

		ResponseProcessor processor = new ResponseProcessor()
		{
			public void process( Response response )
			{
				System.out.println( response.getHttpVersion() + " " + response.getStatus() + " " + response.getReason() );
				Map<String, String> headers = response.getHeaders();
				for( Entry<String, String> entry : headers.entrySet() )
					System.out.println( entry.getKey() + ": " + entry.getValue() );
				System.out.println();

				InputStream in = response.getInputStream();
				if( in == null )
					return;
				try
				{
//					FileOutputStream out = new FileOutputStream( "test.out" );
//					try
//					{
						int i = in.read();
						while( i >= 0 )
						{
//							out.write( i );
							i = in.read();
						}
//					}
//					finally
//					{
//						out.close();
//					}
				}
				catch( IOException e )
				{
					throw new FatalIOException( e );
				}
			}

			public void timeout()
			{
				System.out.println( "Request timed out" );
			}
		};

		client.request( request, processor );
		client.request( request, processor );
		client.request( request, processor );

		Thread.sleep( 10000 );

		System.out.println( "interrupting" );
		dispatcher.shutdown();
//		dispatcher.join();
	}
}
