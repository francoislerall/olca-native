import org.openlca.nativelib.NativeLib;

public class LibraryDownload {

	/**
	 *  Load the native libraries by mean of the OS-specific index file provided
	 *  with olca-native submodule. (see Maven dependencies)
	 *  Return true if the libraries are loaded.
	 */
	public static boolean loadNativeLib() {
		return NativeLib.load();
	}

	public static boolean isLoaded() {
		return NativeLib.isLoaded();
	}
}
