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

import com.shop.util.chunked.ChunkedByteArray;
import com.shop.util.LineReader;
import com.shop.util.SSLSocketMaker;
import javax.net.ssl.SSLException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * General purpose TCP/IP server and client.<br>
 *
 * The majority of client/server code is the same - so here is one class that does both. This class manages either
 * a server or a client that communicate via telnet-style commands: i.e. a command followed by an EOL. The class
 * manages heartbeats between client and server to validate the connection. 
 *
 * @author Jordan Zimmerman
 */
public class GenericCommandClientServer<T>
{

	/**
	 * Make a connection to a generic_command_client_server server
	 *
	 * @param l the listener (can be null)
	 * @param address address to connect to
	 * @param port port to connect to
	 * @param ssl if true, and SSL connection is made
	 * @return the client
	 * @throws Exception any error
	 */
	public static<T> GenericCommandClientServer<T> makeClient(GenericCommandClientServerListener<T> l, String address, int port, boolean ssl) throws Exception
	{
		return new GenericCommandClientServer<T>(l, null, null, address, port, ssl, mode.CLIENT);
	}

	/**
	 * Create a server to accept generic_command_client_server clients
	 *
	 * @param l the listener (can be null)
	 * @param port port to listen to
	 * @param ssl if true, and SSL server is made
	 * @return the server
	 * @throws Exception any error
	 */
	public static<T> GenericCommandClientServer<T> makeServer(GenericCommandClientServerListener<T> l, int port, boolean ssl) throws Exception
	{
		return new GenericCommandClientServer<T>(l, null, null, null, port, ssl, mode.SERVER);
	}

	/**
	 * Associate a user-defined value with this client/server
	 *
	 * @param value the value
	 */
	public synchronized void 			setUserValue(T value)
	{
		fUserValue = value;
	}

	/**
	 * Return the user-defined value for this client/server
	 *
	 * @return value
	 */
	public synchronized T 				getUserValue()
	{
		return fUserValue;
	}

	/**
	 * Returns true if the client/server is open
	 *
	 * @return true/false
	 */
	public synchronized boolean 		isOpen()
	{
		return fOpen;
	}

	/**
	 * Sets the idle read timeout. When set, any read that takes longer than the specified ticks
	 * will cause {@link GenericCommandClientServerListener#notifyIdle(GenericCommandClientServer)} to be called.
	 * IMPORTANT: Can only be set for clients and must be called prior to {@link #start()} being called
	 *
	 * @param ticks idle ticks
	 * @throws SocketException errors
	 */
	public void 						setIdleNotificationTimeout(int ticks) throws SocketException
	{
		if ( isServer() )
		{
			throw new IllegalStateException("set_idle_notification_timeout() not supported for servers");
		}
		fSocket.setSoTimeout(ticks);
		fIdleTimeout = ticks;
	}

	/**
	 * Start the client/server
	 */
	public synchronized void			start()
	{
		fThread.start();
	}

	/**
	 * Return true if this is a new connection (only useful when the pool is used)
	 *
	 * @return true/false
	 */
	public boolean 						isNew()
	{
		return fIsNew;
	}

	/**
	 * For clients, closes the connection. For servers, closes all connected clients and the server itself
	 */
	public synchronized void			close()
	{
		synchronized(this)
		{
			if ( !fDone )
			{
				switch ( fMode )
				{
					default:
					{
						// do nothing
						break;
					}

					case ACCEPTED_CLIENT:
					case CLIENT:
					{
						try
						{
							flush();
						}
						catch ( IOException ignore )
						{
							// ignore
						}
						break;
					}
				}

				fDone = true;
				fThread.interrupt();

				if ( fSocket != null )
				{
					try
					{
						// the only way to force an exit out of a waiting read
						fSocket.close();
					}
					catch ( IOException ignore )
					{
						// ignore
					}
				}
			}

			internalNotify();	// there may be waiting threads
		}
	}

