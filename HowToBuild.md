# Introduction #

SHOP.COM Cache is self-contained and can be easily compiled using any build system you like or from the command line using `javac`. The source is distributed as a several modules that can be compiled into separate JARs. If you prefer, though, you can compile everything into one JAR.

# Modules #

Because of internal dependencies, the source should be built in the following steps:

  1. Build the `chunked` module. It's used by nearly every other module.
  1. Build the `generic_io` module with `chunked` in the CLASSPATH.
  1. Build the `api` module with `chunked` in the CLASSPATH.

The above constitute the API portion of SHOP.COM cache. If you are using a custom implementation you can stop here. To build the default implementation:

  1. Build the `ccdb2` module with `chunked` in the CLASSPATH.
  1. Build the `common` module with `api` in the CLASSPATH.
  1. Build the `storage` module with `chunked`, `ccdb2` and `api` in the CLASSPATH.
  1. Build the `client` module with `chunked`, `generic_io`, `common` and `api` in the CLASSPATH.
  1. Build the `server` module with `chunked`, `generic_io`, `common` and `api` in the CLASSPATH.

# Dependencies #
Here is a summary of the dependencies:

| **Module** | **Directory** | **Dependencies** |
|:-----------|:--------------|:-----------------|
| chunked    | _sccache_/source/utilities/chunked | _none_           |
| generic\_io | _sccache_/source/utilities/generic\_io | chunked          |
| ccdb2      | _sccache_/source/utilities/ccdb2 | chunked          |
| api        | _sccache_/source/api | chunked          |
| common     | _sccache_/source/implementation/common | api              |
| storage    | _sccache_/source/implementation/storage | chunked, ccdb2, api |
| client     | _sccache_/source/implementation/client | chunked, generic\_io, common, api |
| server     | _sccache_/source/implementation/server | chunked, generic\_io, common, api |


# Compiling the Modules #

For Windows users, there is a batch file: `build.bat`. Build files for other platforms is TBD.

Here are the command lines to build each module.

**chunked**
```
cd source/utilities/chunked/src
javac -d ../classes ./com/shop/util/chunked/*.java
```

**generic\_io**
```
cd source/utilities/generic_io/src
javac -d ../classes -cp ../../chunked/classes ./com/shop/util/*.java ./com/shop/util/generic/*.java
```

**ccdb2**
```
cd source/utilities/ccdb2/src
javac -d ../classes -cp ../../chunked/classes ./com/shop/util/ccdb2/*.java
```

**api**
```
cd source/api/src
javac -d ../classes -cp ../../utilities/chunked/classes ./com/shop/cache/api/client/io/*.java ./com/shop/cache/api/client/main/*.java ./com/shop/cache/api/commands/*.java ./com/shop/cache/api/server/*.java ./com/shop/cache/api/storage/*.java ./com/shop/cache/api/common/*.java
```

**common**
```
cd source/implementation/common/src
javac -d ../classes -cp ../../../api/classes ./com/shop/cache/imp/common/*.java
```

**storage**
```
cd source/implementation/storage/src
javac -d ../classes -cp ../../../api/classes;../../../utilities/chunked/classes;../../../utilities/ccdb2/classes ./com/shop/cache/imp/storage/ccdb2/*.java
```

**client**
```
cd source/implementation/client/src
javac -d ../classes -cp ../../../api/classes;../../../utilities/chunked/classes;../../../utilities/generic_io/classes;../../common/classes ./com/shop/cache/imp/client/*.java
```

**server**
```
cd source/implementation/server/src
javac -d ../classes -cp ../../../api/classes;../../../utilities/chunked/classes;../../../utilities/generic_io/classes;../../common/classes ./com/shop/cache/imp/server/*.java
```