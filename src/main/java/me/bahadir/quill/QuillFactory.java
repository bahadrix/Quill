package me.bahadir.quill;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates threads and controls them.
 * @param <T0> Input type
 * @param <T1> Output type
 */
@SuppressWarnings("UnusedDeclaration")
public class QuillFactory<T0, T1> {
    private static org.apache.log4j.Logger log = Logger.getLogger(QuillFactory.class);

    /**
     * Thread work implementation.
     * @param <T2> Input type
     * @param <T3> Output type
     */
    static interface IJob<T2,T3> {
        /**
         * Fires when the block received by worker.
         * @param iterator Iterator of block items
         * @param output Output collection for results
         * @param workerName Name of the worker
         */
        public void onBlockReceived(Iterator<T2> iterator, BlockingQueue<T3> output, String workerName);
    }

    private final BlockingQueue<T1> outputQueue;
    private final IJob<T0, T1> job;

    private int blockSize;
    private BlockingQueue<T0> currentBlock;
    private final int workerCount;

    private final Queue<BlockingQueue<T0>> queue;

    private int cntrBlocksQueued;
    private int cntrItemsPackaged;
    private boolean closed;
    private List<Thread> threads;
    private List<Worker> workers;

    class Worker implements Runnable {
        private final Queue<BlockingQueue<T0>> queue;
        public final String name;
        private BlockingQueue<T0> block;
        private int cntrWorkComplete;
        private long itemsBurned;
        private final QuillFactory factory;
        private boolean atWork;
        protected Worker(QuillFactory factory, Queue<BlockingQueue<T0>> queue, String name) {
            this.factory = factory;
            this.queue = queue;
            this.name = name;
            this.cntrWorkComplete = 0;
            this.itemsBurned = 0;
            this.atWork = false;
        }

        public int getCntrWorkComplete() {
            return cntrWorkComplete;
        }

        public long getItemsBurned() {
            return itemsBurned;
        }

        @Override
        public void run() {
            long nullCount = 0;

            while(true){
                block = null;
                synchronized (QuillFactory.this) {

                    if(queue.isEmpty() && factory.isClosed()) {
                        break;
                    } else if(!queue.isEmpty()) {
                        block = queue.poll();
                    }
                }
                if(block != null) {
                    job.onBlockReceived(block.iterator(), outputQueue, name);
                    cntrWorkComplete++;
                    itemsBurned += block.size();
                } else {
                    nullCount++;
                }
            }

            log.info("Worker " + name + " gone home with " + nullCount + " empty cycles.");

        }

        public Thread getThread() {
            return Thread.currentThread();
        }

        public void end() {
            //Thread.currentThread().interrupt();
            log.info("Worker " + name + " ended.");
        }

    }

    /**
     * Create factory with specified configuration.
     * Factory automatically starts when the first block is passed.
     * Blocks passed when they reach their capacity. If block hasn't reached
     * capacity, it will be passed when the factory closed.
     *
     * @param blockSize Item's count in each block
     * @param workerCount Worker count.
     * @param job Job implementation of workers.
     */
    public QuillFactory(int blockSize, int workerCount, IJob<T0, T1> job) {
        this.job = job;
        this.blockSize = blockSize;
        this.workerCount = workerCount;
        this.queue = new LinkedBlockingQueue<BlockingQueue<T0>>(1024);
        this.threads = new ArrayList<Thread>();
        this.workers = new ArrayList<Worker>();
        this.outputQueue = new LinkedBlockingQueue<T1>(1024);
        this.closed = true;
        resetCounters();
        resetCurrentBlock();
        createThreads();
    }

    private void start() {
        startAllThread();
        setClosed(false);
    }

    private void createThreads() {
        log.info("Creating threads.");
        for (int i = 0; i < workerCount; i++) {
            Worker worker =  new Worker(this, this.queue, String.valueOf(i + 100));
            workers.add(worker);
            threads.add(new Thread(worker));
        }

    }

    private void startAllThread() {
        log.info("Starting all threads");
        setClosed(false);
        for(Thread t : threads) {
            t.start();
        }


    }

    public void resetCounters() {
        this.cntrBlocksQueued = 0;
        this.cntrItemsPackaged = 0;
    }

    private void resetCurrentBlock() {
        this.currentBlock = new ArrayBlockingQueue<T0>(blockSize);
    }

    /**
     * Commit item to the factory.
     * @param item Item to be processed.
     */
    public void commit(T0 item) {

        currentBlock.add(item);
        cntrItemsPackaged++;
        if(currentBlock.size() == blockSize) {
            //bfListener.onBlockReady(currentBlock);
            commitCurrentBlock();
        }
    }

    private void commitCurrentBlock() {

        queue.add(currentBlock);
        resetCurrentBlock();
        cntrBlocksQueued++;
        if(isClosed())
            start();
    }

    /**
     * If factory is closed or in closing phase this method returns true,
     * otherwise returns false.
     * @return Closing state of factory.
     */
    public boolean isClosed() {
        return closed;
    }

    private void stopAllThreads() {
        for(Thread t : threads) {
            t.interrupt();
        }
    }

    /**
     * Returns true if any worker of this factory is awake.
     * @return Running stat of factory.
     */
    public boolean isRunning() {
        for(Thread t : threads) {
            if(!t.isInterrupted()) {
                return true;
            }
        }
        return false;
    }

    private void setClosed(boolean closed) {
        this.closed = closed;
    }

    private void joinAll() {
        for(Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                log.warn(e);
            }
        }
    }

    /**
     * Closes the factory.
     * If any block is waiting to filling it's capacity, it immediately goes into
     * process queue.
     * This method waits until all blocks burned and must be called once job.
     *
     */
    public void close() {

        if(isClosed()) {
            log.warn("Already closed. Start the factory first!");
            return;
        }

        // If there is block send it
        if(currentBlock.size() > 0)
            commitCurrentBlock();

        // Make sure that queue is empty
        while(!queue.isEmpty()) {}

        setClosed(true);

        joinAll();

        int totalBlocksBurned = 0;
        int totalItemsBurned = 0;
        String workerStats = "";
        for(Worker worker : workers) {
            workerStats += String.format(
                    "\n\tWorker %s: Blocks burned %d, Items burned: %d",
                    worker.name, worker.getCntrWorkComplete(), worker.getItemsBurned()
            );
            totalBlocksBurned += worker.getCntrWorkComplete();
            totalItemsBurned += worker.getItemsBurned();
        }

        if(totalBlocksBurned != cntrBlocksQueued) {
            log.error(String.format("Not all blocks burned! %d/%d", totalBlocksBurned, cntrBlocksQueued));
            return;
        } else if (totalItemsBurned != cntrItemsPackaged) {
            log.error("Not all items burned!");
            return;
        }

        log.info("Factory closing report:" +
                "\n--------------------------------" +
                "\nJOB Finished" +
                "\n---------------------------------" +
                String.format("\nBlocks burned/queued: %d \t\t%.1f%%", cntrBlocksQueued, 100*(float)totalBlocksBurned/cntrBlocksQueued) +
                String.format("\nItems burned/packaged: %d \t%.1f%%", cntrItemsPackaged, 100*(float)totalItemsBurned/cntrItemsPackaged) +
                "\nWorkers:" +
                workerStats +
                "\n"

        );
        resetCounters();
    }

    /**
     * Elements of map output collection.
     * @return Output's of map methods.
     */
    public BlockingQueue<T1> getOutput() {
        if(!isClosed())
            log.warn("Factory is still open, output is not completed!");
        return outputQueue;
    }
}
