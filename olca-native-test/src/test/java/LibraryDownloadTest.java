import org.junit.Assert;
import org.junit.Test;

public class LibraryDownloadTest {

	/**
	 * This test is ignored as we only run it from time to time in order
	 * to check if our library downloads work. Before running the test,
	 * delete the `~./openLCA` folder. Otherwise, you may do not really
	 * test something here.
	 */
	@Test
	public void testFetchSparseLibs() {

			// first load the libraries from the jar
			Assert.assertTrue(LibraryDownload.loadNativeLib());
			Assert.assertTrue(LibraryDownload.isLoaded());
	}
}
