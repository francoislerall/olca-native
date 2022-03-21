import org.openlca.nativelib.NativeLib;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
