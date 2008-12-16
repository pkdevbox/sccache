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

/**
 * The notification object for client/server events<br>
 */
public interface GenericCommandClientServerListener<T>
{
	/**
	 * Called when an exception occurs - in most cases you should close the client/server
	 *
	 * @param gen the client/server that generated the exception
	 * @param e the exception
	 */
	public void 		notifyException(GenericCommandClientServer<T> gen, Exception e);

	/**
	 * Called when the server accepts a connection from a client
	 *
	 * @param server the server
	 * @param client the client
	 */
	public void 		notifyClientAccepted(GenericCommandClientServer<T> server, GenericCommandClientServer<T> client);

	/**
	 * Called when a client connection or server is closed
	 *
	 * @param gen the client/server that is closing
	 */
	public void 		notifyClientServerClosed(GenericCommandClientServer<T> gen);

	/**
	 * Called when a command is received for a client
	 *
	 * @param client_server the client receiving the command
	 * @param line the command
	 * @param reader can be used to read additional data (including raw bytes) from the stream. IMPORTANT: this reader becomes invalid after the notify_line_received() completes
	 * @throws Exception on any error
	 */
	public void 		notifyLineReceived(GenericCommandClientServer<T> client_server, String line, GenericCommandClientServerReader reader) throws Exception;

	/**
	 * Gives an opportunity to wrap the input stream if needed.
	 *
	 * @param client the client
	 * @param in the client's stream
	 * @return either a wrapped stream or the parameter "in" unchanged
	 * @throws Exception on any error
	 */
	public InputStream 	notifyCreatingInputStream(GenericCommandClientServer<T> client, InputStream in) throws Exception;

	/**
	 * Gives an opportunity to wrap the output stream if needed.
	 *
	 * @param client the client
	 * @param out the client's stream
	 * @return either a wrapped stream or the parameter "out" unchanged
	 * @throws Exception on any error
	 */
	public OutputStream notifyCreatingOutputStream(GenericCommandClientServer<T> client, OutputStream out) throws Exception;

	/**
	 * Called when an idle notification timeout has been set (via {@link GenericCommandClientServer#setIdleNotificationTimeout}) and a
	 * read has taken longer than the timeout. If there is a problem, close the client to indicate so.
	 *
	 * @param client the client
	 */
	public void 		notifyIdle(GenericCommandClientServer<T> client);
}
