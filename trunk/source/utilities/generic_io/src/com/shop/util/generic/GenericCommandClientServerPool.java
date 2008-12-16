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

import com.shop.util.InterruptibleFutureTask;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages a pool of connections to a single {@link GenericCommandClientServer} server.<br>
 *
 * @author Jordan Zimmerman
 */
public class GenericCommandClientServerPool<T>
{
	/**
	 * Can be passed to the constructor as the value for retry_connection_ticks 
	 */
	public static final int				DEFAULT_RETRY_CONNECTION_TICKS = 60 * 1000;	// 1 minute

	/**
	 * Can be passed to the constructor as the value for keep_alive_ticks
	 */
	public static final int 			DEFAULT_KEEP_ALIVE_TICKS = 15 * 60 * 1000;	// 15 minutes

	/**
	 * Create the pool
	 *
	 * @param address server's address/hostname
	 * @param port server listening port
	 * @param listener comm notification object
	 * @param ssl if true, use SSL connections
	 * @param retryConnectionTicks number of ticks to wait before re-trying failed connections
	 * @param keepAliveTicks ticks after witch to close old connections
	 */
	public GenericCommandClientServerPool(String address, int port, GenericCommandClientServerListener<T> listener, boolean ssl, int retryConnectionTicks, int keepAliveTicks)
	{
		fAddress = address;
		fPort = port;
		fListener = listener;
		fSSL = ssl;
		fRetryConnectionTicks = retryConnectionTicks;
		fKeepAliveTicks = keepAliveTicks;
		fIdleNotificationTimeout = null;
		fNewConnectionStarter = null;

		fFreeList = new ConcurrentLinkedQueue<GenericCommandClientServer<T>>();
		fRelatedDataMap = new ConcurrentHashMap<GenericCommandClientServer<T>, RelatedData>();

		fState = new GenericCommandClientServerPoolState<T>();
		fLastKeepAliveCheck = new AtomicLong(System.currentTimeMillis());
	}

	/**
	 * Callback for starting client connections
	 *
	 * @param <T>
	 */
	public interface NewConnectionStarter<T>
	{
		/**
		 * Called when a new connection is created. The implementation is responsible for doing whatever
		 * initialization is needed and calling {@link GenericCommandClientServer#start()} for the given client.
		 *
		 * @param client the new client
		 * @throws Exception errors
		 */
		public void startNewConnection(GenericCommandClientServer<T> client) throws Exception;
	}

	/**
	 * Sets the new connection starter for this pool. If no starter is set, the pool will auto-start connections after creation.
	 *
	 * @param starter the starter
	 */
	public void 	setNewConnectionStarter(NewConnectionStarter<T> starter)
	{
		fNewConnectionStarter = starter;
	}

	/**
	 * Sets an idle notification timeout for clients created by the pool
	 *
	 * @param ticks idle ticks
	 * @see GenericCommandClientServer#setIdleNotificationTimeout(int)
	 */
	public void 			setIdleNotificationTimeout(int ticks)
	{
		fIdleNotificationTimeout = ticks;
	}

	/**
	 * Returns true if the pool is an open state
	 *
	 * @return true/false
	 */
	public boolean 		isOpen()
	{
		return fState.isOpen();
	}

	/**
	 * Returns true if the pool is an open state and there is no current reopener
	 *
	 * @return true/false
	 */
	public boolean 		isOpenAndNoReopener()
	{
		return fState.isOpenAndNoReopener();
	}

	/**
	 * Close all connections to the server and put this pool in a closed state
	 */
	public synchronized void				close()
	{
		InterruptibleFutureTask<GenericCommandClientServer<T>> localReopener = fState.close();
		if ( localReopener != null )
		{
			localReopener.interruptTask();
			try
			{
				GenericCommandClientServer<T> gen = localReopener.get();
				if ( gen != null )
				{
					gen.close();
				}
			}
			catch ( InterruptedException dummy )
			{
				Thread.currentThread().interrupt();
			}
			catch ( ExecutionException ignore )
			{
				// ignore
			}
		}

		closeAllConnections();
	}

	/**
	 * Attempt to reconnect to a server. An internal thread is started and will continue to attempt to reconnect periodically. Once
	 * reconnection is successful, {@link #get(AtomicReference)} will return connections. NOTE: if a reopen is currently in progress,
	 * this method does nothing.
	 *
	 * @return true if a new reopener was started - false if one already existed
	 */
	public boolean 		reopen()
	{
		InterruptibleFutureTask<GenericCommandClientServer<T>> 		task = new InterruptibleFutureTask<GenericCommandClientServer<T>>
		(
			new Callable<GenericCommandClientServer<T>>()
			{
				@Override
				public GenericCommandClientServer<T> call() throws Exception
				{
					GenericCommandClientServer<T> client = null;
					try
					{
						while ( client == null )
						{
							Thread.sleep(fRetryConnectionTicks);
							try
							{
								client = allocateNewConnection();
							}
							catch ( Exception ignore )
							{
								// still down - sleep and try again
							}
						}
					}
					catch ( InterruptedException dummy )
					{
						Thread.currentThread().interrupt();
					}

					return client;
				}
			}
		);

		boolean			result;
		if ( fState.checkSetReopener(task) )
		{
			closeAllConnections();
			GenericCommandClientServer.runInThread(task);
			result = true;
		}
		else
		{
			task.cancel(true);
			result = false;
		}
		return result;
	}

