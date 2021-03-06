package org.openlca.nativelib;

import org.openlca.core.DataDir;
import org.openlca.util.OS;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Optional;
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

	private static final AtomicBoolean _loaded = new AtomicBoolean(false);

	/**
	 * Returns true if the native libraries with openLCA bindings are loaded.
	 */
	public static boolean isLoaded() {
		return _loaded.get();
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

		var indexInputStream = NativeLib.class.getResourceAsStream("index.txt");
		if (indexInputStream == null) {
			log.info("No libraries were found in the index file, thus, nothing has " +
				"been loaded.");
			return false;
		}

		try (var br = new BufferedReader(new InputStreamReader(indexInputStream))) {
			String lib;
			while((lib = br.readLine()) != null) {
				var libFile = new File(dir, lib);
				if (libFile.exists())
					continue;
				try {
					copyLib(lib, libFile);
				} catch (Exception e) {
					log.error("Failed to extract library " + lib, e);
					return false;
				}
			}
		} catch (IOException e) {
			log.info("Could not parse the index file with: {}", e.getMessage());
			return false;
		}
		return loadFromDir(dir);
	}

	private static void copyLib(String lib, File file) throws IOException {
		var is = NativeLib.class.getResourceAsStream(lib);
		var os = new FileOutputStream(file);
		byte[] buf = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			os.write(buf, 0, len);
		}
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
			var indexInputStream = NativeLib.class.getResourceAsStream("index.txt");
			if (indexInputStream == null) {
				log.info("No libraries were found in the index file, thus, nothing has " +
					"been loaded.");
				return false;
			}

			try (var br = new BufferedReader(new InputStreamReader(indexInputStream))) {
				String lib;
				while((lib = br.readLine()) != null) {
					File f = new File(dir, lib);
					System.load(f.getAbsolutePath());
					log.info("Loaded native library {}", f);
				}
				_loaded.set(true);
				return true;
			} catch (Error | IOException e) {
				log.error("Failed to load native libs from " + dir, e);
				return false;
			}
		}
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
