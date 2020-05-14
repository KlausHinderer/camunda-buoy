package de.metaphisto.buoy.persistence;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public class SocketStoreHolder extends AbstractStoreHolder<SocketChannel> {
    private final SocketChannel socketChannel;

    SocketStoreHolder(String ankerPackageName, String host, int port) {
        super(ankerPackageName);
        if(host == null) {
            host = "localhost";
        }
        SocketAddress socketAddress = new InetSocketAddress(host, port);
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(true);
            socketChannel.connect(socketAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    SocketChannel getChannel() {
        return socketChannel;
    }


}
