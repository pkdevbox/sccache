# Design Choices #

Here are some of the design choices that have been made in the development of the SHOP.COM Cache.

**No transactions**<br>
If you need transactions, you should use a database. A cache should be used to provide quick access to data that doesn't change very often.<br>
<br>
<b>Memory based index</b><br>
The default implementation stores the cache index in memory. This should be considered when planning hardware needs.<br>
<br>
<b>Maximum TTL</b><br>
The default implementation assumes a predefined maximum TTL as a compile time constant. Objects in the cache cannot exceed this maximum (12 hours by default).<br>
<br>
<b>Optimized for reads over writes</b><br>
The cache is highly optimized for fast reads.<br>
<br>
<b>SoftReferences and no object qty limit</b><br>
There are no object qty limits. Instead, sccache relies on SoftReferences and the JVM. However, this has GC implications. You may need to tune your java command line arguments.<br>
<br>
---<br>
<br>
<b>TBD</b> - complete this page