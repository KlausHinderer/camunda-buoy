package de.metaphisto.buoy.persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class RedisPersistenceTest {

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
    public void testAppendToKey() throws IOException {
        int outstandingBytes = 0;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(32768 * 2);
        RedisPersistence redisPersistence = new RedisPersistence();
        boolean locked = false;
        String key = "appendTo";

        long start = System.currentTimeMillis();
        locked = redisPersistence.beforeFirstWriteCommand(byteBuffer, key, locked);
        for (int i = 0; i < 10240000; i++) {
            locked = redisPersistence.appendNext("0123456789", key, byteBuffer, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
            outstandingBytes += 10;
        }
        locked = redisPersistence.afterLastWriteCommand(byteBuffer, key, locked);

        long end = System.currentTimeMillis();
        System.out.println("Writing " + outstandingBytes + " took " + (end - start) + " ms");

        byteBuffer.clear();
    }

    @Test
    public void testReadKey() throws IOException {
        writeThenReadKey(1024);
    }

    @Test
    public void testBufferWrap() throws IOException {
        //Uses different Buffer sizes to see if the append commands work
        for (int i = 0; i < 10; i++) {
            writeThenReadKey(2048 + i);
            System.out.println("Wrap for " + (2048 + i) + " OK");
        }
    }

    @Test
    public void testBuffersizePerformance() throws IOException {
        for (int i = 10; i <= 16; i++) {
            int bufferSize = 0x01 << i;
            long start = System.currentTimeMillis();
            writeThenReadKey(bufferSize);
            long end = System.currentTimeMillis();
            System.out.println(bufferSize + "\ttook \t" + (end - start));

        }
    }

    public void writeThenReadKey(int bufferSize) throws IOException {
        int outstandingBytes = 0;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
        RedisPersistence redisPersistence = new RedisPersistence();
        boolean locked = false;
        String key = "testReadKey" + bufferSize;


        locked = redisPersistence.beforeFirstWriteCommand(byteBuffer, key, locked);
        for (int i = 0; i < 10240; i++) {
            locked = redisPersistence.appendNext("0123456789", key, byteBuffer, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
            outstandingBytes += 10;
        }
        locked = redisPersistence.afterLastWriteCommand(byteBuffer, key, locked);
        redisPersistence.unlock();
        locked = false;
        byteBuffer.clear();
        ReadAction readAction = redisPersistence.prepareForRead(key, locked, byteBuffer);
        do {
            byte[] temp = new byte[byteBuffer.remaining()];
            byteBuffer.get(temp);
            outstandingBytes -= temp.length;
            //System.out.println(new String(temp));
        } while (redisPersistence.readNext(readAction, byteBuffer) >= 0);

        if(readAction.isLocked()){
            redisPersistence.unlock();
        }
        assertEquals("Written bytes +2 for cr lf must match read bytes", -2, outstandingBytes);
    }
}