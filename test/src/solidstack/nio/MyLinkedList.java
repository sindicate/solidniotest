package solidstack.nio;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import solidstack.lang.Assert;


public class MyLinkedList<T>
{
	static public class Entry<T>
	{
		Entry<T> previous; // TODO Maybe we do not need previous in the end
		Entry<T> next;
		T item;
		long lastPooled;
	}

	private Entry<T> head;
	private Map<T, Entry<T>> all = new HashMap<T, Entry<T>>();
	private int size;

	// checked
	public void addHead( T item )
	{
		Entry<T> entry = new Entry<T>();
		entry.item = item;
		addHead( entry );
	}

	// checked
	public T peekHead()
	{
		if( this.head == null )
			return null;
		return this.head.item;
	}

	// checked
	public void stashHead()
	{
		Entry<T> head = this.head;
		this.head = head.next;
		if( this.head != null )
			this.head.previous = null;
		head.lastPooled = 0;
		this.size --;
//		checkSize( "stashHead" );
//		Assert.isTrue( this.size >= 0 );
	}

//	public T pollHead()
//	{
//		Entry<T> head = this.head;
//		if( head == null )
//			return null;
//		this.head = head.next;
//		this.size--;
//		head.lastPooled = 0;
//		return head.item;
//	}

	// checked
	public int size()
	{
		return this.size;
	}

	// checked
	public int all()
	{
		return this.all.size();
	}

	// checked
	public void unstash( T item )
	{
		Entry<T> entry = this.all.get( item );
		if( entry != null && entry.lastPooled == 0 )
		{
			entry.previous = null;
			entry.next = this.head;
			entry.lastPooled = System.currentTimeMillis();
			if( this.head != null )
				this.head.previous = entry;
			this.head = entry;
			this.size ++;
//			checkSize( "unstash" );
//			Assert.isTrue( this.size <= all() );
		}
	}

	// checked
	public void remove( T item )
	{
		remove0( item );
	}

	// checked
	private Entry<T> remove0( T item )
	{
		Entry<T> entry = this.all.remove( item );
		if( entry != null )
			if( entry.lastPooled != 0 )
			{
				entry.lastPooled = 0; // TODO Should not be needed
				if( entry.previous != null )
					entry.previous.next = entry.next;
				else
					this.head = entry.next; // No previous means it was the head
				if( entry.next != null )
					entry.next.previous = entry.previous;
				this.size --;
//				checkSize( "remove0" );
//				Assert.isTrue( this.size >= 0 );
			}
		return entry;
	}

	// checked
	public T moveHeadToStash( MyLinkedList<T> other )
	{
		Entry<T> head = this.head;
		if( head == null )
			return null;

		this.all.remove( head.item );

		this.head = head.next;
		if( this.head != null )
			this.head.previous = null;
		this.size--;
//		checkSize( "moveHeadToStash" );
//		Assert.isTrue( this.size >= 0 );
		head.lastPooled = 0;

		other.addStashed( head );

		return head.item;
	}

//	private void checkSize( String where )
//	{
//		int count = 0;
//		Entry<T> last = null;
//		Entry<T> item = this.head;
//		while( item != null )
//		{
//			last = item;
//			count++;
//			item = item.next;
//		}
//		Assert.isTrue( count == this.size, where + ": count(1) != this.size: " + count + " != " + this.size );
//
//		count = 0;
//		item = last;
//		while( item != null )
//		{
//			count++;
//			item = item.previous;
//		}
//		Assert.isTrue( count == this.size, where + ": count(2) != this.size: " + count + " != " + this.size );
//	}

	// checked
	public T moveHeadTo( MyLinkedList<T> other )
	{
		Entry<T> head = this.head;
		if( head == null )
			return null;

		this.all.remove( head.item );

		this.head = head.next;
		if( this.head != null )
			this.head.previous = null;
		this.size--;
//		checkSize( "moveHeadTo" );
//		Assert.isTrue( this.size >= 0 );
		head.lastPooled = 0; // TODO Should not be necessary

		other.addHead( head );

		return head.item;
	}

	// checked
	public void moveTo( T item, MyLinkedList<T> other )
	{
		Entry<T> entry = remove0( item );
		if( entry != null )
			other.addHead( entry );
	}

	// checked
	private void addStashed( Entry<T> entry )
	{
		this.all.put( entry.item, entry );
		entry.lastPooled = 0;
	}

	// checked
	private void addHead( Entry<T> entry )
	{
		this.all.put( entry.item, entry );
		entry.previous = null;
		entry.next = this.head;
		entry.lastPooled = System.currentTimeMillis();
		if( this.head != null )
			this.head.previous = entry;
		this.head = entry;
		this.size ++;
//		checkSize( "addHead" );
//		Assert.isTrue( this.size <= all() );
	}

	// checked
	public Set<T> getAll()
	{
		return this.all.keySet();
	}

	public Entry<T> timeout()
	{
		long now = System.currentTimeMillis();

		Entry<T> entry = this.head;

		// TODO Maximum number of timeouts per occurrence or use closer thread
		// TODO Max this period configurable
		while( entry != null && entry.lastPooled + 10000 > now )
			entry = entry.next;
		// expired or null

		Entry<T> result = entry;

		if( entry != null )
		{
			if( entry.previous != null )
			{
				// entry is the first one that is timing out
				entry.previous.next = null;
				entry.previous = null; // TODO Should not be really needed
			}
			else
			{
				// They are all timing out
				this.head = null;
			}

			while( entry != null )
			{
				Assert.notNull( this.all.remove( entry.item ) );
//				Assert.isTrue( e == entry, e != null ? e.toString() : "null" );
				this.size --;
//				Assert.isTrue( this.size >= 0 );

				entry = entry.next;
			}

//			checkSize( "timeout" );
		}

		return result;
	}
}
