/*
 * Copyright 2008-2009 SHOP.COM
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
package com.shop.cache.api.client.main;

import com.shop.cache.api.client.io.SCManager;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default memory cache implementation. This implementation
 * is based on SoftReferences. It does not limit the number of
 * objects cached in memory. Instead it relies on the JVM to handle
 * the SoftReferences.
 *
 * @author Jordan Zimmerman
 */
class DefaultMemoryCache implements SCMemoryCache
{
	/**
	 * Link back to the manager being used
	 *
	 * @param manager the manager
	 */
	DefaultMemoryCache(SCManager manager)
	{
		fManager = manager;
		fMemoryCache = new ConcurrentHashMap<String, SoftReference<SCDataBlock>>();
		fMemoryCacheKeys = Collections.newSetFromMap(new ConcurrentHashMap());
		fQueue = new ReferenceQueue<SCDataBlock>();

		Thread		purgeThread = new Thread
		(
			new Runnable()
			{
				@Override
				public void run()
				{
					stalePurgeLoop();
				}
			},
			"DefaultMemoryCache purge thread"
		);
		purgeThread.setDaemon(true);
		purgeThread.start();
	}

	@Override
	public void put(SCDataBlock block)
	{
		removeOldReferences();

		fMemoryCache.put(block.getKey(), new SoftReference<SCDataBlock>(block, fQueue));
		fMemoryCacheKeys.add(block.getKey());
	}

	@Override
	public SCDataBlock get(String key)
	{
		removeOldReferences();

		SoftReference<SCDataBlock> 		memoryBlockRef = fMemoryCache.get(key);
		return (memoryBlockRef != null) ? memoryBlockRef.get() : null;
	}

	/**
	 * DefaultMemoryCache uses a background thread that periodically checks the TTL of
	 * objects in memory against the main cache. Stale objects are removed from memory
	 */
	private void	stalePurgeLoop()
	{
		for(;;)
		{
			try
			{
				Thread.sleep(STALE_PURGE_SLEEP_TICKS);
			}
			catch ( InterruptedException e )
			{
				Thread.currentThread().interrupt();
				break;
			}

			// calling ConcurrentHashMap.keySet() ends up locking references to much of the cache
			// which can cause an OutOfMemoryException. So, use a separate/parallel set for the keys
			for ( String key : fMemoryCacheKeys )
			{
				SoftReference<SCDataBlock> 	memoryBlockRef = fMemoryCache.get(key);
				if ( memoryBlockRef != null )
				{
					SCDataBlock		block = memoryBlockRef.get();
					if ( block != null )
					{
						try
						{
							long 			ttl = fManager.getTTL(key);
							if ( (ttl == 0) || (ttl > block.getTTL()) )	// TTL has changed - purge from memory so that the correct copy is retrieved
							{
								fMemoryCache.remove(key);
								fMemoryCacheKeys.remove(key);
							}
						}
						catch ( Exception e )
						{
							if ( fManager.getNotificationHandler() != null )
							{
								fManager.getNotificationHandler().notifyException("DefaultMemoryCache.stalePurgeLoop()", e);
							}
						}
					}
				}
			}
		}
	}

	private void removeOldReferences()
	{
		Reference<? extends SCDataBlock> reference;
		while ( (reference = fQueue.poll()) != null ) 
		{
			SCDataBlock 						block = reference.get();
			if ( block != null )
			{
				fMemoryCache.remove(block.getKey());
				fMemoryCacheKeys.remove(block.getKey());
			}
		}
	}

	private static final int			STALE_PURGE_SLEEP_TICKS = 60 * 1000;	// 1 minute

	private final ConcurrentHashMap<String, SoftReference<SCDataBlock>>	fMemoryCache;
	private final Set<String> 											fMemoryCacheKeys;
	private final ReferenceQueue<SCDataBlock>							fQueue;
	private final SCManager 											fManager;
}
