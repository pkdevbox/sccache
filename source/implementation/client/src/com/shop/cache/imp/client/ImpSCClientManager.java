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
package com.shop.cache.imp.client;

import com.shop.cache.api.client.io.SCClient;
import com.shop.cache.api.client.io.SCClientContext;
import com.shop.cache.api.client.io.SCClientManager;
import com.shop.cache.api.common.SCDataSpec;
import com.shop.cache.api.common.SCGroupSpec;
import com.shop.cache.api.common.SCNotifications;
import com.shop.cache.api.common.SCGroup;
import com.shop.cache.imp.common.ImpSCUtils;
import com.shop.util.chunked.ChunkedByteArray;
import com.shop.util.generic.GenericCommandClientServer;
import com.shop.util.generic.GenericCommandClientServerPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.ArrayList;

/**
 * SHOP.COM's Client Manager implementation
 *
 * @author Jordan Zimmerman
 */
class ImpSCClientManager implements SCClientManager
{
	ImpSCClientManager(SCClientContext context)
	{
		fContext = context;
		fLastException = new AtomicReference<Exception>(null);

		fPool = new GenericCommandClientServerPool<ImpSCClient>(fContext.getAddress().getHostName(), fContext.getAddress().getPort(), ImpSCClient.getListener(), false, GenericCommandClientServerPool.DEFAULT_RETRY_CONNECTION_TICKS, GenericCommandClientServerPool.DEFAULT_KEEP_ALIVE_TICKS);
		fPool.setIdleNotificationTimeout(ImpSCUtils.IDLE_NOTIFICATION_TICKS);
		fPool.setNewConnectionStarter
		(
			new GenericCommandClientServerPool.NewConnectionStarter<ImpSCClient>()
			{
				@Override
				public void startNewConnection(GenericCommandClientServer<ImpSCClient> client) throws Exception
				{
					ImpSCClient 		localClient = new ImpSCClient(ImpSCClientManager.this, client, fContext);
					client.setUserValue(localClient);
					client.start();
					localClient.hello();
				}
			}
		);
	}

	@Override
	public SCNotifications getNotificationHandler()
	{
		return fContext.getNotificationHandler();
	}

	@Override
	public List<String> removeGroup(SCGroup group) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.removeGroup(group) : new ArrayList<String>();
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public List<String> listGroup(SCGroup group) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.listGroup(group) : new ArrayList<String>();
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public List<String> dumpStats(boolean verbose) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.dumpStats(verbose) : new ArrayList<String>();
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public List<String> stackTrace() throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.stackTrace() : new ArrayList<String>();
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public List<String> getConnectionList() throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.getConnectionList() : new ArrayList<String>();
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public List<String> regExRemove(String expression) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.regExRemove(expression) : new ArrayList<String>();
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public void writeKeyData(String fPath) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			if ( client != null )
			{
				client.writeKeyData(fPath);
			}
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public long getTTL(String key) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.getTTL(key) : 0;
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public ChunkedByteArray get(String key, boolean ignoreTTL) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			return (client != null) ? client.get(key, ignoreTTL) : null;
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public void put(String key, SCDataSpec data, SCGroupSpec groups) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			if ( client != null )
			{
				client.put(key, data, groups);
			}
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public void remove(String key) throws Exception
	{
		SCClient 			client = getClient();
		try
		{
			if ( client != null )
			{
				client.remove(key);
			}
		}
		finally
		{
			releaseClient(client);
		}
	}

	@Override
	public void registerException(Exception e)
	{
		fLastException.set(e);
	}

	@Override
	public boolean serverIsDown()
	{
		return !fPool.isOpenAndNoReopener();
	}

	@Override
	public SCClient getClient() throws Exception
	{
		ImpSCClient client = null;
		try
		{
			GenericCommandClientServer<ImpSCClient> 	gen = fPool.get(null);
			if ( gen == null )
			{
				if ( fPool.isOpen() )
				{
					reopen();
				}
			}
			else
			{
				client = gen.getUserValue();
			}
		}
		catch ( Exception e )
		{
			registerException(e);
		}

		return client;
	}

	@Override
	public String toString()
	{
		return fContext.getAddress().getHostName() + ":" + fContext.getAddress().getPort();
	}

	@Override
	public void releaseClient(SCClient client)
	{
		if ( client != null )
		{
			if ( !(client instanceof ImpSCClient) )
			{
				throw new IllegalArgumentException("Only clients allocated via getClient() can be released.");
			}

			ImpSCClient 		impClient = (ImpSCClient)client;
			fPool.release(impClient.getGen());
		}
	}

	@Override
	public void close()
	{
		fPool.close();
	}

	@Override
	public Exception getAndClearLastException()
	{
		return fLastException.getAndSet(null);
	}

	private void reopen()
	{
		if ( fPool.reopen() )
		{
			if ( fContext.getNotificationHandler() != null )
			{
				fContext.getNotificationHandler().notifyServerDown(toString());
			}
		}
	}

	private final SCClientContext 								fContext;
	private final AtomicReference<Exception> 					fLastException;
	private final GenericCommandClientServerPool<ImpSCClient> 	fPool;
}
