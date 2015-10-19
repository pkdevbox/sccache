### Introduction ###
The [SHOP.COM](http://www.shop.com) Cache System (sccache) is a high performance, highly scalable object caching system. It is a 100% Java library and can cache any object of any size that implements `Serializable`. Cached objects are referenced by `String` keys of any length. sccache is used by SHOP.COM to cache generated HTML pages, SQL queries, HTTP Sessions, POJO objects, etc.

The sccache system consists of three major components: Servers, Clients and Storage. sccache presents an abstract interface to these components that is implementation neutral. If needed, custom implementations can be written for any of the components. For most uses, however, the default SHOP.COM implementation will be used.

_Clients_<br>
sccache Clients are any application that needs to consume/produce cached objects. The class that abstracts an sccache Client is <code>SCCache</code>. <code>SCCache</code> has methods to get objects from the cache, put objects into the cache, etc. Here's a sample of the available methods:<br>
<pre><code>public Object get(...);<br>
public void put(...);<br>
public List&lt;String&gt; listGroup(...); // sccache supports associative keys<br>
public List&lt;String&gt; regExRemove(String expression);<br>
public List&lt;String&gt; dumpStats(boolean verbose);<br>
</code></pre>

<i>Server</i><br>
sccache Servers are shared repositories for objects. TBD<br>
<br>
<i>Storage</i><br>
The sccache storage API provides a framework for sccache Servers to write cached objects to disk. TBD<br>
<br>
<h3>Details</h3>
See <a href='Architecture.md'>Architecture</a>.<br>
<br>
<h3>Javadoc</h3>
<a href='http://jordanzimmerman.com/sccache/frameset.html'>Click here for online Javadoc</a>

<h3>Utilities</h3>
<ul><li><a href='SCCacheObject.md'>SCCacheObject</a>
</li><li><a href='ChunkedByteArray.md'>ChunkedByteArray</a>
</li><li><a href='CCDB2.md'>CCDB2</a>