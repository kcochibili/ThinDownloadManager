package com.slim.downloadmanager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadRequestQueue {

	/** Number of network request dispatcher threads to start. */
    private static final int DEFAULT_DOWNLOAD_THREAD_POOL_SIZE = 1;

    /**
     * The set of all requests currently being processed by this RequestQueue. A Request
     * will be in this set if it is waiting in any queue or currently being processed by
     * any dispatcher.
     */
    private final Set<DownloadRequest> mCurrentRequests = new HashSet<DownloadRequest>();

    /** The queue of requests that are actually going out to the network. */
    private final PriorityBlockingQueue<DownloadRequest> mDownloadQueue =
        new PriorityBlockingQueue<DownloadRequest>();


	/** The download dispatchers */
	DownloadDispatcher[] mDownloadDispatchers;

	/** Used for generating monotonically-increasing sequence numbers for requests. */
    private AtomicInteger mSequenceGenerator = new AtomicInteger();

    /**
     * Creates the download dispatchers workers pool.
     */
	public DownloadRequestQueue() {
		mDownloadDispatchers = new DownloadDispatcher[DEFAULT_DOWNLOAD_THREAD_POOL_SIZE];
	}

    // Package-private methods
    /**
     * Generates a download id for the request and adds the download request to the
     * download request queue for the dispatchers pool to act on immediately.
     * @param request
     * @param listener
     * @return downloadId
     */
	 int add(DownloadRequest request) {
		int downloadId = getDownloadId();
        // Tag the request as belonging to this queue and add it to the set of current requests.
        request.setDownloadRequestQueue(this);

        synchronized (mCurrentRequests) {
            mCurrentRequests.add(request);
        }

        // Process requests in the order they are added.
        request.setDownloadId(downloadId);		
        mDownloadQueue.add(request);
        
		return downloadId;		
	}

    int query(int downloadId) {
        synchronized (mCurrentRequests) {
            for(DownloadRequest request: mCurrentRequests) {
                if(request.getDownloadId() == downloadId) {
                    return request.getDownloadState();
                }
            }
        }

        return DownloadManager.STATUS_NOT_FOUND;
    }

    void start() {
        stop();  // Make sure any currently running dispatchers are stopped.
    
        // Create download dispatchers (and corresponding threads) up to the pool size.
        for (int i = 0; i < mDownloadDispatchers.length; i++) {
            DownloadDispatcher networkDispatcher = new DownloadDispatcher(mDownloadQueue);
            mDownloadDispatchers[i] = networkDispatcher;
            networkDispatcher.start();
        }
    }

    /**
     * Stops download dispatchers.
     */
    void stop() {
		for (int i = 0; i < mDownloadDispatchers.length; i++) {
            if (mDownloadDispatchers[i] != null) {
            	mDownloadDispatchers[i].quit();
            }
        }
    }
    
    /**
     * Gets a sequence number.
     */
    int getDownloadId() {
        return mSequenceGenerator.incrementAndGet();
    }

    /**
     * Cancel all the dispatchers in work and also stops the dispatchers.
     */
    void cancelAll() {
        stop();
        //Remove from the queue.
        synchronized (mCurrentRequests) {
            mCurrentRequests.clear();
        }
    }

    /**
     * Cancel a particular download in progress.
     * Returns 1 if the download Id is found else returns 0.
     *
     * @param downloadId
     * @return int
     */
    int cancel(int downloadId) {
        synchronized (mCurrentRequests) {
            for(DownloadRequest request: mCurrentRequests) {
                if(request.getDownloadId() == downloadId) {
                    request.cancel();
                    return 1;
                }
            }
        }

        return 0;
    }

    void finish(DownloadRequest request) {
        System.out.println("####### DownloadRequest Queue finish ####### "+request.getDownloadId());
        //Remove from the queue.
        synchronized (mCurrentRequests) {
            mCurrentRequests.remove(request);
        }

        System.out.println("####### DownloadRequest Queue after finish ####### "+mCurrentRequests.size());
    }

}
