/*
 * Copyright 2008 SHOP.COM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shop.util.generic;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Manages connections to a farm of servers (that implement the {@link GenericCommandClientServer} protocol).<br>
 *
 * The boilerplate code for using this class is:
<code><pre>
farm = new generic_command_client_server_farm&lt;MyClass&gt;(addresses, myListener, ssl);

// ...

try
{
	for ( generic_command_client_server&lt;MyClass&gt; connection : farm )
	{
		try
		{
 			// do work with the connection

 			// if connection is successful, break out of the loop
		}
		catch ( Exception e )
		{
 			// let the farm know that there was an error
			farm.set_exception(e);
		}
	}
}
finally
{
 	// must be in a finally block so as not to leak any connections
	farm.check_exception();		// can throw an exception
}

</pre></code>
 * @author Jordan Zimmerman
 */
public class GenericCommandClientServerFarm<T> implements Iterable<GenericCommandClientServer<T>>
{
	/**
	 * Init the farm
	 *
	 * @param addresses list of each server address
	 * @param listener the standard generic_command_client_server listener
	 * @param ssl if true, SSL clients are created
	 */
	public GenericCommandClientServerFarm(List<InetSocketAddress> addresses, GenericCommandClientServerListener<T> listener, boolean ssl)
	{
		fListener = listener;
		fSSL = ssl;
		fIsOpen = true;

		fReverseMap = new HashMap<GenericCommandClientServer<T>, ConnectionData>();
		fServers = new HashMap<ServerData, List<GenericCommandClientServer<T>>>();
		for ( InetSocketAddress address : addresses )
		{
			ServerData data = new ServerData();
			data.address = address;
			data.lastExceptionTicks = 0;
			fServers.put(data, new LinkedList<GenericCommandClientServer<T>>());
		}
	}

