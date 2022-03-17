package org.openlca.nativelib;

import org.openlca.core.DataDir;
import org.openlca.util.OS;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the Java interface for the native libraries and contains some
 * utility methods for loading these libraries.
 */
public final class NativeLib {

	/**
	 * The version of the native interface that is used.
	 */
	public static final String VERSION = "1.1.0";

	private enum LinkOption {
		NONE, BLAS, ALL
	}

	private static final AtomicBoolean _loaded = new AtomicBoolean(false);
	private static final AtomicBoolean _withSparse = new AtomicBoolean(false);

	/**
	 * Returns true if the native libraries with openLCA bindings are loaded.
	 */
	public static boolean isLoaded() {
		return _loaded.get();
	}

	public static boolean hasSparseLibraries() {
		return _withSparse.get();
	}

	public static synchronized boolean fetchSparseLibraries() {
		if (isLoaded() && hasSparseLibraries())
			return true;
		try {
			new LibraryDownload().run();
		} catch (Exception e) {
			return false;
		}
		_loaded.set(false);
		return load();
	}

	/**
	 * Get the default location on the file system where our native libraries
	 * are located.
	 */
	public static File getDefaultDir() {
		var root = DataDir.root();
		var arch = System.getProperty("os.arch");
		var os = OS.get().toString();
		var path = Strings.join(
				List.of("native", VERSION, os, arch),
				File.separatorChar);
		var log = LoggerFactory.getLogger(NativeLib.class);
		return new File(root, path);
	}

