package org.apache.flink.cdc.common.types.utils.runtime;

import org.apache.flink.core.memory.DataInputView;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** An input stream that draws its data from a {@link DataInputView}. */
public class DataInputViewStream extends InputStream {

    protected DataInputView inputView;

    public DataInputViewStream(DataInputView inputView) {
        this.inputView = inputView;
    }

    public DataInputView getInputView() {
        return inputView;
    }

    @Override
    public int read() throws IOException {
        try {
            return inputView.readUnsignedByte();
        } catch (EOFException ex) {
            return -1;
        }
    }

    @Override
    public long skip(long n) throws IOException {
        long toSkipRemaining = n;
        while (toSkipRemaining > Integer.MAX_VALUE) {
            int skippedBytes = inputView.skipBytes(Integer.MAX_VALUE);

            if (skippedBytes == 0) {
                return n - toSkipRemaining;
            }

            toSkipRemaining -= skippedBytes;
        }
        return n - (toSkipRemaining - inputView.skipBytes((int) toSkipRemaining));
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return inputView.read(b, off, len);
    }
}
