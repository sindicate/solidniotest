package solidstack.nio;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class MyLinkedList<T>
{
	static public class Entry<T>
	{
		Entry<T> previous;
		Entry<T> next;
		T item;
		long lastPooled;
	}

	private Entry<T> head;
	private Map<T, Entry<T>> all = new HashMap<T, Entry<T>>();
	private int size;

	public void addHead( T item )
	{
		Entry<T> entry = new Entry<T>();
		entry.next = this.head;
		entry.item = item;
		entry.lastPooled = System.currentTimeMillis();
		this.all.put( item, entry );
		this.head = entry;
		this.size ++;
	}

	public T peekHead()
	{
		if( this.head == null )
			return null;
		return this.head.item;
	}

	public void stashHead()
	{
		Entry<T> head = this.head;
		this.head = head.next;
		head.lastPooled = 0;
		this.size --;
	}

	public T pollHead()
	{
		Entry<T> head = this.head;
		if( head == null )
			return null;
		this.head = head.next;
		this.size--;
		head.lastPooled = 0;
		return head.item;
	}

	public int size()
	{
		return this.size;
	}

	public int all()
	{
		return this.all.size();
	}

	public void unstash( T item )
	{
		Entry<T> entry = this.all.get( item );
		entry.previous = null;
		entry.next = this.head;
		entry.lastPooled = System.currentTimeMillis();
		this.head = entry;
		this.size ++;
	}

	public void remove( T item )
	{
		Entry<T> entry = this.all.remove( item );
		if( entry != null )
			if( entry.lastPooled != 0 )
			{
				if( entry.previous != null )
					entry.previous.next = entry.next;
				else
					this.head = entry.next; // No previous means it was the head
				if( entry.next != null )
					entry.next.previous = entry.previous;
				this.size --;
			}
	}

	public T moveHeadToStash( MyLinkedList<T> other )
	{
		Entry<T> head = this.head;
		if( head == null )
			return null;

		this.all.remove( head.item );

		this.head = head.next;
		this.size--;
		head.lastPooled = 0;

		other.addStashed( head );

		return head.item;
	}

	private void addStashed( Entry<T> entry )
	{
		this.all.put( entry.item, entry );
		entry.lastPooled = 0;
	}

	public Set<T> getAll()
	{
		return this.all.keySet();
	}
}
