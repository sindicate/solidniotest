package solidstack.nio;

import java.util.Map;
import java.util.WeakHashMap;

public class DebugId
{
	static private int id = 1;
	static private Map< Object, Integer > idMap = new WeakHashMap< Object, Integer >();

	static public int getId( Object object )
	{
		synchronized( idMap )
		{
			Integer id2 = idMap.get( object );
			if( id2 != null )
				return id2;

			int id3 = id++;
			idMap.put( object, id3 );
			return id3;
		}
	}
}
