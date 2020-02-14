package de.metaphisto.buoy.persistence;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class DelegateExecutionSerializerTest {

    public static final String TEST_STRING = "TEST_STRING";

    @Mock
    private FileChannelHolder fileChannelHolder;

    @Mock
    private FileChannel fileChannel;

    private ByteBuffer byteBuffer = ByteBuffer.allocateDirect(TEST_STRING.length() + 1);

    @Test
    public void testSchreibe() throws IOException {
        Mockito.when(fileChannel.write(byteBuffer)).thenAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                byteBuffer.position(byteBuffer.limit());
                return null;
            }
        });
        Mockito.when(fileChannelHolder.getChannel()).thenReturn(fileChannel);
        AbstractStoreHolder.schreibeString(TEST_STRING, byteBuffer, fileChannelHolder, false, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        assertEquals(1, byteBuffer.remaining());
        Mockito.verify(fileChannel, Mockito.never()).write(byteBuffer);
        AbstractStoreHolder.schreibeString(TEST_STRING, byteBuffer, fileChannelHolder, false, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
        assertEquals(2, byteBuffer.remaining());
        Mockito.verify(fileChannel, Mockito.times(1)).write(byteBuffer);
    }

    @Test
    public void testMultiThread() throws IOException, InterruptedException {
        String fileName = "target/ankerMulti.out";
        Path filePath = Paths.get(fileName);
        int threads = 7;
        filePath.toFile().delete();
        FileChannelHolder ziel = new FileChannelHolder(fileName);
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        String zuSchreiben = "TEST_STRING123";
        for (int i = 0; i < threads; i++) {
            executorService.execute(new Thread() {
                @Override
                public void run() {
                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(32);
                    boolean locked = false;
                    long start = System.currentTimeMillis();
                    for (int i = 0; i < 2000; i++) {
                        try {
                            for (int j = 0; j < 100; j++) {
                                locked = locked | AbstractStoreHolder.schreibeString(zuSchreiben, byteBuffer, ziel, locked, AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL);
                            }
                            if (byteBuffer.position() > 0) {
                                byteBuffer.flip();
                                if (!locked) {
                                    ziel.lock();
                                    locked = true;
                                }
                                ziel.getChannel().write(byteBuffer);
                                byteBuffer.clear();
                            }
                            if (locked) {
                                ziel.unlock();
                                locked = false;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            Thread.sleep((long) (10 * Math.random()));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println((System.currentTimeMillis() - start) / 1000 + " s\t");
                    if (locked) {
                        ziel.unlock();
                    }
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);
        ziel.getChannel().close();
        FileReader fileReader = new FileReader(fileName);
        int writeCount = 0;
        char[] erwarteterInhalt = zuSchreiben.toCharArray();
        char[] inhalt = new char[erwarteterInhalt.length];
        while (fileReader.ready()) {
            assertEquals(erwarteterInhalt.length, fileReader.read(inhalt));
            assertArrayEquals(erwarteterInhalt, inhalt);
            writeCount++;
        }
        assertEquals(threads * 200000, writeCount);
    }
}