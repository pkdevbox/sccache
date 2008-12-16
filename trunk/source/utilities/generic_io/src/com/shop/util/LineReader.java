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
package com.shop.util;

import java.io.*;

/**
 * Listens for administration commands<br>
 *
 * @author Jordan Zimmerman
 */
public class LineReader extends InputStream
{
	public LineReader(InputStream in)
	{
		in_mbr = in;
		buffer_mbr = new StringBuilder();
		last_was_cr_mbr = false;
		pushback_char_mbr = 0;
	}

	@Override
	public synchronized int 			read() throws IOException
	{
		if ( pushback_char_mbr == 0 )
		{
			return stream_read();
		}
		else
		{
			return pushback_read();
		}
	}

	public synchronized String		read_line() throws IOException
	{
		buffer_mbr.setLength(0);

		boolean		eof = false;
		for(;;)
		{
			int		b = read();
			if ( b == -1 )
			{
				eof = true;
				break;
			}

			if ( b == '\r' )
			{
				last_was_cr_mbr = true;
				break;
			}

			if ( b == '\n' )
			{
				break;
			}
            buffer_mbr.append((char)(b & 0xFF));
		}
		return ((buffer_mbr.length() == 0) && eof) ? null : buffer_mbr.toString();
	}

	@Override
	public synchronized void 	close() throws IOException
	{
		super.close();
		buffer_mbr = null;
		in_mbr.close();
	}

	/**
	 * Push the given char so that the next() read will return this char
	 * @param c char to push back
	 */
	public synchronized void	pushback(int c)
	{
		pushback_char_mbr = c;
	}

	private int pushback_read()
	{
		int		b = pushback_char_mbr;
		pushback_char_mbr = 0;
		return b;
	}

	private int stream_read() throws IOException
	{
		int				b = in_mbr.read();
		if ( last_was_cr_mbr )
		{
			last_was_cr_mbr = false;

			char	c = (char)(b & 0xff);
			if ( c == '\n' )
			{
				return read();
			}
		}

		return b;
	}

	private InputStream			in_mbr;
	private StringBuilder		buffer_mbr;
	private boolean				last_was_cr_mbr;
	private int					pushback_char_mbr;
}
