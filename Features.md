**In-process cache and external, shared Cache**<br>
sccache is architected for large server farms. Objects are stored locally in a JVM as well as on a farm of shared cache servers. When an object is cached, you get the benefit of local storage and other servers in your farm have access to the object.<br>
<br>
<b>Horizontally Scalable</b><br>
Unlimited objects can be cached by adding additional cache servers. sccache will evenly distribute objects amongst the entire farm of cache servers. If a cache server in the farm fails, automatically notices and reconfigures itself to not use that server. It will periodically check to see if the server becomes available and, if it does, automatically add it back to its set of usable servers.<br>
<br>
<b>Data stored to disk</b><br>
Cache servers are not limited to available memory. All cached objects are written to disk. This allows cache servers to survive restarts and avoid a "cold start" scenario. See <a href='CCDB2.md'>CCDB2</a> for details.<br>
<br>
<b>Associative Keys</b><br>
Objects in the cache can be associated with one or more groups. A group is identified by a number ID. All objects in a group can be listed or deleted with one call.<br>
<br>
<b>Non-Transactional</b><br>
Yes, this is a <i>feature</i>. Transactions are slow. A cache should not be used as a database. The purpose of a cache is to provide fast access to post-calculated results that are expensive to calculate.<br>
<br>
<b>Any size key / any size data</b><br>
Key size and data size are limited only by memory. At SHOP.COM we have keys that are several K bytes long and data that is multiple megabytes.<br>
<br>
<b>Auto-GC based on TTL</b><br>
sccache automatically cleans up stale objects as well as stale database/storage files.<br>
<br>
<b>Container/Platform neutral</b><br>
sccache is totally self-contained. It doesn't rely on any third party libraries or frameworks.<br>
<br>
<b>Iterate over keys</b><br>
You can query for the set of keys currently in the cache.<br>
<br>
<b>Regular Expression matching over keys</b><br>
You can query via regular expression for the set of matching keys in the cache.