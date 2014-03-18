package me.bahadir.quill;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quill
 * User: Bahadir
 * Date: 15.03.2014
 * Time: 20:24
 */
public class QuillFactoryTest {

    @SuppressWarnings("UnusedDeclaration")
    private static org.apache.log4j.Logger log = Logger.getLogger(QuillFactoryTest.class);

    private File sampleTextFile;

    @Before
    public void setUp() throws Exception {
        URL r = QuillFactoryTest.class.getResource("/sample.txt");
        Assert.assertNotNull("Sample test file missing.", r);
        sampleTextFile = new File(r.getFile());

    }

    @org.junit.Test
    public void testStandart() throws Exception {

        final ConcurrentMap<Integer, AtomicInteger> words = new ConcurrentSkipListMap<Integer, AtomicInteger>();


        QuillFactory<String, String> factory = new QuillFactory<String, String>(200,20, new QuillFactory.IJob<String, String>() {
            @Override
            public void onBlockReceived(Iterator<String> iterator, BlockingQueue<String> output, String workerName) {
                String line;
                Integer tempLen;

                while(iterator.hasNext()) {

                    line = iterator.next();
                    StringTokenizer tokens = new StringTokenizer(line);
                    while(tokens.hasMoreTokens()) {
                        tempLen = tokens.nextToken().length();
                        words.putIfAbsent(tempLen, new AtomicInteger(0));
                        words.get(tempLen).incrementAndGet();

                    }


                }
            }
        });

        BufferedReader br = new BufferedReader(new FileReader(sampleTextFile));
        String line;
        while ((line = br.readLine()) != null) {
            factory.commit(line);
        }
        br.close();
        factory.close(); // wait until complete


        for(Integer wordlen : words.keySet()) {

            System.out.println(String.format("%d: %d", wordlen, words.get(wordlen).get()));
        }

    }
}
