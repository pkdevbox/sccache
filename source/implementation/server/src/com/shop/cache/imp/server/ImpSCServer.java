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
package com.shop.cache.imp.server;

import com.shop.util.chunked.ChunkedByteArray;
import com.shop.util.generic.GenericCommandClientServer;
import com.shop.util.generic.GenericCommandClientServerListener;
import com.shop.util.generic.GenericCommandClientServerReader;
import com.shop.cache.api.storage.SCStorage;
import com.shop.cache.api.storage.SCStorageServerDriver;
import com.shop.cache.api.server.SCServer;
import com.shop.cache.api.server.SCServerContext;
import com.shop.cache.api.common.SCDataSpec;
import com.shop.cache.api.common.SCGroup;
import com.shop.cache.api.common.SCGroupSpec;
import com.shop.cache.api.common.SCNotifications;
import com.shop.cache.imp.common.ImpSCUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * SHOP.COM's Server implementation
 *
 * @author Jordan Zimmerman
 */
class ImpSCServer implements SCServer, SCStorageServerDriver
{
	ImpSCServer(SCServerContext context, SCStorage database) throws Exception
	{
		fContext = context;
		fDatabase = database;

		fAbnormalCloses = new AtomicInteger(0);
		fErrorState = null;
		fIsDone = false;
		fIsOpen = true;

		fDumpPinPoint = new Date();

		PrintStream			logFile = null;
		if ( context.getLogPath() != null )
		{
			logFile = new PrintStream(new FileOutputStream(context.getLogPath()));
		}
		fLogFile = logFile;

		fServer = GenericCommandClientServer.makeServer(new InternalListener(false), context.getPort(), false);

		GenericCommandClientServer<ImpSCServerConnection> 		server = null;
		if ( fContext.getMonitorPort() != 0 )
		{
			server = GenericCommandClientServer.makeServer(new InternalListener(true), context.getMonitorPort(), false);
			System.out.println("Monitor active on port " + context.getMonitorPort());
		}
		fMonitor = server;

		fTransactionCount = new AtomicLong(0);

		fLastGetTimesIndex = new AtomicInteger(0);
		fLastGetTimes = new AtomicReferenceArray<String>(LAST_GET_TIMES_QTY);
		for ( int i = 0; i < LAST_GET_TIMES_QTY; ++i )
		{
			fLastGetTimes.set(i, "");
		}

		if ( fMonitor != null )
		{
			fMonitor.start();
		}
		fServer.start();

		System.out.println("Server active on port " + context.getPort());
	}

	@Override
	public SCNotifications getNotificationHandler()
	{
		return null;
	}

	@Override
	public void close()
	{
		shutdown();
	}

	@Override
	public long getTTL(String key) throws Exception
	{
		SCDataSpec 	entry = getEntry(key, true);
		return (entry != null) ? entry.ttl : 0;
	}

	@Override
	public String getErrorState()
	{
		return fErrorState;
	}

	@Override
	public void handleException(Exception e)
	{
		ImpSCUtils.handleException(e, this);
	}

	@Override
	public void log(String s, Throwable e, boolean newline)
	{
		Date			now = new Date();
		if ( newline )
		{
			s = now.toString() + ": " + s;
		}

		System.out.print(s);
		if ( newline )
		{
			System.out.println();
		}
		if ( e != null )
		{
			e.printStackTrace();
		}

		if ( fLogFile != null )
		{
			fLogFile.print(s);
			if ( newline )
			{
				fLogFile.println();
			}
			if ( e != null )
			{
				e.printStackTrace(fLogFile);
			}
		}
	}

	@Override
	public void 		writeKeyData(String filePath) throws IOException
	{
		fDatabase.writeKeyData(new File(filePath));
	}

	@Override
	public List<String> stackTrace()
	{
		List<String>						tab = new ArrayList<String>();
		Map<Thread, StackTraceElement[]>	threads = Thread.getAllStackTraces();
		for ( Thread thread : threads.keySet() )
		{
			String 		thread_name = thread.getName();
			tab.add(thread_name);

			StackTraceElement[]		stack = threads.get(thread);
			for ( StackTraceElement element : stack )
			{
				tab.add("\t" + element.getClassName() + " - " + element.getMethodName() + "() Line " + element.getLineNumber());
			}
		}

		return tab;
	}

	@Override
	public List<String> getConnectionList()
	{
		List<String>												tab = new ArrayList<String>();
		List<GenericCommandClientServer<ImpSCServerConnection>> 	clients = fServer.getClients();
		tab.add(clients.size() + " client(s)");

		int			index = 0;
		for ( GenericCommandClientServer<ImpSCServerConnection> connection : clients )
		{
			tab.add(++index + ". " + connection.getUserValue().toString() + " - " + connection.getUserValue().getCurrentCommand());
		}

		return tab;
	}