	/**
	 * Get a connection from the pool. IMPORTANT: each connection returned <b>must</b> released via a call to {@link #release(GenericCommandClientServer)}
	 *
	 * @param isNew if not null, will get set to true if the connection is brand new (i.e. not from the internal free list)
	 * @return the connection. Will return null if the pool is not in an open state
	 * @throws Exception any errors
	 */
	public GenericCommandClientServer<T> get(AtomicReference<Boolean> isNew) throws Exception
	{
		AtomicReference<Boolean> 								isOpen = new AtomicReference<Boolean>(false);
		InterruptibleFutureTask<GenericCommandClientServer<T>> 	localReopener = fState.getAndCheckDoneReopener(isOpen);
		if ( localReopener != null )
		{
			GenericCommandClientServer<T> gen = localReopener.isDone() ? localReopener.get() : null;
			if ( (gen != null) && (isNew != null) )
			{
				isNew.set(true);
			}
			return gen;
		}

		if ( !isOpen.get() )
		{
			return null;
		}

		GenericCommandClientServer<T> gen = fFreeList.poll();
		if ( gen == null )
		{
			if ( isNew != null )
			{
				isNew.set(true);
			}

			gen = allocateNewConnection();
		}

		closeStale();

		gen.updateLastUse();
		return gen;
	}

	/**
	 * Release a connection retrieved from {@link #get(AtomicReference<Boolean>)}
	 *
	 * @param gen the connection (cannot be null)
	 */
	public void				release(GenericCommandClientServer<T> gen)
	{
		assert gen != null;

		//noinspection ConstantConditions
		if ( gen == null )
		{
			return;
		}

		RelatedData related = fState.isOpenAndNoReopener() ? fRelatedDataMap.get(gen) : null;
		if ( (related != null) && !related.hadException )
		{
			gen.clearNew();
			fFreeList.add(gen);
		}
		else
		{
			removeAndClose(gen);
		}
	}

	private void closeStale()
	{
		if ( (System.currentTimeMillis() - fLastKeepAliveCheck.get()) > fKeepAliveTicks )
		{
			Iterator<GenericCommandClientServer<T>> iterator = fFreeList.iterator();
			while ( iterator.hasNext() )
			{
				GenericCommandClientServer<T> gen = iterator.next();
				if ( (System.currentTimeMillis() - gen.getLastUse()) > fKeepAliveTicks )
				{
					iterator.remove();
					removeAndClose(gen);
				}
			}
			fLastKeepAliveCheck.set(System.currentTimeMillis());
		}
	}

	private void removeAndClose(GenericCommandClientServer<T> gen)
	{
		fRelatedDataMap.remove(gen);
		gen.close();
	}

	private void closeAllConnections()
	{
		for ( GenericCommandClientServer<T> gen : fFreeList )
		{
			gen.close();
		}
		fFreeList.clear();
		fRelatedDataMap.clear();
	}

	private GenericCommandClientServer<T> allocateNewConnection() throws Exception
	{
		GenericCommandClientServer<T> gen = GenericCommandClientServer.makeClient
		(
			new GenericCommandClientServerListener<T>()
			{
				@Override
				public void notifyException(GenericCommandClientServer<T> gen, Exception e)
				{
					RelatedData related = fRelatedDataMap.get(gen);
					if ( related != null )
					{
						related.hadException = true;
					}
					fListener.notifyException(gen, e);
				}

				@Override
				public void notifyIdle(GenericCommandClientServer<T> client)
				{
					fListener.notifyIdle(client);
				}

				@Override
				public void notifyClientAccepted(GenericCommandClientServer<T> server, GenericCommandClientServer<T> client)
				{
				}

				@Override
				public void notifyClientServerClosed(GenericCommandClientServer<T> gen)
				{
					fRelatedDataMap.remove(gen);
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
			},
			fAddress,
			fPort,
			fSSL
		);

		RelatedData related = new RelatedData();
		fRelatedDataMap.put(gen, related);

		if ( fIdleNotificationTimeout != null )
		{
			gen.setIdleNotificationTimeout(fIdleNotificationTimeout);
		}

		if ( fNewConnectionStarter != null )
		{
			fNewConnectionStarter.startNewConnection(gen);
		}
		else
		{
			gen.start();
		}

		return gen;
	}

	private static class RelatedData
	{
		boolean 	hadException = false;
	}

	private final String 															fAddress;
	private final int 																fPort;
	private final GenericCommandClientServerListener<T> fListener;
	private final boolean 															fSSL;
	private final int 																fRetryConnectionTicks;
	private final int 																fKeepAliveTicks;
	private final ConcurrentLinkedQueue<GenericCommandClientServer<T>> 			fFreeList;
	private final ConcurrentHashMap<GenericCommandClientServer<T>, RelatedData> 	fRelatedDataMap;
	private final GenericCommandClientServerPoolState<T> 							fState;
	private final AtomicLong 														fLastKeepAliveCheck;
	private Integer 																fIdleNotificationTimeout;
	private volatile NewConnectionStarter<T> 										fNewConnectionStarter;
}
