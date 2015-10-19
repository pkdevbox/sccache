### Background ###
sccache can be used in a small setup consisting of one server or scale to large farm of servers. This doc will describe how sccache is scaled at SHOP.COM. At SHOP.COM, we run an MS Windows environment, so this doc will be based on that.

### Determining Number of Servers ###
We've found that each cache server can comfortably run at approx. 5,000 transactions a minute with around 500,000 objects cached. This assumes a very large server: a 4 core, 64-bit box with 8GB memory and very fast disk I/O. As a cache server approaches this, you should add a new server. For anything other than a small environment, it's a good idea to have at least 2 cache servers (for failover purposes).

### JVM Options for the Server ###
These are the JVM options we set for our cache servers running Sun's Java 6 64-bit VM:
```
-server -Xms3g -Xmx6g -Xrs -XX:InitialSurvivorRatio=1 -XX:MinSurvivorRatio=1 
-XX:MaxTenuringThreshold=31 -XX:+UseParallelGC -XX:+UseParallelOldGC 
-XX:+UseParallelOldGCCompacting -XX:-UsePSAdaptiveSurvivorSizePolicy
-XX:ParallelGCThreads=4 -XX:GCTimeRatio=15
```

### Client-side Issues ###
The in-process cache on the clients prefers a lot of memory. We run our app servers in a 32-bit Java 6 VM with 1GB of RAM. This said, the in-process cache can cope with any memory footprint because `SoftReferences` are used.