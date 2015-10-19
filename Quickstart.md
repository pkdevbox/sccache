**Step 1. Get the code**<br>
You can get the binaries and latest source tree from the Downloads tab. The source is also available via <i>svn</i> from the Source tab.<br>
<br>
<b>Step 2. Determine hardware needs</b><br>
If availability isn't a concern and your caching needs are small, you could run everything on one machine. At <a href='http://www.shop.com'>SHOP.COM</a> we have lots of app servers and lots of cache servers. The cache servers are 64 bit Windows servers with 8GB of memory each.<br>
<br>
<b>Step 3. Start cache server(s)</b><br>
You'll find a bundled, runnable server JAR at <code>./ServerApplication/ShopCacheServer.jar</code>. To run it:<br>
<pre><code>java &lt;jvm options&gt; -jar ShopCacheServer.jar -port &lt;p&gt; -path &lt;d&gt;<br>
</code></pre>
where <code>&lt;p&gt;</code> is the port to listen on and <code>&lt;d&gt;</code> is the path to a directory to<br>
store DB files.<br>
<br>
FYI - the ShopCacheServer source is valuable to look at for its use of the sccache APIs: <a href='http://code.google.com/p/sccache/source/browse/trunk/ServerApplication/src/ShopCacheServer.java'>http://code.google.com/p/sccache/source/browse/trunk/ServerApplication/src/ShopCacheServer.java</a>

<b>Step 4. Configure client side</b><br>
In your client application, you access the cache via the <code>SCCache</code> interface. Here's the code to get an instance:<br>
<pre><code>SCClientFactory     clientFactory = ShopComCacheFactory.getClientFactory();<br>
SCClientContext     context = clientFactory.newContext();<br>
context.address(new InetSocketAddress(/*address*/, /*port number*/));<br>
SCClientManager     manager = clientFactory.newClientManager(context);<br>
<br>
myCache = new SCCache(manager);<br>
</code></pre>
If you have a farm of cache servers, create the <code>SCCache</code> instance this way:<br>
<pre><code>List&lt;SCClientManager&gt; clientSet = new ArrayList&lt;SCClientManager&gt;();<br>
SCClientFactory       clientFactory = ShopComCacheFactory.getClientFactory();<br>
<br>
SCClientContext       context = clientFactory.newContext();<br>
context.address(new InetSocketAddress(/*address 1*/, /*port number 1*/));<br>
SCClientManager       manager = clientFactory.newClientManager(context);<br>
clientSet.add(manager);<br>
<br>
// add additional managers for each cache server<br>
<br>
myCache = new SCCache(new SCMultiManager(clientSet));<br>
</code></pre>

<b>Step 5. Use the cache in your code</b><br>
This is the standard code for caching objects:<br>
<pre><code>// see if your object is in the cache<br>
MyObject    obj = (MyObject)myCache.get(new SCDataBlock(myKey));<br>
<br>
// if it's not, allocate it and add it to the cache<br>
if ( obj == null )<br>
{<br>
    obj = new MyObject();<br>
    myCache.put(new SCDataBlock(myKey, obj));<br>
}<br>
</code></pre>