	/**
	 * Wait for this server or client thread to end. Assumes that {@link #close()} has already been called
	 */
	public void 			waitForClose()
	{
		try
		{
			fThread.join();
		}
		catch ( InterruptedException ignore )
		{
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Returns true if this gen was created via {@link #makeServer(GenericCommandClientServerListener , int, boolean)}
	 *
	 * @return true/false
	 */
	public boolean 			isServer()
	{
		return fMode == mode.SERVER;
	}

	/**
	 * By default, a blank link is sent as a periodic heartbeat. Use this method to set a different value.
	 *
	 * @param newHeartbeatMessage heartbeat value
	 */
	public void 			changeHeartbeat(String newHeartbeatMessage)
	{
		for ( int i = 0; i < newHeartbeatMessage.length(); ++i )
		{
			if ( (newHeartbeatMessage.charAt(i) == '\n') || (newHeartbeatMessage.charAt(i) == '\r') )
			{
				throw new IllegalArgumentException("Heartbeat cannot contain newlines or returns.");
			}
		}
		fHeartbeatMessage = newHeartbeatMessage;
	}

	/**
	 * For clients, sends the given command to the server. For servers, sends the given command to all connected clients.
	 *
	 * @param line the command
	 * @throws IOException i/o error
	 */
	public synchronized void			send(String line) throws IOException
	{
		if ( line.equals(fHeartbeatMessage) )
		{
			throw new UnsupportedOperationException("You cannot send a message that the is the same as the heartbeat.");
		}

		checkAgainstReader();

		internalSend(line);
	}

	/**
	 * Normally, generic client/server is line based. Use this method to send a byte array
	 *
	 * @param bytes bytes to send
	 * @param offset offset into bytes
	 * @param length length to write
	 * @throws IOException errors
	 */
	public synchronized void 		sendBytes(byte[] bytes, int offset, int length) throws IOException
	{
		checkAgainstReader();

		switch ( fMode )
		{
			case ACCEPTED_CLIENT:
			case CLIENT:
			{
				fOut.write(bytes, offset, length);
				break;
			}

			default:
			{
				sendBytesToAllClients(bytes, offset, length);
				break;
			}
		}
	}

	/**
	 * Synchronous send/receive. Sends the given line and then waits for a single line response. IMPORTANT: You
	 * should not have any pending reads/writes on this connection. NOTE: your notification_listener will not be
	 * called for events that occur during this method's run.
	 *
	 * @param line the line to send
	 * @return the line returned
	 * @throws Exception any errors
	 */
	public synchronized String 		sendFlushWaitReceive(String line) throws Exception
	{
		if ( fMode == mode.SERVER )
		{
			throw new UnsupportedOperationException("send_flush_wait_receive() is only useable by clients");
		}

		checkAgainstReader();

		return internalSendFlushWaitReceive(line);
	}

	public static class AcquireReaderData
	{
		public final String								line;
		public final GenericCommandClientServerReader 	reader;

		private AcquireReaderData(String line, GenericCommandClientServerReader reader)
		{
			this.line = line;
			this.reader = reader;
		}
	}

	/**
	 * Similar to {@link #sendFlushWaitReceive(String)} but after the result is received, the internal reader is put on hold
	 * and returned to the client. The client can then do blocking reads as needed. You MUST call
	 * {@link GenericCommandClientServerReader#release()} at some point to release the reader.
	 *
	 * @param line line to send
	 * @return Both the internal reader and the result of the sent line
	 * @throws Exception errors
	 * @see GenericCommandClientServerReader#release() - MUST be called to release reader
	 */
	public synchronized AcquireReaderData 	SendFlushWaitReceiveAcquireReader(String line) throws Exception
	{
		if ( fMode == mode.SERVER )
		{
			throw new UnsupportedOperationException("send_flush_wait_receive_acquire_reader() is only useable by clients");
		}

		if ( fReaderAcquisitionState != ReaderAcquisitionState.RELEASED )
		{
			throw new IllegalStateException("send_flush_wait_receive_acquire_reader() cannot be nested");
		}

		fReaderAcquisitionState = ReaderAcquisitionState.NEEDS_ONE_LINE;
		String		result = internalSendFlushWaitReceive(line);

		fReader.setSafeToUse(true);
		return new AcquireReaderData(result, fReader);
	}

	/**
	 * {@link #send(String)} buffers commands. Call flush() to force the current buffer out
	 *
	 * @throws IOException i/o error
	 */
	public synchronized void			flush() throws IOException
	{
		switch ( fMode )
		{
			case ACCEPTED_CLIENT:
			case CLIENT:
			{
				fLastFlushTicks = System.currentTimeMillis();
				fOut.flush();
				break;
			}

			default:
			{
				flushAllClients();
				break;
			}
		}
	}

	/**
	 * If this is a client accept by a server, this method returns the server object
	 *
	 * @return server object or null
	 */
	public GenericCommandClientServer<T> 	getParentServer()
	{
		return fParentServer;
	}

	/**
	 * For servers, returns all the currently connected clients. For clients, always returns null
	 *
	 * @return the currently connected clients or null
	 */
	public List<GenericCommandClientServer<T>> getClients()
	{
		if ( fClients != null )
		{
			synchronized(fClients)
			{
				return new ArrayList<GenericCommandClientServer<T>>(fClients);
			}
		}
		return null;
	}

	/**
	 * Returns the underlying socket's address
	 *
	 * @return address
	 */
	public InetSocketAddress getAddress()
	{
		String			hostname;
		if ( fConnectedToAddress != null )
		{
			hostname = fConnectedToAddress;
		}
		else
		{
			hostname = (fSocket != null) ? fSocket.getInetAddress().getHostAddress() : fServerSocket.getInetAddress().getHostAddress();
		}

		int				port = (fSocket != null) ? fSocket.getPort() : fServerSocket.getLocalPort();

		return InetSocketAddress.createUnresolved(hostname, port);
	}

	static void 	runInThread(Runnable r)
	{
		fThreadPool.execute(r);
	}

	long 			getLastUse()
	{
		return fLastUse.get();
	}

	void 			updateLastUse()
	{
		fLastUse.set(System.currentTimeMillis());
	}

	void 			clearNew()
	{
		fIsNew = false;
	}

	private void 	internalSend(String line) throws IOException
	{
		switch ( fMode )
		{
			case ACCEPTED_CLIENT:
			case CLIENT:
			{
				sendToClient(line);
				break;
			}

			default:
			{
				sendToAllClients(line);
				break;
			}
		}
	}

	private String 	internalSendFlushWaitReceive(String line) throws Exception
	{
		GenericCommandClientServerListener<T> saveListener = fListener;
		try
		{
			InternalNotificationListener internalListener = new InternalNotificationListener(saveListener);
			fListener = internalListener;

			internalSend(line);
			flush();

			//noinspection ThrowableResultOfMethodCallIgnored
			while ( (internalListener.getLastLine() == null) && (internalListener.getLastException() == null) && !fDone )
			{
				wait();
			}

			//noinspection ThrowableResultOfMethodCallIgnored
			if ( internalListener.getLastException() != null )
			{
				throw internalListener.getLastException();
			}

			if ( fDone )
			{
				throw new IOException("connection closed - socket closed");
			}

			return internalListener.getLastLine();
		}
		finally
		{
			fListener = saveListener;
		}
	}

	private void checkAgainstReader()
	{
		if ( readerIsAcquired() )
		{
			throw new UnsupportedOperationException("You cannot send while the internal reader has been acquired.");
		}
	}

	private synchronized void internalReleaseReader()
	{
		if ( fReaderAcquisitionState == ReaderAcquisitionState.RELEASED )
		{
			throw new IllegalStateException("send_flush_wait_receive_acquire_reader() has not been called");
		}
		fReader.setSafeToUse(false);
		fReaderAcquisitionState = ReaderAcquisitionState.RELEASED;
		internalNotify();
	}

	private void flushAllClients()
	{
		synchronized(fClients)
		{
			for ( final GenericCommandClientServer<T> client : fClients )
			{
				runInThread
				(
					new Runnable()
					{
						@Override
						public void run()
						{
							try
							{
								client.flush();
							}
							catch ( IOException e )
							{
								fListener.notifyException(client, e);
								try
								{
									client.internalClose();
								}
								catch ( InterruptedException ignore )
								{
									// ignore
								}
							}
						}
					}
				);
			}
		}
	}

	private void 		internalClose() throws InterruptedException
	{
		boolean 		wasOpen;
		boolean 		wasDone;
		synchronized(this)
		{
			wasOpen = fOpen;
			wasDone = fDone;

			if ( fOpen )
			{
				fOpen = false;
				fDone = true;
			}
		}

		if ( wasOpen )
		{
			switch ( fMode )
			{
				case ACCEPTED_CLIENT:
				case CLIENT:
				{
					closeClient();
					waitForThread(wasDone);
					break;
				}

				case SERVER:
				{
					waitForThread(wasDone);
					closeServer();
					break;
				}
			}

			fListener.notifyClientServerClosed(this);
		}

		internalNotify();	// there may be waiting threads
	}

	private void waitForThread(boolean wasDone) throws InterruptedException
	{
		if ( !wasDone )
		{
			fThread.interrupt();
			fThread.join();
		}
	}

	private void closeServer()
	{
		List<GenericCommandClientServer<?>> 		toBeClosed;
		synchronized(fClients)
		{
			toBeClosed = new ArrayList<GenericCommandClientServer<?>>(fClients);
		}

		for ( GenericCommandClientServer<?> client : toBeClosed )
		{
			try
			{
				client.internalClose();
			}
			catch ( InterruptedException ignore )
			{
				// ignore
			}
		}

		try
		{
			synchronized(fClients)
			{
				while ( fClients.size() > 0 )
				{
					fClients.wait();
				}
			}
		}
		catch ( InterruptedException ignore )
		{
			// ignore
		}

		try
		{
			fServerSocket.close();
		}
		catch ( IOException ignore )
		{
			// ignore
		}
	}

	private void closeClient()
	{
		try
		{
			fSocket.close();
		}
		catch ( IOException ignore )
		{
			// ignore
		}

		removeFromParentServer();
	}

	private void removeFromParentServer()
	{
		if ( fParentServer != null )
		{
			synchronized(fParentServer.fClients)
			{
				fParentServer.fClients.remove(this);
				fParentServer.fClients.notifyAll();
			}
		}
	}

	private void sendToAllClients(final String line)
	{
		synchronized(fClients)
		{
			for ( final GenericCommandClientServer<T> client : fClients )
			{
				runInThread
				(
					new Runnable()
					{
						@Override
						public void run()
						{
							try
							{
								client.send(line);
							}
							catch ( IOException e )
							{
								fListener.notifyException(client, e);
								try
								{
									client.internalClose();
								}
								catch ( InterruptedException ignore )
								{
									// ignore
								}
							}
						}
					}
				);
			}
		}
	}

	private void sendBytesToAllClients(final byte[] bytes, final int offset, final int length)
	{
		synchronized(fClients)
		{
			for ( final GenericCommandClientServer<T> client : fClients )
			{
				runInThread
				(
					new Runnable()
					{
						@Override
						public void run()
						{
							try
							{
								client.fOut.write(bytes, offset, length);
							}
							catch ( IOException e )
							{
								fListener.notifyException(client, e);
								try
								{
									client.internalClose();
								}
								catch ( InterruptedException ignore )
								{
									// ignore
								}
							}
						}
					}
				);
			}
		}
	}

	private synchronized void sendToClient(String line) throws IOException
	{
		int		length = line.length();
		for ( int i = 0; i < length; ++i )
		{
			char		c = line.charAt(i);
			fOut.write(c & 0xff);
		}
		fOut.write('\n');
	}

	private GenericCommandClientServer(GenericCommandClientServerListener<T> l, Socket acceptedSocket, GenericCommandClientServer<T> parentServer, String address, int port, boolean ssl, mode m) throws Exception
	{
		fConnectedToAddress = address;
		fListener = (l != null) ? l : new InternalNotificationListener(null);
		fParentServer = parentServer;
		fSSL = ssl;
		fMode = m;
		fThread = new RunLoop();
		fOpen = true;
		fHeartbeatMessage = "";
		fUserValue = null;
		fLastFlushTicks = System.currentTimeMillis();
		fLastUse = new AtomicLong(System.currentTimeMillis());
		fIsNew = true;
		fIdleTimeout = null;
		fReaderAcquisitionState = ReaderAcquisitionState.RELEASED;

		ArrayList<GenericCommandClientServer<T>> 	localClients;
		Socket 										localSocket;
		ServerSocket 								localServerSocket;
		switch ( fMode )
		{
			default:
			case ACCEPTED_CLIENT:
			case CLIENT:
			{
				if ( fMode == mode.ACCEPTED_CLIENT )
				{
					localSocket = acceptedSocket;
				}
				else
				{
					localSocket = ssl ? SSLSocketMaker.make(address, port) : new Socket(address, port);
				}
				localSocket.setSoTimeout(MAX_HEARTBEAT_LAPSE);
				localClients = null;
				localServerSocket = null;
				break;
			}

			case SERVER:
			{
				localClients = new ArrayList<GenericCommandClientServer<T>>();
				localSocket = null;
				localServerSocket = ssl ? SSLSocketMaker.makeServer(port, BACKLOG) : new ServerSocket(port, BACKLOG);
				localServerSocket.setSoTimeout(1000);
				break;
			}
		}

		fSocket = localSocket;
		fServerSocket = localServerSocket;
		fClients = localClients;
		fDone = false;

		LineReader localIn;
		OutputStream 	localOut;
		InternalReader 	localReader;
		switch ( fMode )
		{
			default:
			case ACCEPTED_CLIENT:
			case CLIENT:
			{
				InputStream 		socketIn = fListener.notifyCreatingInputStream(this, localSocket.getInputStream());
				if ( socketIn == null )
				{
					socketIn = localSocket.getInputStream();
				}
				OutputStream 		socketOut = fListener.notifyCreatingOutputStream(this, localSocket.getOutputStream());
				if ( socketOut == null )
				{
					socketOut = localSocket.getOutputStream();
				}

				localIn = new LineReader(new BufferedInputStream(new ClientInputStream(socketIn), localSocket.getReceiveBufferSize()));
				localOut = new BufferedOutputStream(socketOut, localSocket.getSendBufferSize());
				localReader = new InternalReader();
				break;
			}

			case SERVER:
			{
				localIn = null;
				localOut = null;
				localReader = null;
				break;
			}
		}

		fIn = localIn;
		fOut = localOut;
		fReader = localReader;
	}

	private void serverRunLoop() throws Exception
	{
		while ( !fDone )
		{
			try
			{
				Socket								s = fServerSocket.accept();
				GenericCommandClientServer<T> client = new GenericCommandClientServer<T>(fListener, s, this, s.getInetAddress().getHostAddress(), s.getPort(), fSSL, mode.ACCEPTED_CLIENT);
				synchronized(fClients)
				{
					fClients.add(client);
				}
				fListener.notifyClientAccepted(this, client);
				client.fThread.start();	// cannot be started until after notify_client_accepted() is called
			}
			catch ( SSLException exception )
			{
				fListener.notifyException(null, exception);
			}
			catch ( InterruptedIOException ignore )
			{
				// ignore
			}
		}
	}

	private void clientRunLoop() throws Exception
	{
		fAll.put(this, 0);
		try
		{
			while ( !fDone )
			{
				String			line = fIn.readLine();
				if ( line == null )
				{
					fDone = true;
				}
				else if ( !line.equals(fHeartbeatMessage) )
				{
					checkReaderAcquisitionNeedsOneLine();

					fReader.setSafeToUse(true);
					try
					{
						fListener.notifyLineReceived(this, line, fReader);
					}
					finally
					{
						fReader.setSafeToUse(false);
					}

					checkReaderAcquisitionWaiting();
				}
			}
		}
		finally
		{
			fAll.remove(this);
		}
	}

	private ChunkedByteArray internalReadBytes(int size) throws IOException
	{
		ChunkedByteArray		bytes = (size < ChunkedByteArray.DEFAULT_CHUNK_SIZE) ? new ChunkedByteArray(size) : new ChunkedByteArray();
		while ( size-- > 0 )
		{
			int		i = fIn.read();
			if ( i < 0 )
			{
				throw new EOFException();
			}
			bytes.append((byte)(i & 0xff));
		}
		return bytes;
	}

	private synchronized void checkReaderAcquisitionNeedsOneLine()
	{
		if ( fReaderAcquisitionState == ReaderAcquisitionState.NEEDS_ONE_LINE )
		{
			fReaderAcquisitionState = ReaderAcquisitionState.WAITING;
		}
	}

	private synchronized void checkReaderAcquisitionWaiting() throws InterruptedException, IOException
	{
		long 		maxWait = MAX_HEARTBEAT_LAPSE;
		try
		{
			while ( (fReaderAcquisitionState == ReaderAcquisitionState.WAITING) && !fDone )
			{
				if ( maxWait <= 0 )
				{
					throw new SocketTimeoutException("Timeout: recv failed");
				}

				long		ticks = System.currentTimeMillis();
				wait(maxWait);
				long		elapsed = System.currentTimeMillis() - ticks;
				maxWait -= elapsed;
			}
		}
		catch ( InterruptedException e )
		{
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	private synchronized void sendHeartbeat() throws IOException
	{
		if ( fOpen && !fDone )
		{
			sendToClient(fHeartbeatMessage);
			flush();
		}
	}

	private synchronized void handleIdle(SocketTimeoutException e, long startTicks) throws SocketTimeoutException
	{
		if ( (fIdleTimeout == null) || ((System.currentTimeMillis() - startTicks) > MAX_HEARTBEAT_LAPSE) )
		{
			throw e;
		}
		else
		{
			fListener.notifyIdle(this);
		}
	}

	private synchronized void internalNotify()
	{
		notifyAll();
	}

	private synchronized boolean readerIsAcquired()
	{
		return fReaderAcquisitionState != ReaderAcquisitionState.RELEASED;
	}

	private enum mode
	{
		SERVER,
		CLIENT,
		ACCEPTED_CLIENT
	}

	private class ClientInputStream extends InputStream
	{
		private ClientInputStream(InputStream in)
		{
			fIn = in;
		}

		@Override
		public int read() throws IOException
		{
			long			ticks = System.currentTimeMillis();
			for(;;)
			{
				try
				{
					return fIn.read();
				}
				catch ( SocketTimeoutException e )
				{
					handleIdle(e, ticks);
				}
			}
		}

		@Override
		public int read(byte[] b) throws IOException
		{
			return read(b, 0, b.length);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			long			ticks = System.currentTimeMillis();
			for(;;)
			{
				try
				{
					return fIn.read(b, off, len);
				}
				catch ( SocketTimeoutException e )
				{
					handleIdle(e, ticks);
				}
			}
		}

		@Override
		public void close() throws IOException
		{
			fIn.close();
		}

		private final InputStream 	fIn;
	}

	private class RunLoop extends Thread
	{
		private RunLoop()
		{
			setDaemon(true);
		}

		@Override
		public void run()
		{
			try
			{
				switch ( fMode )
				{
					case ACCEPTED_CLIENT:
					case CLIENT:
					{
						clientRunLoop();
						break;
					}

					case SERVER:
					{
						serverRunLoop();
						break;
					}
				}
			}
			catch ( Exception e )
			{
				boolean wasDone;
				synchronized(this)
				{
					wasDone = fDone;
				}
				if ( !wasDone )
				{
					fListener.notifyException(GenericCommandClientServer.this, e);
				}
			}

			try
			{
				internalClose();
			}
			catch ( InterruptedException ignore )
			{
				// ignore
			}
		}
	}

	private class InternalNotificationListener implements GenericCommandClientServerListener<T>
	{
		public InternalNotificationListener(GenericCommandClientServerListener<T> parent_listener)
		{
			fParentListener = parent_listener;
			fLastException = null;
			fLastLine = null;
		}

		@Override
		public void notifyException(GenericCommandClientServer<T> gen, Exception e)
		{
			fLastException = e;
			internalNotify();
		}

		@Override
		public void notifyIdle(GenericCommandClientServer<T> client)
		{
			if ( fParentListener != null )
			{
				fParentListener.notifyIdle(client);
			}
			internalNotify();
		}

		@Override
		public void notifyClientAccepted(GenericCommandClientServer<T> generic_command_client_server, GenericCommandClientServer<T> client)
		{
		}

		@Override
		public void notifyClientServerClosed(GenericCommandClientServer<T> gen)
		{
			fLastLine = null;
			internalNotify();
		}

		@Override
		public void notifyLineReceived(GenericCommandClientServer<T> generic_command_client_server, String line, GenericCommandClientServerReader reader) throws Exception
		{
			if ( line == null )
			{
				System.out.println("Null line");
			}
			fLastLine = line;
			internalNotify();
		}

		@Override
		public InputStream notifyCreatingInputStream(GenericCommandClientServer<T> client, InputStream in) throws Exception
		{
			return in;
		}

		@Override
		public OutputStream notifyCreatingOutputStream(GenericCommandClientServer<T> client, OutputStream out) throws Exception
		{
			return out;
		}

		public Exception getLastException()
		{
			return fLastException;
		}

		public String getLastLine()
		{
			return fLastLine;
		}

		private final GenericCommandClientServerListener<T> fParentListener;
		private volatile Exception 							fLastException;
		private volatile String 							fLastLine;
	}

	private class InternalReader implements GenericCommandClientServerReader
	{
		public InternalReader()
		{
			fSafeToUse = new AtomicInteger(0);
		}

		@Override
		public int read() throws IOException
		{
			assert isSafeToUse();

			return fIn.read();
		}

		@Override
		public String readLine() throws IOException
		{
			assert isSafeToUse();

			return fIn.readLine();
		}

		@Override
		public ChunkedByteArray readBytes(int size) throws IOException
		{
			assert isSafeToUse();

			return internalReadBytes(size);
		}

		@Override
		public void release()
		{
			internalReleaseReader();
		}

		void 	setSafeToUse(boolean value)
		{
			if ( value )
			{
				fSafeToUse.incrementAndGet();
			}
			else
			{
				fSafeToUse.decrementAndGet();
			}
		}

		boolean isSafeToUse()
		{
			return fSafeToUse.get() > 0;
		}

		private final AtomicInteger 	fSafeToUse;
	}

	private static final int 	BACKLOG = 256;
	private static final int	MAX_HEARTBEAT_LAPSE = 5 * 60 * 1000;	// 5 minutes
	private static final int	HEARTBEAT_TICKS = MAX_HEARTBEAT_LAPSE / 3;
	private static final int	HEARTBEAT_SLEEP_TICKS = HEARTBEAT_TICKS / 2;

	private static final ExecutorService 		fThreadPool = Executors.newCachedThreadPool
	(
		new ThreadFactory()
		{
			@Override
			public Thread newThread(Runnable r)
			{
				Thread		thread = new Thread(r);
				thread.setDaemon(true);
				return thread;
			}
		}
	);

	private static final ConcurrentHashMap<GenericCommandClientServer<?>, Integer> 	fAll = new ConcurrentHashMap<GenericCommandClientServer<?>, Integer>();

	private static final Thread 		fHeartbeatThread = new Thread()
	{
		@Override
		public void run()
		{
			try
			{
				//noinspection InfiniteLoopStatement
				for(;;)
				{
					Thread.sleep(HEARTBEAT_SLEEP_TICKS);

					long 		now = System.currentTimeMillis();
					for ( final GenericCommandClientServer<?> gen : fAll.keySet() )
					{
						if ( !gen.readerIsAcquired() )
						{
							if ( (now - gen.fLastFlushTicks) >= HEARTBEAT_TICKS )
							{
								try
								{
									gen.sendHeartbeat();
								}
								catch ( IOException dummy )
								{
									gen.close();
								}
							}
						}
					}
				}
			}
			catch ( InterruptedException dummy )
			{
				// do nothing
			}
		}
	};
	
	private enum ReaderAcquisitionState
	{
		RELEASED,
		NEEDS_ONE_LINE,
		WAITING
	}

	static
	{
		fHeartbeatThread.setDaemon(true);
		fHeartbeatThread.start();
	}

	private final List<GenericCommandClientServer<T>> 		fClients;
	private final Thread 									fThread;
	private volatile GenericCommandClientServerListener<T> 	fListener;
	private final boolean 									fSSL;
	private final mode 										fMode;
	private final Socket 									fSocket;
	private final ServerSocket 								fServerSocket;
	private final LineReader fIn;
	private final OutputStream 								fOut;
	private final GenericCommandClientServer<T> 			fParentServer;
	private final String 									fConnectedToAddress;
	private final AtomicLong 								fLastUse;
	private volatile boolean 								fDone;
	private volatile boolean 								fOpen;
	private volatile boolean 								fIsNew;
	private volatile long 									fLastFlushTicks;
	private volatile String 								fHeartbeatMessage;
	private volatile T 										fUserValue;
	private volatile ReaderAcquisitionState 				fReaderAcquisitionState;
	private volatile Integer 								fIdleTimeout;
	private final InternalReader 							fReader;
}
