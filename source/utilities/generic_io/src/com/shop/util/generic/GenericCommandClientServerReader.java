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
import java.io.IOException;

/**
 * Reading mechanism
 */
public interface GenericCommandClientServerReader
{
	/**
	 * Call to read a line
	 *
	 * @return the line
	 * @throws IOException errors
	 */
	public String 				readLine() throws IOException;

	/**
	 * Call to read n bytes
	 *
	 * @param size number of bytes to read
	 * @return the bytes
	 * @throws IOException errors
	 */
	public ChunkedByteArray 	readBytes(int size) throws IOException;

	/**
	 * Read 1 byte.
	 *
	 * @return the byte or -1 if EOF
	 * @throws IOException errors
	 */
	public int					read() throws IOException;

	/**
	 * MUST be called to release this reader when you are finished
	 */
	public void					release();
}