	/**
	 * Set the error state. IMPORTANT: Setting the error state will cause a shutdown of the non-monitor server
	 *
	 * @param errorState new error
	 */
	@Override
	public void setErrorState(String errorState)
	{
		assert errorState != null;
		fErrorState = errorState;
		shutdown();
	}

	@Override
	public synchronized void shutdown()
	{
		if ( !fIsDone )
		{
			fIsDone = true;
			fServer.close();
			fServer.waitForClose();
		}
		notifyAll();
	}

	@Override
	public synchronized void		join() throws InterruptedException
	{
		while ( !fIsDone && fIsOpen )
		{
			wait();
		}

		internalClose();
	}

	@Override
	public ChunkedByteArray get(String key, boolean ignoreTTL)
	{
		TrackerTimer		timer = new TrackerTimer(fGetTimerData);
		timer.start();

		SCDataSpec 	entry = getEntry(key, ignoreTTL);

		int 		getTime = timer.end("get()");
		String 		timingMessage;
		if ( (entry != null) && (entry.data != null) )
		{
			int 		bytesPerSecond = (entry.data.size() / Math.max(1, getTime));
			timingMessage = getTime + " ms " + entry.data.size() + " bytes " + bytesPerSecond + " bpms";
		}
		else
		{
			timingMessage = getTime + " ms <not found>";
		}

		int 		index = fLastGetTimesIndex.getAndIncrement();
		if ( index >= LAST_GET_TIMES_QTY )
		{
			fLastGetTimesIndex.compareAndSet(index, 0);
			index = 0;
		}
		fLastGetTimes.set(index, timingMessage);

		return (entry != null) ? entry.data : null;
	}

	@Override
	public void putWithBackup(String key, SCDataSpec data, SCGroupSpec groups) throws Exception
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void put(String key, SCDataSpec data, SCGroupSpec groups)
	{
		TrackerTimer		timer = new TrackerTimer(fPutTimerData);
		timer.start();

		try
		{
			fDatabase.put(key, data, groups);
		}
		catch ( Throwable e )
		{
			ImpSCUtils.handleException(e, null);
		}

		timer.end("put()");
	}

	@Override
	public List<String> listGroup(SCGroup group)
	{
		try
		{
			return fDatabase.listGroup(group);
		}
		catch ( Throwable e )
		{
			ImpSCUtils.handleException(e, null);
		}

		return new ArrayList<String>();
	}

	@Override
	public List<String> removeGroup(SCGroup group)
	{
		try
		{
			return fDatabase.removeGroup(group);
		}
		catch ( Throwable e )
		{
			ImpSCUtils.handleException(e, null);
		}

		return new ArrayList<String>();
	}

	@Override
	public List<String> regExRemove(String expression)
	{
		Set<String> 			keySet = null;
		try
		{
			keySet = fDatabase.regexFindKeys(expression);
			for ( String thisKey : keySet )
			{
				fDatabase.remove(thisKey);
			}
		}
		catch ( Throwable e )
		{
			ImpSCUtils.handleException(e, null);
		}

		return new ArrayList<String>(keySet);
	}

	@Override
	public void remove(String key)
	{
		try
		{
			fDatabase.remove(key);
		}
		catch ( Throwable e )
		{
			ImpSCUtils.handleException(e, null);
		}
	}

	@Override
	public void			incrementTransactionCount()
	{
		fTransactionCount.incrementAndGet();
	}

	@Override
	public List<String> dumpStats(boolean verbose)
	{
		List<String>	tab = new ArrayList<String>();
		int				client_count = fServer.getClients().size();

		try
		{
			tab.add("Stats since: " + fDumpPinPoint);
			tab.add("Cache server build: " + CHECKIN_VERSION);
			tab.add("===========================");

			TrackerTimer.output(tab, fGetTimerData, verbose);
			TrackerTimer.output(tab, fPutTimerData, verbose);
			if ( verbose )
			{
				tab.add(" ");
			}
			tab.addAll(fDatabase.dumpStats(verbose));

			long 			minutesRunning = Math.max((System.currentTimeMillis() - fDumpPinPoint.getTime()) / (1000 * 60), 1);

			tab.add("Client Count:            " + client_count);
			tab.add("VM Free Memory:          " + Runtime.getRuntime().freeMemory());
			tab.add("VM Total Memory:         " + Runtime.getRuntime().totalMemory());
			tab.add("VM Max Memory:           " + Runtime.getRuntime().maxMemory());
			tab.add("VM Version:              " + System.getProperty("java.vm.version", "???"));
			tab.add("Thread Count:            " + Thread.activeCount());
			tab.add("Current Time:            " + (new Date()));
			tab.add("Transaction Qty:         " + fTransactionCount.get());
			tab.add("Transactions Per Minute: " + (fTransactionCount.get() / minutesRunning));
			tab.add("Abnormal Disconnects:    " + fAbnormalCloses.get());

			tab.add(" ");

			tab.add("Last " + LAST_GET_TIMES_QTY + " get times:");
			for ( int i = 0; i < LAST_GET_TIMES_QTY; ++i )
			{
				String s = fLastGetTimes.get(i);
				if ( s.length() > 0 )
				{
					tab.add(s);
				}
			}

			tab.add(" ");
		}
		catch ( IOException e )
		{
			ImpSCUtils.handleException(e, null);
		}

		return tab;
	}