	/**
	 * Tries to load the libraries from the default folder. Returns true if the
	 * libraries could be loaded or if they were already loaded.
	 */
	public static synchronized boolean load() {
		if (_loaded.get())
			return true;
		var log = LoggerFactory.getLogger(NativeLib.class);
		var dir = getDefaultDir();
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				log.error("Could not create library dir {}", dir);
				return false;
			}
		}

		// check if our base BLAS libraries are present and
		// extract them if necessary
		var blasLibs = libs(LinkOption.BLAS);

		for (var lib : blasLibs) {
			var libFile = new File(dir, lib);
			if (libFile.exists())
				continue;
			var arch = System.getProperty("os.arch");
			var jarPath = "/native/" + OS.get().toString()
					+ "/" + arch + "/" + lib;
			log.info("jarpath: {}", jarPath);
			try {
				copyLib(jarPath, libFile);
			} catch (Exception e) {
				log.error("failed to extract library " + lib, e);
				return false;
			}
		}
		log.info("dir in load(): {}", dir);
		return loadFromDir(dir);
	}

	private static void copyLib(String jarPath, File file) throws IOException {
		var is = NativeLib.class.getResourceAsStream(jarPath);
		var os = new FileOutputStream(file);
		byte[] buf = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			os.write(buf, 0, len);
		}
		os.flush();
		os.close();
	}

	/**
	 * Loads the native libraries and openLCA bindings from the given folder.
	 * Return true if the libraries could be loaded (at least there should be a
	 * `libjolca` library in the folder that could be loaded).
	 */
	public static boolean loadFromDir(File dir) {
		Logger log = LoggerFactory.getLogger(NativeLib.class);
		log.info("Try to load native libs and bindings from {}", dir);
		if (_loaded.get()) {
			log.info("Native libs already loaded; do nothing");
			return true;
		}
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			log.warn("{} does not contain the native libraries", dir);
			return false;
		}
		synchronized (_loaded) {
			if (_loaded.get())
				return true;
			try {
				LinkOption opt = linkOption(dir);
				if (opt == null || opt == LinkOption.NONE) {
					log.info("No native libraries found");
					return false;
				}
				for (String lib : libs(opt)) {
					File f = new File(dir, lib);
					System.load(f.getAbsolutePath());
					log.info("loaded native library {}", f);
				}
				_loaded.set(true);
				if (opt == LinkOption.ALL) {
					_withSparse.set(true);
					log.info("Math libraries loaded with UMFPACK support.");
				} else {
					log.info("Math libraries loaded without UMFPACK support.");
				}
				return true;
			} catch (Error e) {
				log.error("Failed to load native libs from " + dir, e);
				return false;
			}
		}
	}

	private static List<String> libs(LinkOption opt) {
		var log = LoggerFactory.getLogger(NativeLib.class);
		if (opt == null || opt == LinkOption.NONE)
			return null;

		var loadIndexURL = NativeLib.class.getResource("index.txt");
		log.info("loadIndexURL: {}", loadIndexURL);
		if (loadIndexURL == null) {
			log.warn("Failed to load the load index URL of the native library.");
			return null;
		} else {
			List<String> loadIndex;
			try {
				var loadIndexPath = Paths.get(loadIndexURL.toURI());
				Charset charset = Charset.defaultCharset();
				loadIndex = Files.readAllLines(loadIndexPath, charset);
			} catch (URISyntaxException | IOException e) {
				log.warn("Failed to load the load index of the native library: {}",
					e.getMessage());
				return null;
			}
			if (loadIndex.isEmpty()) {
				return null;
			}
			return loadIndex;
		}
	}

			/**
	 * Searches for the library which can be linked. When there are multiple link
	 * options it chooses the one with more functions.
	 */
	private static LinkOption linkOption(File dir) {
		if (dir == null || !dir.exists())
			return LinkOption.NONE;
		var files = dir.listFiles();
		var log = LoggerFactory.getLogger(NativeLib.class);
		for (File file : files) {
			log.info("dir.listFiles()[i]: {}", file.toString());
		}

		if (files == null)
			return LinkOption.NONE;
		var opt = LinkOption.NONE;
		for (File f : files) {
			if (!f.isFile())
				continue;
			if (f.getName().contains("olcar_withumf")) {
				return LinkOption.ALL;
			}
			if (f.getName().contains("olcar")) {
				opt = LinkOption.BLAS;
			}
		}
		return opt;
	}

	// BLAS

	/**
	 * Matrix-matrix multiplication: C := A * B
	 *
	 * @param rowsA [in] number of rows of matrix A
	 * @param colsB [in] number of columns of matrix B
	 * @param k     [in] number of columns of matrix A and number of rows of matrix
	 *              B
	 * @param a     [in] matrix A (size = rowsA*k)
	 * @param b     [in] matrix B (size = k * colsB)
	 * @param c     [out] matrix C (size = rowsA * colsB)
	 */
	public static native void mmult(int rowsA, int colsB, int k,
									double[] a, double[] b, double[] c);

	/**
	 * Matrix-vector multiplication: y:= A * x
	 *
	 * @param rowsA [in] rows of matrix A
	 * @param colsA [in] columns of matrix A
	 * @param a     [in] the matrix A
	 * @param x     [in] the vector x
	 * @param y     [out] the resulting vector y
	 */
	public static native void mvmult(int rowsA, int colsA,
									 double[] a, double[] x, double[] y);

	// LAPACK

	/**
	 * Solves a system of linear equations A * X = B for general matrices. It calls
	 * the LAPACK DGESV routine.
	 *
	 * @param n    [in] the dimension of the matrix A (n = rows = columns of A)
	 * @param nrhs [in] the number of columns of the matrix B
	 * @param a    [io] on entry the matrix A, on exit the LU factorization of A
	 *             (size = n * n)
	 * @param b    [io] on entry the matrix B, on exit the solution of the equation
	 *             (size = n * bColums)
	 * @return the LAPACK return code
	 */
	public static native int solve(int n, int nrhs, double[] a, double[] b);

	/**
	 * Inverts the given matrix.
	 *
	 * @param n [in] the dimension of the matrix (n = rows = columns)
	 * @param a [io] on entry: the matrix to be inverted, on exit: the inverse (size
	 *          = n * n)
	 * @return the LAPACK return code
	 */
	public static native int invert(int n, double[] a);

	// UMFPACK
	public static native void umfSolve(
			int n,
			int[] columnPointers,
			int[] rowIndices,
			double[] values,
			double[] demand,
			double[] result);

	public static native long umfFactorize(
			int n,
			int[] columnPointers,
			int[] rowIndices,
			double[] values);

	public static native void umfDispose(long pointer);

	public static native long umfSolveFactorized(
			long pointer,
			double[] demand,
			double[] result);


	public static native long createDenseFactorization(
			int n,
			double[] matrix);

	public static native void solveDenseFactorization(
			long factorization,
			int columns,
			double[] b);

	public static native void destroyDenseFactorization(
			long factorization);

	public static native long createSparseFactorization(
			int n,
			int[] columnPointers,
			int[] rowIndices,
			double[] values);

	public static native void solveSparseFactorization(
			long factorization,
			double[] b,
			double[] x);

	public static native void destroySparseFactorization(
			long factorization);
}
