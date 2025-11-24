package __JAVA_PACKAGE__;

import java.io.Closeable;
import java.io.IOException;

public interface Provider extends Closeable {

    /**
     * Reads characters into an array
     */
    int read(char[] buffer, int offset, int length) throws IOException;
}