	/**
	 * Returns an iterator over the servers in the farm. Any servers that have had connection problems recently
	 * will not be used (however, they will be tried again within a period of time). The iterator manages allocating/releasing
	 * the connections internally. However, you must call {@link #checkException()} in a finally block to ensure no resource leaks.
	 * If your work with the returned connection is successful, break out of the loop.
	 *
	 * @return a connection iterator (compatible with the "for-each" syntax)
	 */
	@Override
	public Iterator<GenericCommandClientServer<T>> iterator()
	{
		final IteratorData 		idata = fIteratorData.get();
		idata.exception = null;
		idata.connection = null;
		if ( idata.servers.size() > 0 )
		{
			idata.servers.add(idata.servers.remove(0));	// this acheives round robin
		}

		final Iterator<ServerData> 		localIterator = idata.servers.iterator();
		return new Iterator<GenericCommandClientServer<T>>()
		{
			@Override
			public boolean hasNext()
			{
				releaseIData(idata);

				while ( (idata.connection == null) && localIterator.hasNext() )
				{
					ServerData data = localIterator.next();
					if ( !hasActiveException(data) )
					{
						try
						{
							idata.connection = getConnection(data);
						}
						catch ( Exception e )
						{
							idata.exception = e;
							fListener.notifyException(null, e);
							data.lastExceptionTicks = System.currentTimeMillis();
						}
					}
				}

				if ( idata.connection != null )
				{
					idata.exception = null;
				}

				return (idata.connection != null);
			}

			@Override
			public GenericCommandClientServer<T> next()
			{
				return idata.connection;
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Must be called in a finally block after using {@link #iterator()}
	 *
	 * @throws Exception if there were any exceptions during iteration
	 */
	public void 	checkException() throws Exception
	{
		IteratorData 		idata = fIteratorData.get();
		releaseIData(idata);
		if ( idata.exception != null )
		{
			throw idata.exception;
		}
	}

	/**
	 * If an exception occurs on a connection that your code catches, call this method to notify the farm
	 *
	 * @param e the exception
	 */
	public void 	setException(Exception e)
	{
		IteratorData 		idata = fIteratorData.get();
		idata.exception = e;
	}

	/**
	 * Close any connections cached in the farm
	 */
	public synchronized void		close()
	{
		if ( !fIsOpen )
		{
			return;
		}
		fIsOpen = false;

		for ( List<GenericCommandClientServer<T>> list : fServers.values() )
		{
			for ( GenericCommandClientServer<T> connection : list )
			{
				connection.close();
			}
			list.clear();
		}
	}

	/**
	 * Return the list of servers that are currently down (if any)
	 *
	 * @return server list (may be 0 length)
	 */
	public List<InetSocketAddress> 	getDownServers()
	{
		List<InetSocketAddress>		down = new ArrayList<InetSocketAddress>();

		List<ServerData> 			serverList;
		synchronized(this)
		{
			serverList = new ArrayList<ServerData>(fServers.keySet());
		}

		for ( ServerData data : serverList )
		{
			if ( data.lastExceptionTicks != 0 )
			{
				down.add(data.address);
			}
		}

		return down;
	}

	private void releaseIData(IteratorData idata)
	{
		if ( idata.connection != null )
		{
			try
			{
				release(idata.connection);
			}
			catch ( Exception e )
			{
				idata.connection = null;
				idata.exception = e;
			}
			idata.connection = null;
		}
	}

	private synchronized void				release(GenericCommandClientServer<T> gen) throws Exception
	{
		ConnectionData 		cdata = fReverseMap.get(gen);
		if ( cdata != null )
		{
			if ( cdata.data.lastExceptionTicks != 0 )
			{
				removeAndClose(gen);
			}
			else
			{
				releaseConnection(cdata.data, gen);
			}
		}
	}

	private boolean hasActiveException(ServerData data)
	{
		if ( data.lastExceptionTicks != 0 )
		{
			if ( (System.currentTimeMillis() - data.lastExceptionTicks) >= EXCEPTION_RECHECK_TICKS )
			{
				data.lastExceptionTicks = 0;
			}
		}

		return (data.lastExceptionTicks != 0);
	}

	private synchronized void releaseConnection(ServerData data, GenericCommandClientServer<T> connection) throws Exception
	{
		if ( fIsOpen )
		{
			List<GenericCommandClientServer<T>> 		connectionList = fServers.get(data);
			connectionList.add(connection);
		}
		else
		{
			connection.close();
		}
	}

	private GenericCommandClientServer<T> getConnection(ServerData data) throws Exception
	{
		GenericCommandClientServer<T> connection = null;
		int 										remainingCachedConnections = 0;
		synchronized(this)
		{
			if ( fIsOpen )
			{
				List<GenericCommandClientServer<T>> 	connectionList = fServers.get(data);
				connection = (connectionList.size() > 0) ? connectionList.remove(0) : null;
				remainingCachedConnections = fServers.size();
			}
		}

		if ( connection == null )
		{
			connection = GenericCommandClientServer.makeClient(new InternalListener(), data.address.getHostName(), data.address.getPort(), fSSL);
			connection.start();

			ConnectionData 		cdata = new ConnectionData();
			cdata.data = data;
			cdata.lastUseTicks = System.currentTimeMillis();
			synchronized(this)
			{
				fReverseMap.put(connection, cdata);
			}
		}
		else
		{
			ConnectionData cdata;
			synchronized(this)
			{
				cdata = fReverseMap.get(connection);
			}
			if ( cdata != null )
			{
				if ( (remainingCachedConnections > MINIMUM_CONNECTIONS) && ((System.currentTimeMillis() - cdata.lastUseTicks) > STALE_CONNECTION_TICKS) )
				{
					removeAndClose(connection);
					return getConnection(data);
				}
				cdata.lastUseTicks = System.currentTimeMillis();
			}
			else
			{
				assert false;	// should never get here
			}
		}

		return connection;
	}

	private synchronized void removeConnection(GenericCommandClientServer<T> gen, boolean from_exception)
	{
		for ( ServerData data : fServers.keySet() )
		{
			Iterator<GenericCommandClientServer<T>> iterator = fServers.get(data).iterator();
			while ( iterator.hasNext() )
			{
				GenericCommandClientServer<T> connection = iterator.next();
				if ( connection == gen )
				{
					if ( from_exception )
					{
						data.lastExceptionTicks = System.currentTimeMillis();
					}
					iterator.remove();
					break;
				}
			}
		}

		removeAndClose(gen);
	}

	private void 	removeAndClose(GenericCommandClientServer<T> gen)
	{
		gen.close();
		synchronized(this)
		{			
			fReverseMap.remove(gen);
		}
	}

	private class InternalListener implements GenericCommandClientServerListener<T>
	{
		@Override
		public void notifyException(GenericCommandClientServer<T> gen, Exception e)
		{
			removeConnection(gen, true);
			fListener.notifyException(gen, e);
		}

		@Override
		public void notifyIdle(GenericCommandClientServer<T> client)
		{
		}

		@Override
		public void notifyClientAccepted(GenericCommandClientServer<T> server, GenericCommandClientServer<T> client)
		{
			// this is a client only - will never get here
		}

		@Override
		public void notifyClientServerClosed(GenericCommandClientServer<T> gen)
		{
			removeConnection(gen, false);
			fListener.notifyClientServerClosed(gen);
		}

		@Override
		public void notifyLineReceived(GenericCommandClientServer<T> client, String line, GenericCommandClientServerReader reader) throws Exception
		{
			fListener.notifyLineReceived(client, line, reader);
		}

		@Override
		public InputStream notifyCreatingInputStream(GenericCommandClientServer<T> client, InputStream in) throws Exception
		{
			return fListener.notifyCreatingInputStream(client, in);
		}

		@Override
		public OutputStream notifyCreatingOutputStream(GenericCommandClientServer<T> client, OutputStream out) throws Exception
		{
			return fListener.notifyCreatingOutputStream(client, out);
		}
	}

	private static class ServerData
	{
		InetSocketAddress	address;
		long 				lastExceptionTicks;
	}

	private static class ConnectionData
	{
		ServerData 			data;
		long 				lastUseTicks;
	}

	private static final int		EXCEPTION_RECHECK_TICKS = 5 * 60 * 1000;	// 5 minutes
	private static final int		STALE_CONNECTION_TICKS = 5 * 60 * 1000;	// 5 minutes
	private static final int		MINIMUM_CONNECTIONS = 5;

	private class IteratorData
	{
		Exception 							exception = null;
		GenericCommandClientServer<T> connection = null;
		final List<ServerData>				servers = new LinkedList<ServerData>(fServers.keySet());
	}

	private final ThreadLocal<IteratorData> 	fIteratorData = new ThreadLocal<IteratorData>()
	{
		@Override
		protected IteratorData initialValue()
		{
			return new IteratorData();
		}
	};

	private final Map<ServerData, List<GenericCommandClientServer<T>>> 	fServers;
	private final Map<GenericCommandClientServer<T>, ConnectionData> 	fReverseMap;
	private final GenericCommandClientServerListener<T> 					fListener;
	private final boolean 													fSSL;
	private boolean 														fIsOpen;
}
