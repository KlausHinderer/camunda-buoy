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
        locked = redisPersistence.prepareForRead(key, locked, byteBuffer);
        do {
            byte[] temp = new byte[byteBuffer.remaining()];
            byteBuffer.get(temp);
            outstandingBytes -= temp.length;
            //System.out.println(new String(temp));
        } while (redisPersistence.readNext(key, byteBuffer) >= 0);

        assertEquals("Written bytes +2 for cr lf must match read bytes", -2, outstandingBytes);
    }


    /*
    @Test
    public void testAppendToKey() throws IOException {
        int outstandingBytes =0;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);
        RedisPersistence redisPersistence = new RedisPersistence();
        boolean locked = false;
        String key = "appendTo";

        long start = System.currentTimeMillis();
        locked = redisPersistence.beforeFirstWriteCommand(byteBuffer, key, locked );
        for (int i = 0; i < 1024; i++) {
            locked = redisPersistence.appendNext("0123456789", key, byteBuffer, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
            outstandingBytes +=10;
        }

        byteBuffer.clear();
        //*2 für Key + Value, $3 für GET.length, $3 für KEY.length
        AbstractStoreHolder.schreibeString("*2\r\n$3\r\nGET\r\n$"+key.length()+"\r\n"+key+"\r\n",byteBuffer, redisPersistence, true, AbstractStoreHolder.WriteMode.FORCE_FLUSH_BUFFER_TO_CHANNEL);

        assertEquals(1024*10, outstandingBytes);
        //First part of response is the length
        outstandingBytes += ("$"+(1024*10)).length();
        int read;
        int responseLength = Integer.MAX_VALUE;
        boolean responseLengthInitialized = false;
        String responseLengthString = null;
        //TODO Länge lesen, dann als Abbruchbedingung für while
        do {
            read = redisPersistence.getChannel().read(byteBuffer);
            byteBuffer.flip();
            byte[] readBytes = new byte[byteBuffer.limit()];
            for (int i = 0; i < byteBuffer.limit(); i++) {
                if(!responseLengthInitialized){
                    byte b = byteBuffer.get();
                    if(b == ":".getBytes()[0]) {
                        responseLengthString = "";
                    }else if(b == "\n".getBytes()[0]) {
                        responseLength = Integer.valueOf(responseLengthString);
                        responseLengthInitialized = true;
                    }else if(b == "\r".getBytes()[0]) {
                    }else {
                        responseLengthString += new String(new byte[]{b});
                    }
                } else {
                    byte singleByte = byteBuffer.get();
                    readBytes[i] = singleByte;
                    if (singleByte > "\n".getBytes()[0] && singleByte > "\r".getBytes()[0]) {
                        outstandingBytes--;
                    }
                }
            }
            System.out.println(new String(readBytes));
            byteBuffer.clear();
        }while (read < responseLength);

        System.out.println(System.currentTimeMillis()-start);
        assertEquals(0,outstandingBytes);

    }
*/
}