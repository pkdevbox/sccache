## Description ##
CCDB2 is an extremely fast key to object database and is useful for situations where you have a large number of objects that will be read most of the time and written to occasionally. It is highly optimized for fast reads. It is also optimized for fast loading from disk.

## Use Cases ##
  * Very good for very fast reads/writes.
  * Very good when the number of objects is not more than 5 million or so (depending on memory and key size).
  * Very good when the number of reads is orders of magnitude more than the number of writes.

## API ##

The client API is contained in the class `CCDB2Instance`. An additional class, `CCDB2Driver` is used for client required operations.

_CCDB2Driver_<br>
Clients must implement the CCDB2Driver interface and pass an instance to CCDB2Instance. CCDB2Instance will call its methods as needed:<br>
<table><thead><th>Method</th><th>Description</th></thead><tbody>
<tr><td>public void handleException(Exception e)</td><td>Called if an exception occurs inside of CCDB2.</td></tr>
<tr><td>public void log(String s, Throwable e, boolean newline)</td><td>Called when a diagnostic can be logged. Clients can safely NOP this method.</td></tr>
<tr><td>public String getDBExtension()</td><td>Return the file extension to use for DB files. A good values is ".db"</td></tr>
<tr><td>public String getIndexExtension()</td><td>Return the file extension to use for index files. A good values is ".idx"</td></tr>
<tr><td>public boolean doChunking()</td><td>Return true to read/write objects in chunks. Clients, in general, should return true.</td></tr>
<tr><td>public void callRemoveObject(String key)</td><td>Called when objects are being removed from a group. Call remove() with the specified key.</td></tr>
<tr><td>public long getAllocationChunkSize()</td><td>CCDB2 grows its file in chunks. This method should return the desired chunk size. Return 0 to use the default size of 16MB</td></tr>