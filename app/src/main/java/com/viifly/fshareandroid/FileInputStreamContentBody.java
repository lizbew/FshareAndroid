package com.viifly.fshareandroid;

import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.content.AbstractContentBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.http.entity.ContentType;

// Reference org.apache.http.entity.mime.content.FileBody

public class FileInputStreamContentBody extends AbstractContentBody {
    private InputStream inputStream;
    private String fileName;
    private long fileSize;

    public FileInputStreamContentBody(InputStream inputStream, String fileName, long fileSize) {
        super(ContentType.DEFAULT_BINARY);
        this.inputStream = inputStream;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    @Override
    public String getFilename() {
        return this.fileName;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        try {
            final byte[] tmp = new byte[4096];
            int l;
            while ((l = this.inputStream.read(tmp)) != -1) {
                out.write(tmp, 0, l);
            }
            out.flush();
        } finally {
            // this.inputStream.close();
        }
    }

    @Override
    public String getTransferEncoding() {
        return MIME.ENC_BINARY;
    }

    @Override
    public long getContentLength() {
        return this.fileSize;
    }
}
