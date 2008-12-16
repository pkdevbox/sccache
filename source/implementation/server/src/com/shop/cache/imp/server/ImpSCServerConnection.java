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
import com.shop.util.generic.GenericCommandClientServerReader;
import com.shop.cache.api.commands.SCCommand;
import com.shop.cache.api.commands.SCDataBuilder;
import com.shop.cache.api.commands.SCDataBuilderTypeAndCount;
import com.shop.cache.api.commands.SCSetOfCommands;
import com.shop.cache.api.server.SCConnection;
import java.io.IOException;
import java.io.OutputStream;

/**
 * SHOP.COM's Server Connection implementation
 *
 * @author Jordan Zimmerman
 */
class ImpSCServerConnection implements SCConnection
{
	ImpSCServerConnection(ImpSCServer server, GenericCommandClientServer<ImpSCServerConnection> connection, boolean monitorMode)
	{
		fServer = server;
		fConnection = connection;
		fMonitorMode = monitorMode;
		fCurrentCommand = null;
	}

	@Override
	public boolean isMonitorMode()
	{
		return fMonitorMode;
	}

	@Override
	public void sendObject(ChunkedByteArray obj) throws IOException
	{
		fConnection.send((obj != null) ? Integer.toString(obj.size()) : "0");
		if ( obj != null )
		{
			obj.writeTo
			(
				new OutputStream()
				{
					@Override
					public void write(int i) throws IOException
					{
						byte		b = (byte)(i & 0xff);
						byte[]		bytes = {b};
						fConnection.sendBytes(bytes, 0, 1);
					}

					@Override
					public void write(byte[] b) throws IOException
					{
						fConnection.sendBytes(b, 0, b.length);
					}

					@Override
					public void write(byte[] b, int off, int len) throws IOException
					{
						fConnection.sendBytes(b, off, len);
					}
				}
			);
		}
	}

	@Override
	public void sendValue(String... v) throws IOException
	{
		for ( String s : v )
		{
			fConnection.send(s);
		}
	}

	@Override
	public void close() throws IOException
	{
		fConnection.close();
	}

	@Override
	public String toString()
	{
		return fConnection.getAddress().getHostName();
	}

	String 		getCurrentCommand()
	{
		return (fCurrentCommand != null) ? fCurrentCommand.name : "<idle>";
	}

	void receiveLine(String line, GenericCommandClientServerReader reader) throws Exception
	{
		if ( fCurrentCommand == null )
		{
			SCCommand 		newCommand = SCSetOfCommands.get(line);
			if ( fMonitorMode && (newCommand != null) && !newCommand.isMonitorCommand() )
			{
				newCommand = null;
			}
			if ( newCommand != null )
			{
				fServer.incrementTransactionCount();

				fCurrentCommand = new CommandState();
				fCurrentCommand.name = line;
				fCurrentCommand.command = newCommand;
				fCurrentCommand.builder = newCommand.newBuilder();
				fCurrentCommand.tAndCIndex = 0;
				fCurrentCommand.workIndex = 0;
				fCurrentCommand.remainingBounded = null;
			}
		}
		else
		{
			SCDataBuilderTypeAndCount 		currentTandC = fCurrentCommand.command.getTypesAndCounts().get(fCurrentCommand.tAndCIndex);
			switch ( currentTandC.type )
			{
				case FIXED_SIZE_VALUE_SET:
				{
					fCurrentCommand.builder.addNextValue(line);
					if ( ++fCurrentCommand.workIndex >= currentTandC.count )
					{
						++fCurrentCommand.tAndCIndex;
					}
					break;
				}

				case BOUNDED_VALUE_SET:
				{
					if ( fCurrentCommand.remainingBounded == null )
					{
						fCurrentCommand.remainingBounded = sizeFromLine(line);
					}
					else
					{
						fCurrentCommand.builder.addNextValue(line);
						--fCurrentCommand.remainingBounded;
					}

					if ( fCurrentCommand.remainingBounded <= 0 )
					{
						fCurrentCommand.remainingBounded = null;
						++fCurrentCommand.tAndCIndex;
					}
					break;
				}

				case OBJECT:
				{
					++fCurrentCommand.tAndCIndex;
					int		size = sizeFromLine(line);
					if ( size > 0 )
					{
						ChunkedByteArray		bytes = reader.readBytes(size);
						fCurrentCommand.builder.addNextObject(bytes);
					}
					break;
				}

				case UNBOUNDED_VALUE_SET:
				{
					if ( line.trim().length() > 0 )
					{
						fCurrentCommand.builder.addNextValue(line);
					}
					else
					{
						++fCurrentCommand.tAndCIndex;
					}
					break;
				}
			}
		}

		if ( (fCurrentCommand != null) && (fCurrentCommand.tAndCIndex >= fCurrentCommand.command.getTypesAndCounts().size()) )
		{
			fCurrentCommand.builder.executeCommand(fServer, this);
			fCurrentCommand = null;
			fConnection.flush();
		}
	}

	private int sizeFromLine(String line)
	{
		int			size = 0;
		try
		{
			size = Integer.parseInt(line);
		}
		catch ( NumberFormatException e )
		{
			// ignore
		}

		return size;
	}

	private static class CommandState
	{
		String			name;
		SCCommand		command;
		SCDataBuilder 	builder;
		int 			tAndCIndex;
		int				workIndex;
		Integer			remainingBounded;
	}

	private final ImpSCServer 										fServer;
	private final GenericCommandClientServer<ImpSCServerConnection>	fConnection;
	private final boolean 											fMonitorMode;
	private CommandState											fCurrentCommand;
}
