package solidstack.monetdb.jdbc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import solidstack.lang.SystemException;


public class Test
{
	public static void main( String[] args ) throws IOException
	{
		Socket socket = new Socket( "localhost", 50000 );
		BlockInputStream in = new BlockInputStream( socket.getInputStream() );
		final BlockOutputStream out = new BlockOutputStream( socket.getOutputStream() );

//		SocketMachine machine = new SocketMachine();
//		machine.start();
//		ClientSocket client = new ClientSocket( "localhost", 50000, machine );
//		Socket socket = client.getSocket();
//		OutputStream out = socket.getOutputStream();
//		out.write( 0 );
//		out.flush();
//		InputStream in = socket.getInputStream();

		String challenge = in.readBlock();

		String response = getChallengeResponse( challenge, "monetdb", "monetdb", "sql", "demo", null );
		out.writeBlock( response );
		out.flush();

		in.readBlock();

		out.writeBlock( "Xauto_commit 0" );
		in.readBlock();
		out.writeBlock( "Xreply_size 10000" ); // Xexport %s %s %s ?
		in.readBlock();
		out.writeBlock( "sprepare select * from tables where name = ?;" );
		in.readBlock();
		out.writeBlock( "sexec 0 ('sequences');" );
		in.readBlock();
		out.writeBlock( "sselect * from tables;" );
		in.readBlock();
		out.writeBlock( "scommit;" );
		in.readBlock();
//		out.writeBlock( "screate table test ( test varchar(1000) );" );
//		in.readBlock();
		out.writeBlock( "sdelete from test;" );
		in.readBlock();
//		out.writeBlock( "sprepare insert into test values ( ? );" );
//		in.readBlock();
		Thread thread = new Thread()
		{
			@Override
			public void run()
			{
				for( int i = 0; i < 1000; i++ )
				{
//					System.out.println( "-->" + i );
//					out.writeBlock( "sexec 2 ( 'test' );" );
					out.writeBlock( "sinsert into test values ( 'test' );" );
				}
			}
		};
		thread.start();
		for( int i = 0; i < 1000; i++ )
		{
//			System.out.println( "<--" + i );
			in.readBlock();
		}
//		thread.join();
		out.writeBlock( "sexec 0 ( 'test' );" );
		in.readBlock();
		out.writeBlock( "scommit;" );
		in.readBlock();
		out.writeBlock( "sselect * from test;" );
		in.readBlocks();
		out.writeBlock( "Xexport 3 500 100" );
		in.readBlocks();
		out.writeBlock( "Xexport 3 200 1000" );
		in.readBlocks();

//		machine.shutdown();
	}

