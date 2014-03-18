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
        /* Correct results from Hadoop
        1       5212
        2       18571
        3       23515
        4       19787
        5       12280
        6       8927
        7       6938
        8       4785
        9       3136
        10      1901
        11      1100
        12      683
        13      358
        14      179
        15      84
        16      34
        17      16
        18      8
        19      5
        20      5
        21      4
        23      1
        24      1
        29      1
        30      1
        36      1
        */
        //Test some values
        Assert.assertEquals("Word length of 2 is incorrect",5212,  words.get(1).get());
        Assert.assertEquals("Word length of 2 is incorrect",18571, words.get(2).get());
        Assert.assertEquals("Word length of 3 is incorrect",23515, words.get(3).get());
        Assert.assertEquals("Word length of 4 is incorrect",19787, words.get(4).get());
        Assert.assertEquals("Word length of 3 is incorrect",12280, words.get(5).get());

    }
}
