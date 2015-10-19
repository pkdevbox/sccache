### Overview ###

<table cellpadding='5' border='1'>
<tr>
<td>
API<br>
</td>
<td>
SHOP.COM Cache high-level API. This API is implementation neutral. Any cache library could potentially be plugged into<br>
this API. The API is mostly interfaces with some generic implementation code.<br>
<blockquote><table cellpadding='5' border='1'>
<tr>
<blockquote><td>
Server<br>
</td>
<td>
<i>Cache server interfaces and support code</i>
</td>
</blockquote></tr>
<tr>
<blockquote><td>
Client<br>
</td>
<td>
<i>Cache client interfaces and support code</i>
</td>
</blockquote></tr>
<tr>
<blockquote><td>
Storage<br>
</td>
<td>
<i>Interfaces for writing cached objects to disk</i>
</td>
</blockquote></tr>
</table>
</td>
</tr></blockquote>

<tr>
<td>
Implementation<br>
</td>
<td>
A standard implementation of the SHOP.COM Cache API (the implementation used at SHOP.COM).<br>
<blockquote><table cellpadding='5' border='1'>
<tr>
<blockquote><td>
Server/Client<br>
</td>
<td>
<i>SHOP.COM cache client and server implementation</i>
</td>
</blockquote></tr>
<tr>
<blockquote><td>
Storage<br>
</td>
<td>
<i>Wrapper around CCDB2 as a storage implementation</i>
</td>
</blockquote></tr>
</table>
</td>
</tr></blockquote>

<tr>
<td>
Utilities<br>
</td>
<td>
Utilities used by the API, the SHOP.COM implementation or both<br>
<blockquote><table cellpadding='5' border='1'>
<tr>
<blockquote><td>
ChunkedByteArray<br>
</td>
<td>
<i>Abstracts an unbounded array that is internally broken into chunks. This avoids allocating large contiguous byte arrays which is very inefficient in Java.</i>
</td>
</blockquote></tr>
<tr>
<blockquote><td>
Generic I/O<br>
</td>
<td>
<i>General purpose TCP/IP server and client</i>
</td>
</blockquote></tr>
<tr>
<blockquote><td>
CCDB2<br>
</td>
<td>
<i>A high performance key-to-object database</i>
</td>
</blockquote></tr>
</table>
</td>
</tr>
</table><br></blockquote>

<h3>Local Cache and External Cache</h3>
sccache offers an in-process cache and an external shared cache.<br>
<br>
The in-process cache is abstracted by the <code>SCMemoryCache</code> interface. The default implementation uses the JDK <code>ConcurrentHashMap</code> and <code>SoftReference</code>s. Additionally, it maintains a background thread that periodically checks local cached objects against the shared external cache for staleness.<br>
<br>
The external shared cache consists of one or more servers. Clients communicate with the servers via TCP/IP using a simple telnet-style protocol. When there are multiple servers, the clients treat them as a single "HashMap" writing objects based on the hash code of the object's key.<br>
<br>
When an object is requested from the cache, the local in-process cache is checked. If the object is found, it is returned. If not found, the shared external cache is checked. If found, it is added to the local in-process cache and then returned.<br>
<br>
<h3>Client Architecture</h3>
The Client API loosely resembles a HashMap. It has methods to <code>put</code> objects referenced by a key and methods to <code>get</code> objects.<br>
<br>
When an object is put into the cache (via <code>SCCache.put(...)</code>), it is added to the in-process cache and then put into a queue that, in the background, sends objects to the shared external caches. This makes the put() operation extremely fast.<br>
<br>
Via the <code>SCManager</code> API, sccache clients maintain multiple connections to the cache servers. If a server goes down for some reason, the client code will notice this and remove that server from its internal list. In the background, it will periodically check to see if the server comes back up, adding back to its list if it does.<br>
<br>
For objects that require more failure safety (an HTTPSession for example), you can use the <code>SCCache.putWithBackup(...)</code> method to write the object to its hashed server as well as a backup server. It the object's designated server goes down, the object will still be available from the backup server.<br>
<br>
<h3>Server Architecture</h3>
sccache Servers listen on a designated port for Client connections.<br>
<br>
TBD