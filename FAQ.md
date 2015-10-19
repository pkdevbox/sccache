**Why another cache?**<br>
At SHOP.COM, we've tried almost every cache system there is. None of them do what we need.<br>
<br>
<b>What's different about SHOP.COM Cache?</b><br>
<code>*</code> It doesn't try to be a database<br>
<code>*</code> It can handle very large objects and very large keys<br>
<code>*</code> Cached objects can survive a server crash<br>
<code>*</code> Has both a local (in-memory) and external/shared cache<br>
<code>*</code> Extremely fast - >20K operations a minute (depending on hardware)<br>
<code>*</code> Highly scalable - sccache scales horizontally extremely well<br>
See the <a href='Comparison.md'>Comparison</a> wiki for more.<br>
<br>
<b>What's the difference between "sccache" and "SHOP.COM Cache"?</b><br>
They're the same. When I get tired of writing out SHOP.COM Cache I use sccache.<br>
<br>
<b>What kinds of things can be cached?</b><br>
Any Serializable object. In practice, it's common to cache database result sets, HTML pages, HTTP sessions, etc.<br>
<br>
<b>Why open source it?</b><br>
Our cache was written by one engineer (me). We'd like more eyes on the code and help maintaining it.<br>
<br>
<b>How stable is the code?</b><br>
sccache has been used at SHOP.COM in one form or another for 10 years. So, it's stable enough to use in our production environment. <b>NOTE:</b> We are now using this version of sccache on the SHOP.COM livesite!<br>
<br>
<b>TBD</b> - complete this FAQ