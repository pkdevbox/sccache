### Description ###

Abstracts an unbounded array that is internally broken into chunks. This avoids allocating large contiguous byte arrays which is very inefficient in Java. The ChunkedByteArray contrasts with JDK classes such as ByteBuffer, byte[.md](.md), ByteArrayOutputStream, etc. As those JDK classes get larger, they get more inefficient. In particular, ByteArrayOutputStream must copy old data as it grows. Further, the memory allocator in Java is not optimized for large objects. Smaller objects get allocated in the young heap and are allocated very fast. The ChunkedByteArray takes advantage of this by only allocating relatively small objects but maintaining an arbitrarily large logical size to clients of the class.

The default chunk size is 32K. This can be changed by setting the system property ChunkedByteArrayDefaultSize to any number of bytes you like.

### Stream Wrappers ###
You can use ChunkedByteArray with the Java streams classes via the wrappers: ChunkedByteArrayOutputStream and ChunkedByteArrayInputStream.