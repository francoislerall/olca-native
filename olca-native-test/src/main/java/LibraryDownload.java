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
		var log = LoggerFactory.getLogger(LibraryDownload.class);

		var index = getIndex();
		if (index == null || index.isEmpty()) {
			log.info("No libraries were found in the index file, thus, nothing has " +
				"been loaded.");
			return false;
		}

		var jarDirURL =  LibraryDownload.class.getResource("native");
		if (jarDirURL == null) {
			log.info("No binaries directory was found.");
			return false;
		}
		try {
			var jarDirPath = Path.of(jarDirURL.toURI()).toString();
			return NativeLib.load(index, jarDirPath);
		} catch (URISyntaxException e) {
			log.info("Failed to open the binaries' directory: {}", e.getMessage());
			return false;
		}
	}

	private static List<String> getIndex() {
		var log = LoggerFactory.getLogger(LibraryDownload.class);

		var indexURL = LibraryDownload.class.getResource("index.txt");
		if (indexURL == null) {
			log.warn("Failed to load the load index URL of the native library.");
			return List.of();
		} else {
			try {
				var indexPath = Paths.get(indexURL.toURI());
				return libs(indexPath);
			} catch (URISyntaxException e) {
				log.warn("Cannot parse the URL of the index file.");
				return List.of();
			}
		}
	}

	private static List<String> libs(Path indexPath) {
		var log = LoggerFactory.getLogger(NativeLib.class);
		if (indexPath == null || !indexPath.toFile().exists()) {
			log.warn("Failed to parse the index of the native library.");
			return List.of();
		}

		List<String> loadIndex;
		try {
			Charset charset = Charset.defaultCharset();
			loadIndex = Files.readAllLines(indexPath, charset);
		} catch (IOException e) {
			log.warn("Failed to load the index of the native library: {}",
				e.getMessage());
			return List.of();
		}
		return loadIndex;
	}

	public static boolean isLoaded() {
		var log = LoggerFactory.getLogger(LibraryDownload.class);
		return NativeLib.isLoaded();
	}
}
