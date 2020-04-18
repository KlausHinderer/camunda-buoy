package de.metaphisto.buoy.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileChannelHolder extends AbstractStoreHolder<FileChannel> {
    private FileChannel fileChannel;

    public FileChannelHolder(String filename) throws IOException {
        super(filename);
        this.fileChannel = FileChannel.open(Paths.get(filename), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }


    protected void rollover() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            LOG.error("FileChannel " + ankerPackageName + " cannot be closed", e);
        }
    }

    @Override
    FileChannel getChannel() {
        return fileChannel;
    }
}
