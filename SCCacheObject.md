Normally, you attempt a get() and, if it's not in the cache, allocate a new instance of the object and add it to the cache. `SCCacheObject` wraps this into a concise pattern.

`SCCacheObject` works with the `SCCacheObjectBuilder` class. `SCCacheObjectBuilder` is responsible for allocating new instances of objects when they are missing (or stale) in the cache. Usually, the `SCCacheObjectBuilder` instances are passed as anonymous inner classes to `SCCacheObject`.

Here is boilerplate code for caching an object of type `MyObject`:
```
SCCacheObject<MyObject>   cacher = new SCCacheObject<MyObject>
(
	theCache,
	new SCDataBlock(theKey),
	new SCCacheObjectBuilder<MyObject>()
	{
		@Override
		public MyObject buildObject() throws Exception
		{
			return new MyObject();
		}
	}
);
MyObject  obj = cacher.get();
```

The call to `cacher.get()` checks the cache for the object and if found returns it. If not found, it calls the passed in `SCCacheObjectBuilder` to allocate a new instance which is then added to the cache and returned back to the caller.