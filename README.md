# olca-native

## Build

```commandline
mvn install -DskipTests=true
```

When modifying dependencies of `olca-native-test`, it is necessary to clean the
target folders with:

```commandline
mvn clean install -DskipTests=true
```

## Test

- Remove/Rename `$HOME/openLCA-data-1.4/native/1.1.0/Linux/amd64` folder.
- Simply run `LibraryDownloadTest.testFetchSparseLibs` function from
`olca-native-test`.

## Instructions:

>Next thing (for this week) you could help with is to develop a concept for packaging the native libraries. There are other Maven libraries which package native code where we can learn from (would be good if you could find some examples). I think we should have something like a parent module which contains the general machinery for copying and loading the libraries. Then, there should be a Maven sub-module for each platform we support (win x64, linux x64, macOS x64 + arm64). This sub-module should contain the native libraries and an index file which lists the load-order. For each platform, we need a version with and without UMFPACK support. Then we need to think about how we package the libraries with openLCA... Currently I checked the libraries for win and macOS x64 into the repo via Git LFS (https://github.com/GreenDelta/olca-modules/tree/master/olca-core/src/main/resources/native); they are loaded like this: https://github.com/GreenDelta/olca-modules/blob/master/olca-core/src/main/java/org/openlca/julia/Julia.java#L74
> But I would very much like to get rid of this and do not check in the binary files into the repo...

>Here is something similar but not fully what I want: https://github.com/intel-analytics/BigDL-core/tree/master/native-dnn

>The Linux binaries are nicely packaged here, but it also contains the Java classes. I want a module that contains the JNI Java code and library loading logic and then platform jars that just contain just the native dlls and an index file for the load order. In principle it is just bringing all we have into a nicer design. I will sketch more about this design later this week but currently have to prepare some other projects...
>https://search.maven.org/artifact/com.intel.analytics.bigdl.core.native.dnn/dnn-java-x86_64-linux/2.0.0/jar


```
olca-native  -> generic module
  /org/openlca/nativelib/NativeLib  -> library loading mechanics
  /org/openlca/nativelib/Blas       -> Blas & Lapack bindings
  /org/openlca/nativelib/Umfpack    -> Umfpack bindings

olca-native-blas-macos-arm64
  /org/openlca/nativelib/index.txt -> Blas load order
  /org/openlca/nativelib/openblas.dylib
  /org/openlca/nativelib/olca-native.dylib
  ...

olca-native-umfpack-macos-arm64
  /org/openlca/nativelib/index.txt -> Blas + Umfpack load order
  /org/openlca/nativelib/openblas.dylib
  /org/openlca/nativelib/umfpack.dylib
  /org/openlca/nativelib/olca-native-umfpack.dylib
  ...

etc...
```

>I think it would be better to do this in a separate repository. I want to deploy the olca-native artifacts independently from the olca-modules. Also, I would like to merge the things from olca-rust into that new repo. And, I think we need some iterations for finding the right design for this. So, better create a fresh repo for this. Thanks

