package conversion;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkPositionIndexes;

public abstract class ListeningOutputStream extends OutputStream {

    private final OutputStream delegate;

    protected ListeningOutputStream(OutputStream delegate) {
        this.delegate = delegate;
    }

    // write(byte[]) is forwarded to the following method

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        checkPositionIndexes(off, off + len, b.length);
        for (int i = off; i < len; i++) {
            onByte(b[i] & 0xFF);
        }
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void write(int b) throws IOException {
        onByte(b);
        delegate.write(b);
    }

    protected abstract void onByte(int b);

}
