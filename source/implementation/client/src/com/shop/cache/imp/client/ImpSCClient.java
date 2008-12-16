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
import com.shop.cache.api.commands.*;
import com.shop.cache.api.common.SCDataSpec;
import com.shop.cache.api.common.SCGroup;
import com.shop.cache.api.common.SCGroupSpec;
import com.shop.cache.api.common.SCNotifications;
import com.shop.cache.imp.common.ImpSCClosedConnectionException;
import com.shop.cache.imp.common.ImpSCUtils;
import com.shop.util.chunked.ChunkedByteArray;
import com.shop.util.generic.GenericCommandClientServer;
import com.shop.util.generic.GenericCommandClientServerListener;
import com.shop.util.generic.GenericCommandClientServerReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SHOP.COM's Client implementation
 *
 * @author Jordan Zimmerman
 */
class ImpSCClient implements SCClient
{
	ImpSCClient(SCClientContext context) throws IOException
	{
		this(null, null, context);
	}

	@Override
	public SCNotifications getNotificationHandler()
	{
		return fContext.getNotificationHandler();
	}

	@Override
	public List<String> removeGroup(SCGroup group) throws Exception
	{
		return standardCommandUntilBlankLine(SCCommandRemoveGroup.class, group.toString());
	}

	@Override
	public List<String> listGroup(SCGroup group) throws Exception
	{
		return standardCommandUntilBlankLine(SCCommandListGroup.class, group.toString());
	}

	@Override
	public List<String> dumpStats(boolean verbose) throws Exception
	{
		return standardCommandUntilBlankLine(verbose ? SCCommandDumpStats.class : SCCommandDumpShortStats.class, null);
	}

	@Override
	public List<String> stackTrace() throws Exception
	{
		return standardCommandUntilBlankLine(SCCommandStack.class, null);
	}

	@Override
	public List<String> getConnectionList() throws Exception
	{
		return standardCommandUntilBlankLine(SCCommandListClients.class, null);
	}

	@Override
	public List<String> regExRemove(String expression) throws Exception
	{
		return standardCommandUntilBlankLine(SCCommandRegexRemoveObjects.class, expression);
	}

