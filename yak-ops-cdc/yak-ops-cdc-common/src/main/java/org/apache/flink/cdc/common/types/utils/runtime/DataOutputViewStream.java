package org.apache.flink.cdc.common.types.utils.runtime;

import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;
import java.io.OutputStream;

/** An output stream that writes its data to a {@link DataOutputView}. */
public class DataOutputViewStream extends OutputStream {
    protected DataOutputView outputView;

    public DataOutputViewStream(DataOutputView outputView) {
        this.outputView = outputView;
    }

    @Override
    public void write(int b) throws IOException {
        outputView.writeByte(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        outputView.write(b, off, len);
    }
}