	static private String getChallengeResponse( String chalstr, String username, String password, String language,
			String database, String hash ) throws IOException
	{
		String response;
		String algo;

		// parse the challenge string, split it on ':'
		String[] chaltok = chalstr.split( ":" );
		if( chaltok.length <= 4 )
			throw new SystemException( "Server challenge string unusable!  Challenge contains too few tokens: "
					+ chalstr );

		// challenge string to use as salt/key
		String challenge = chaltok[ 0 ];
		String servert = chaltok[ 1 ];
		int version;
		try
		{
			version = Integer.parseInt( chaltok[ 2 ].trim() ); // protocol version
		}
		catch( NumberFormatException e )
		{
			throw new SystemException( "Protocol version unparseable: " + chaltok[ 3 ] );
		}

		// handle the challenge according to the version it is
		switch( version )
		{
			default:
				throw new SystemException( "Unsupported protocol version: " + version );
			case 9:
				// proto 9 is like 8, but uses a hash instead of the
				// plain password, the server tells us which hash in the
				// challenge after the byte-order

				/* NOTE: Java doesn't support RIPEMD160 :( */
				if( chaltok[ 5 ].equals( "SHA512" ) )
				{
					algo = "SHA-512";
				}
				else if( chaltok[ 5 ].equals( "SHA384" ) )
				{
					algo = "SHA-384";
				}
				else if( chaltok[ 5 ].equals( "SHA256" ) )
				{
					algo = "SHA-256";
					/* NOTE: Java doesn't support SHA-224 */
				}
				else if( chaltok[ 5 ].equals( "SHA1" ) )
				{
					algo = "SHA-1";
				}
				else if( chaltok[ 5 ].equals( "MD5" ) )
				{
					algo = "MD5";
				}
				else
				{
					throw new SystemException( "Unsupported password hash: " + chaltok[ 5 ] );
				}

				try
				{
					MessageDigest md = MessageDigest.getInstance( algo );
					md.update( password.getBytes( "UTF-8" ) );
					byte[] digest = md.digest();
					password = toHex( digest );
				}
				catch( NoSuchAlgorithmException e )
				{
					throw new AssertionError( "internal error: " + e.toString() );
				}
				catch( UnsupportedEncodingException e )
				{
					throw new AssertionError( "internal error: " + e.toString() );
				}
			case 8:
				// proto 7 (finally) used the challenge and works with a
				// password hash.  The supported implementations come
				// from the server challenge.  We chose the best hash
				// we can find, in the order SHA1, MD5, plain.  Also,
				// the byte-order is reported in the challenge string,
				// which makes sense, since only blockmode is supported.
				// proto 8 made this obsolete, but retained the
				// byte-order report for future "binary" transports.  In
				// proto 8, the byte-order of the blocks is always little
				// endian because most machines today are.
				String hashes = hash == null ? chaltok[ 3 ] : hash;
				Set<String> hashesSet = new HashSet<String>( Arrays.asList( hashes.toUpperCase().split( "[, ]" ) ) );

				// if we deal with merovingian, mask our credentials
				if( servert.equals( "merovingian" ) && !language.equals( "control" ) )
				{
					username = "merovingian";
					password = "merovingian";
				}
				String pwhash;
				algo = null;

				if( hashesSet.contains( "SHA512" ) )
				{
					algo = "SHA-512";
					pwhash = "{SHA512}";
				}
				else if( hashesSet.contains( "SHA384" ) )
				{
					algo = "SHA-384";
					pwhash = "{SHA384}";
				}
				else if( hashesSet.contains( "SHA256" ) )
				{
					algo = "SHA-256";
					pwhash = "{SHA256}";
				}
				else if( hashesSet.contains( "SHA1" ) )
				{
					algo = "SHA-1";
					pwhash = "{SHA1}";
				}
				else if( hashesSet.contains( "MD5" ) )
				{
					algo = "MD5";
					pwhash = "{MD5}";
				}
				else if( version == 8 && hashesSet.contains( "PLAIN" ) )
				{
					pwhash = "{plain}" + password + challenge;
				}
				else
				{
					throw new SystemException( "no supported password hashes in " + hashes );
				}
				if( algo != null )
				{
					try
					{
						MessageDigest md = MessageDigest.getInstance( algo );
						md.update( password.getBytes( "UTF-8" ) );
						md.update( challenge.getBytes( "UTF-8" ) );
						byte[] digest = md.digest();
						pwhash += toHex( digest );
					}
					catch( NoSuchAlgorithmException e )
					{
						throw new AssertionError( "internal error: " + e.toString() );
					}
					catch( UnsupportedEncodingException e )
					{
						throw new AssertionError( "internal error: " + e.toString() );
					}
				}
				// TODO: some day when we need this, we should store
				// this
				if( chaltok[ 4 ].equals( "BIG" ) )
				{
					// byte-order of server is big-endian
				}
				else if( chaltok[ 4 ].equals( "LIT" ) )
				{
					// byte-order of server is little-endian
				}
				else
				{
					throw new SystemException( "Invalid byte-order: " + chaltok[ 5 ] );
				}

				// generate response
				response = "BIG:"; // JVM byte-order is big-endian
				response += username + ":" + pwhash + ":" + language;
				response += ":" + ( database == null ? "" : database ) + ":";

				return response;
		}
	}

	private static String toHex(byte[] digest) {
		char[] result = new char[digest.length * 2];
		int pos = 0;
		for (int i = 0; i < digest.length; i++) {
			result[pos++] = hexChar((digest[i] & 0xf0) >> 4);
			result[pos++] = hexChar(digest[i] & 0x0f);
		}
		return new String(result);
	}

	private static char hexChar(int n) {
		return n > 9
				? (char) ('a' + (n - 10))
				: (char) ('0' + n);
	}
}
