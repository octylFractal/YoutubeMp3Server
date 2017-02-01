package conversion;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHash {

    private static final int CHUNK_SIZE = 4096;

    public static String create(Path file) throws IOException {
        Hasher hasher = Hashing.sha256().newHasher();
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            while (stream.available() != -1) {
                int read = stream.read(buffer);
                if (read == -1) {
                    break;
                }

                hasher.putBytes(buffer, 0, read);
            }
        }
        return hasher.hash().toString();
    }

}