	@Override
	public void writeKeyData(String fPath) throws Exception
	{
		try
		{
			fIsWaitingForResponse = true;
			fGen.send(SCSetOfCommands.getCommandName(SCCommandKeyDump.class));
			fGen.sendFlushWaitReceive(fPath);	// ignore the result
			fIsWaitingForResponse = false;
		}
		catch ( Exception e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	@Override
	public long getTTL(String key) throws Exception
	{
		key = filterKey(key);

		try
		{
			fIsWaitingForResponse = true;
			fGen.send(SCSetOfCommands.getCommandName(SCCommandGetObjectTTL.class));
			String		result = fGen.sendFlushWaitReceive(key);
			fIsWaitingForResponse = false;

			return safeParseLong(result);
		}
		catch ( Exception e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	@Override
	public void close()
	{
		fGen.close();
	}

	@Override
	public void hello() throws Exception
	{
		try
		{
			fIsWaitingForResponse = true;
			fGen.sendFlushWaitReceive(SCSetOfCommands.getCommandName(SCCommandHello.class));
			fIsWaitingForResponse = false;
		}
		catch ( Exception e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	@Override
	public void goodbye() throws IOException
	{
		simpleCommand(SCCommandCloseConnection.class);
	}

	@Override
	public void stopServer() throws IOException
	{
		simpleCommand(SCCommandShutdown.class);
	}

	@Override
	public void dumpStats(List<String> lines, boolean verbose) throws Exception
	{
		GenericCommandClientServerReader 	reader = null;
		try
		{
			GenericCommandClientServer.AcquireReaderData 	readerPair = fGen.SendFlushWaitReceiveAcquireReader(SCSetOfCommands.getCommandName(verbose ? SCCommandDumpStats.class : SCCommandDumpShortStats.class));
			reader = readerPair.reader;

			int		lineCount = safeParseInt(readerPair.line);
			while ( lineCount-- > 0 )
			{
				lines.add(reader.readLine());
			}
		}
		catch ( IOException e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
		finally
		{
			if ( reader != null )
			{
				reader.release();
			}
		}
	}

	@Override
	public void keyDump(String remoteFilename) throws IOException
	{
		try
		{
			fGen.send(SCSetOfCommands.getCommandName(SCCommandKeyDump.class));
			fGen.send(remoteFilename);
			fGen.flush();
		}
		catch ( IOException e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	@Override
	public SCClientManager getManager()
	{
		return fManager;
	}

	@Override
	public ChunkedByteArray get(String key, boolean ignoreTTL) throws Exception
	{
		key = filterKey(key);

		try
		{
			fIsWaitingForResponse = true;
			fLastLineReceivedTicks = 0;

			ChunkedByteArray bytes = null;
			GenericCommandClientServerReader 	reader = null;
			try
			{
				fGen.send(SCSetOfCommands.getCommandName(ignoreTTL ? SCCommandGetObjectIgnoreTTL.class : SCCommandGetObject.class));
				GenericCommandClientServer.AcquireReaderData 	readerPair = fGen.SendFlushWaitReceiveAcquireReader(key);
				reader = readerPair.reader;

				int			size = safeParseInt(readerPair.line);
				if ( size > 0 )
				{
					bytes = reader.readBytes(size);
				}
			}
			catch ( InterruptedException dummy )
			{
				Thread.currentThread().interrupt();
			}
			finally
			{
				if ( reader != null )
				{
					reader.release();
				}
			}
			fIsWaitingForResponse = false;

			if ( (bytes == null) || (bytes.size() == 0) )
			{
				return null;
			}

			return bytes;
		}
		catch ( Exception e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw new IOException(e);
		}
	}

	@Override
	public void put(String key, SCDataSpec spec, SCGroupSpec groups) throws Exception
	{
		key = filterKey(key);

		assert spec.data.size() > 0;
		if ( spec.data.size() == 0 )
		{
			return;
		}

		try
		{
			fGen.send(SCSetOfCommands.getCommandName(SCCommandPutObject.class));
			fGen.send(key);
			fGen.send(Long.toString(spec.ttl));
			if ( groups != null )
			{
				fGen.send(Integer.toString(groups.size()));
				for ( SCGroup g : groups )
				{
					fGen.send(g.toString());
				}
			}
			else
			{
				fGen.send("0");
			}

			writeObject(spec);

			fGen.flush();
		}
		catch ( IOException e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	@Override
	public void remove(String key) throws Exception
	{
		key = filterKey(key);

		try
		{
			fGen.send(SCSetOfCommands.getCommandName(SCCommandRemoveObject.class));
			fGen.send(key);
			fGen.flush();
		}
		catch ( IOException e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	static GenericCommandClientServerListener<ImpSCClient> getListener()
	{
		return fListener;
	}

	ImpSCClient(SCClientManager manager, GenericCommandClientServer<ImpSCClient> gen, SCClientContext context) throws IOException
	{
		fManager = manager;
		fContext = (context != null) ? context : (((gen != null) && (gen.getUserValue() != null)) ? gen.getUserValue().fContext : null);
		fIsWaitingForResponse = false;

		fPutKeyQueue = new AtomicReference<LinkedBlockingQueue<PutEntry>>();

		if ( gen == null )
		{
			try
			{
				gen = GenericCommandClientServer.makeClient(fListener, fContext.getAddress().getHostName(), fContext.getAddress().getPort(), false);
				gen.setUserValue(this);
				gen.setIdleNotificationTimeout(ImpSCUtils.IDLE_NOTIFICATION_TICKS);
				gen.start();
			}
			catch ( Exception e )
			{
				if ( fManager != null )
				{
					fManager.registerException(e);
				}
				throw new IOException(e);
			}
		}

		fGen = gen;
		fGen.changeHeartbeat(fContext.getHeartbeat());
	}

	GenericCommandClientServer<ImpSCClient>	getGen()
	{
		return fGen;
	}

	private List<String> standardCommandUntilBlankLine(Class<? extends SCCommand> commandClass, String argument) throws Exception
	{
		String 				commandName = SCSetOfCommands.getCommandName(commandClass);
		List<String>		tab = new ArrayList<String>();
		try
		{
			fIsWaitingForResponse = true;
			if ( argument != null )
			{
				fGen.send(commandName);
			}
			else
			{
				argument = commandName;
			}
			GenericCommandClientServer.AcquireReaderData 	data = fGen.SendFlushWaitReceiveAcquireReader(argument);
			getUntilBlankLine(data, tab);
			fIsWaitingForResponse = false;
		}
		catch ( Exception e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
		return tab;
	}

	private void getUntilBlankLine(GenericCommandClientServer.AcquireReaderData data, List<String> tab) throws IOException
	{
		String		line = data.line;
		while ( line.length() > 0 )
		{
			tab.add(line);
			line = data.reader.readLine();
		}
	}

	private static int safeParseInt(String s)
	{
		int			i = 0;
		try
		{
			i = Integer.parseInt(s);
		}
		catch ( NumberFormatException e )
		{
			// ignore
		}
		return i;
	}

	private static long safeParseLong(String s)
	{
		long			i = 0;
		try
		{
			i = Long.parseLong(s);
		}
		catch ( NumberFormatException e )
		{
			// ignore
		}
		return i;
	}

	private void		writeObject(SCDataSpec spec) throws IOException
	{
		if ( (spec == null) || (spec.data == null) )
		{
			fGen.send("0");
		}
		else
		{
			int 			size = spec.data.size();

			fGen.send(Integer.toString(size));
			spec.data.writeTo
			(
				new OutputStream()
				{
					@Override
					public void write(int i) throws IOException
					{
						byte		b = (byte)(i & 0xff);
						byte[]		bytes = {b};
						fGen.sendBytes(bytes, 0, 1);
					}

					@Override
					public void write(byte[] b) throws IOException
					{
						fGen.sendBytes(b, 0, b.length);
					}

					@Override
					public void write(byte[] b, int off, int len) throws IOException
					{
						fGen.sendBytes(b, off, len);
					}
				}
			);
		}
	}

	private void simpleCommand(Class<? extends SCCommand> commandClass) throws IOException
	{
		try
		{
			fGen.send(SCSetOfCommands.getCommandName(commandClass));
			fGen.flush();
		}
		catch ( IOException e )
		{
			if ( fManager != null )
			{
				fManager.registerException(e);
			}
			throw e;
		}
	}

	private static String	filterKey(String key)
	{
		StringBuilder		newKey = new StringBuilder(key.length());
		for ( int i = 0; i < key.length(); ++i )
		{
			char		c = key.charAt(i);
			switch ( c )
			{
				case '\n':
				case '\r':
				{
					newKey.append('\u0001');
					break;
				}

				default:
				{
					newKey.append(c);
					break;
				}
			}
		}
		return newKey.toString();
	}

	private static class PutEntry
	{
		public final String			key;

		public PutEntry(String key)
		{
			this.key = key;
		}
	}

	private static final GenericCommandClientServerListener<ImpSCClient> fListener = new GenericCommandClientServerListener<ImpSCClient>()
	{
		@Override
		public void notifyException(GenericCommandClientServer<ImpSCClient> gen, Exception e)
		{
			ImpSCClient localClient = gen.getUserValue();
			if ( localClient != null )
			{
				if ( localClient.fManager != null )
				{
					localClient.fManager.registerException(e);
				}
			}
			gen.close();
		}

		@Override
		public void notifyIdle(GenericCommandClientServer<ImpSCClient> gen)
		{
			ImpSCClient localClient = gen.getUserValue();
			if ( (localClient != null) && localClient.fIsWaitingForResponse )
			{
				notifyException(gen, new ImpSCClosedConnectionException("read timeout"));
			}
		}

		@Override
		public void notifyClientAccepted(GenericCommandClientServer<ImpSCClient> server, GenericCommandClientServer<ImpSCClient> client)
		{
		}

		@Override
		public void notifyClientServerClosed(GenericCommandClientServer<ImpSCClient> gen)
		{
			ImpSCClient		local_client = gen.getUserValue();
			if ( local_client != null )
			{
				LinkedBlockingQueue<PutEntry> 		local_put_queue = local_client.fPutKeyQueue.getAndSet(null);
				if ( local_put_queue != null )
				{
					local_put_queue.add(new PutEntry(null));	// signal the end
				}
			}
		}

		@Override
		public void notifyLineReceived(GenericCommandClientServer<ImpSCClient> gen, String line, GenericCommandClientServerReader reader) throws Exception
		{
			ImpSCClient 		localClient = gen.getUserValue();
			if ( localClient.fLastLineReceivedTicks == 0 )
			{
				localClient.fLastLineReceivedTicks = System.currentTimeMillis();
			}

			LinkedBlockingQueue<PutEntry> 	localPutQueue = localClient.fPutKeyQueue.get();
			if ( localPutQueue != null )
			{
				localPutQueue.add(new PutEntry(line));
			}
		}

		@Override
		public InputStream notifyCreatingInputStream(GenericCommandClientServer<ImpSCClient> client, InputStream in) throws Exception
		{
			return in;
		}

		@Override
		public OutputStream notifyCreatingOutputStream(GenericCommandClientServer<ImpSCClient> client, OutputStream out) throws Exception
		{
			return out;
		}
	};

	private final GenericCommandClientServer<ImpSCClient> 			fGen;
	private final SCClientManager 									fManager;
	private final SCClientContext 									fContext;
	private final AtomicReference<LinkedBlockingQueue<PutEntry>> 	fPutKeyQueue;
	private	volatile boolean 										fIsWaitingForResponse;
	private	volatile long 											fLastLineReceivedTicks;
}