	private SCDataSpec getEntry(String key, boolean ignoreTTL)
	{
		SCDataSpec entry = null;
		try
		{
			entry = fDatabase.get(key);

			if ( entry != null )
			{
				if ( !ignoreTTL )
				{
					long			now = System.currentTimeMillis();
					if ( now >= entry.ttl )
					{
						entry = null;
						fDatabase.remove(key);
					}
				}
			}
		}
		catch ( Throwable e )
		{
			ImpSCUtils.handleException(e, null);
		}
		return entry;
	}

	private synchronized void	internalClose()
	{
		if ( !fIsOpen )
		{
			return;
		}
		fIsOpen = false;

		log("Shutting down server.", null, true);

		if ( fLogFile != null )
		{
			fLogFile.flush();
		}

		// first thing - stop accepting clients
		noMoreConnections();

		try
		{
			fDatabase.close();
		}
		catch ( Exception e )
		{
			ImpSCUtils.handleException(e, null);
		}

		log("Server shutdown complete.", null, true);

		if ( fErrorState != null )
		{
			log("Going into zombie mode due to error state: " + fErrorState, null, true);
			try
			{
				Thread.sleep(Long.MAX_VALUE);
			}
			catch ( InterruptedException e )
			{
				// ignore
			}
		}
		else
		{
			if ( fLogFile != null )
			{
				fLogFile.close();
			}
		}
		notifyAll();
	}

	private synchronized void noMoreConnections()
	{
		fServer.close();
		closeMonitor();
	}

	private synchronized void closeMonitor()
	{
		if ( (fMonitor != null) && (fErrorState == null) )
		{
			fMonitor.close();
			fMonitor.waitForClose();
		}
	}

	private class InternalListener implements GenericCommandClientServerListener<ImpSCServerConnection>
	{
		InternalListener(boolean monitorMode)
		{
			fMonitorMode = monitorMode;
		}

		@Override
		public void notifyIdle(GenericCommandClientServer<ImpSCServerConnection> client)
		{
		}

		@Override
		public void notifyException(GenericCommandClientServer<ImpSCServerConnection> gen, Exception e)
		{
			gen.close();
			if ( !ImpSCUtils.handleException(e, ImpSCServer.this) )
			{
				fAbnormalCloses.incrementAndGet();
			}
		}

		@Override
		public void notifyClientAccepted(GenericCommandClientServer<ImpSCServerConnection> server, GenericCommandClientServer<ImpSCServerConnection> connection)
		{
			connection.changeHeartbeat(fContext.getHeartbeat());
			
			ImpSCServerConnection 	scConnection = new ImpSCServerConnection(ImpSCServer.this, connection, fMonitorMode);
			connection.setUserValue(scConnection);
		}

		@Override
		public void notifyClientServerClosed(GenericCommandClientServer<ImpSCServerConnection> gen)
		{
			if ( gen.isServer() )
			{
				if ( !fMonitorMode )
				{
					shutdown();
				}
			}
		}

		@Override
		public void notifyLineReceived(GenericCommandClientServer<ImpSCServerConnection> gen, String line, GenericCommandClientServerReader reader) throws Exception
		{
			assert gen.getUserValue() != null;
			gen.getUserValue().receiveLine(line, reader);
		}

		@Override
		public InputStream notifyCreatingInputStream(GenericCommandClientServer<ImpSCServerConnection> client, InputStream in) throws Exception
		{
			return in;
		}

		@Override
		public OutputStream notifyCreatingOutputStream(GenericCommandClientServer<ImpSCServerConnection> client, OutputStream out) throws Exception
		{
			return out;
		}

		private final boolean 	fMonitorMode;
	}

	private static final TrackerTimer.data 		fGetTimerData = new TrackerTimer.data("Gets");
	private static final TrackerTimer.data 		fPutTimerData = new TrackerTimer.data("Puts");

	private static final int				LAST_GET_TIMES_QTY = 500;

	private static final String 			CHECKIN_VERSION = "1.1";

	private final SCServerContext 										fContext;
	private final SCStorage fDatabase;
	private final AtomicInteger 										fAbnormalCloses;
	private final GenericCommandClientServer<ImpSCServerConnection> 	fServer;
	private final GenericCommandClientServer<ImpSCServerConnection>		fMonitor;
	private final AtomicReferenceArray<String>							fLastGetTimes;
	private	final AtomicInteger											fLastGetTimesIndex;
	private	final Date 													fDumpPinPoint;
	private final AtomicLong 											fTransactionCount;
	private final PrintStream											fLogFile;
	private boolean 													fIsDone;
	private boolean 													fIsOpen;
	private volatile String												fErrorState;
}
