package de.metaphisto.buoy.persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SocketStoreHolderTest {

    private RedisServer redisServer;

    @Before
    public void setUp() throws IOException {
        redisServer = new RedisServer(6380);
        redisServer.start();
    }

    @After
    public void tearDown() {
        redisServer.stop();
    }

    @Test
    public void testRedisSocketStoreHolder() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(2048);
        SocketStoreHolder socketStoreHolder = new SocketStoreHolder("", null, 6380);

        byteBuffer.put("SET KEY VALUE1\n".getBytes(Charset.defaultCharset()));
        byteBuffer.flip();
        socketStoreHolder.getChannel().write(byteBuffer);

        byteBuffer.flip();
        int read;
        do {
            read = socketStoreHolder.getChannel().read(byteBuffer);
        } while (read <= 0);

        byteBuffer.flip();
        byte[] expected = "+OK\r\n".getBytes(Charset.defaultCharset());
        int expectedIndex = 0;
        while (byteBuffer.hasRemaining()) {
            assertEquals(expected[expectedIndex], byteBuffer.get());
            expectedIndex++;
        }
    }

    @Test
    public void testAbstractStoreHolderWrite() throws IOException {
        SocketStoreHolder socketStoreHolder = new SocketStoreHolder("", null, 6380);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(128);
        AbstractStoreHolder.schreibeString("SET KEY1 VALUE2\n", byteBuffer, socketStoreHolder, false, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
        int read;
        do {
            read = socketStoreHolder.getChannel().read(byteBuffer);
        } while (read <= 0);

        byteBuffer.flip();
        byte[] expected = "+OK\r\n".getBytes(Charset.defaultCharset());
        int expectedIndex = 0;
        while (byteBuffer.hasRemaining()) {
            assertEquals(expected[expectedIndex], byteBuffer.get());
            expectedIndex++;
        }

    }

    @Test
    public void testBufferOverlap() throws IOException {
        SocketStoreHolder socketStoreHolder = new SocketStoreHolder("", null, 6380);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(2);
        AbstractStoreHolder.schreibeString("SET KEY1 VALUE2\n", byteBuffer, socketStoreHolder, false, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
        int read;
        do {
            read = socketStoreHolder.getChannel().read(byteBuffer);
        } while (read <= 0);

        byteBuffer.flip();
        byte[] expected = "+OK\r\n".getBytes(Charset.defaultCharset());
        int expectedIndex = 0;
        while (byteBuffer.hasRemaining()) {
            assertEquals(expected[expectedIndex], byteBuffer.get());
            expectedIndex++;
        }
    }

    @Test
    public void testPerformance2M() throws IOException {
        int outstandingBytes = 0;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(2048);
        SocketStoreHolder socketStoreHolder = new SocketStoreHolder("", null, 6380);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1024; i++) {
            byteBuffer.clear();
            boolean locked = true;
            //the length of the value is not known, so APPEND is used instead of SET in RESP
            byteBuffer.put("APPEND KEY ".getBytes(Charset.defaultCharset()));
            for (int j = 0; j < 202; j++) {
                locked = AbstractStoreHolder.schreibeString("1234567890", byteBuffer, socketStoreHolder, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
                outstandingBytes += 10;
            }
            AbstractStoreHolder.schreibeString("\n", byteBuffer, socketStoreHolder, locked, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);
            int read;
            do {
                read = socketStoreHolder.getChannel().read(byteBuffer);
            } while (read <= 0);
        }

        byteBuffer.clear();
        //*2 für Key + Value, $3 für GET.length, $3 für KEY.length
        AbstractStoreHolder.schreibeString("*2\r\n$3\r\nGET\r\n$3\r\nKEY\r\n", byteBuffer, socketStoreHolder, true, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);

        assertEquals(1024 * 202 * 10, outstandingBytes);
        //First part of response is the length
        outstandingBytes += "$2068480".length();
        int read;
        do {
            read = socketStoreHolder.getChannel().read(byteBuffer);
            byteBuffer.flip();
            byte[] readBytes = new byte[byteBuffer.limit()];
            for (int i = 0; i < byteBuffer.limit(); i++) {
                byte singleByte = byteBuffer.get();
                readBytes[i] = singleByte;
                if (singleByte > "\n".getBytes()[0] && singleByte > "\r".getBytes()[0]) {
                    outstandingBytes--;
                }
            }
            //         System.out.println(new String(readBytes));
            byteBuffer.clear();
        } while (read > 4);

        System.out.println(System.currentTimeMillis() - start);
        assertEquals(0, outstandingBytes);

    }
}

