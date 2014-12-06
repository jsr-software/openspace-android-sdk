package uk.co.ordnancesurvey.osmobilesdk.raster.tiles;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import uk.co.ordnancesurvey.osmobilesdk.raster.BuildConfig;
import uk.co.ordnancesurvey.osmobilesdk.raster.DBTileSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.FailedToLoadException;
import uk.co.ordnancesurvey.osmobilesdk.raster.MapTile;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSTileSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.TileCache;
import uk.co.ordnancesurvey.osmobilesdk.raster.TileServiceDelegate;
import uk.co.ordnancesurvey.osmobilesdk.raster.WMSTileSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;

/**
 * This class is NOT threadsafe. It is designed to be used from a single thread.
 * Do not call clear() or requestBitmapForTile() without first calling lock() and subsequently calling unlock()
 */
public class TileService {

    private static final String CLASS_TAG = TileService.class.getName();
    private static final String EMPTY_API_KEY = "";
    private static final String OSTILES = ".ostiles";

    private final Context mContext;
    private final String mPackageName;
    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mFull = mLock.newCondition();
    private final List<OSTileSource> mSources = new ArrayList<>();
    private final HashSet<MapTile> mRequests = new HashSet<>();
    private final LinkedList<MapTile> mFetches = new LinkedList<>();
    private final TileCache mTileCache;
    private final BroadcastReceiver mNetworkReceiver;
    private final TileFetchThread mAsynchronousFetchThreads[] = new TileFetchThread[]{
            new TileFetchThread(),
            new TileFetchThread(),
            //new TileFetchThread(),
            //new TileFetchThread(),
            //new TileFetchThread(),
    };

    private final TileServiceDelegate mTileServiceDelagate;

    private volatile OSTileSource[] mVolatileSynchronousSources = new OSTileSource[0];
    private volatile OSTileSource[] mVolatileAsynchronousSources = new OSTileSource[0];

    private boolean mStopThread = true;
    private boolean mNetworkReachable;

    public TileService(Context context, TileServiceDelegate tileServiceDelegate) {
        if (context == null) {
            throw new IllegalArgumentException("Null Context");
        }
        mContext = context;
        mPackageName = mContext.getPackageName();
        mTileServiceDelagate = tileServiceDelegate;

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = activityManager.getMemoryClass();
        int memoryMB = memoryClass / 2;
        int diskMB = 128;
        File cacheDir = new File(context.getCacheDir(), "uk.co.ordnancesurvey.android.maps.TILE_CACHE");
        int appVersion;
        try {
            appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(CLASS_TAG, "Failed to get package version for " + context.getPackageName(), e);
            assert !BuildConfig.DEBUG : "This shouldn't happen!";
            appVersion = 1;
        }

        mTileCache = TileCache.newInstance(memoryMB, diskMB, cacheDir, appVersion);

        mNetworkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onNetworkChange();
            }
        };
    }

    public void start(MapConfiguration mMapConfiguration) throws FailedToLoadException {
        List<OSTileSource> sources = getTileSourcesForConfiguration(mMapConfiguration);
        ArrayList<OSTileSource> synchronousSources = new ArrayList<>(sources.size());
        ArrayList<OSTileSource> asynchronousSources = new ArrayList<>(sources.size());
        for (OSTileSource source : sources) {
            boolean synchronous = source.isSynchronous();
            ArrayList<OSTileSource> sourceList = (synchronous ? synchronousSources : asynchronousSources);
            sourceList.add(source);
        }
        mVolatileSynchronousSources = synchronousSources.toArray(new OSTileSource[0]);
        mVolatileAsynchronousSources = asynchronousSources.toArray(new OSTileSource[0]);

        if (!mStopThread) {
            assert false : "Threads already started!";
            return;
        }

        joinAll();

        mStopThread = false;
        // TODO: Can threads be restarted like this?
        for (TileFetchThread t : mAsynchronousFetchThreads) {
            t.start();
        }

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mNetworkReceiver, filter);
    }

    public void shutDown(boolean waitForThreadsToStop) {
        if (mStopThread) {
            assert false : "Threads already stopped";
            if (waitForThreadsToStop) {
                joinAll();
            }
            return;
        }
        mStopThread = true;
        for (TileFetchThread t : mAsynchronousFetchThreads) {
            t.interrupt();
        }

        mContext.unregisterReceiver(mNetworkReceiver);
    }


    public Bitmap requestBitmapForTile(MapTile mapTile, boolean asyncFetchOK) {
        assert mLock.isHeldByCurrentThread();
        // Attempt a synchronous response.
        Bitmap bmp = bitmapForTile(mapTile, true);
        if (!asyncFetchOK || bmp != null || mTileServiceDelagate == null) {
            return bmp;
        }

        // Copy the tile!
        mapTile = new MapTile(mapTile);

        if (mRequests.add(mapTile)) {
            mFetches.add(mapTile);
            if (mFetches.size() == 1) {
                mFull.signal();
            }
        }
        return null;
    }

    public void finishRequest(MapTile mapTile) {
        mRequests.remove(mapTile);
    }

    public void lock() {
        assert !mLock.isHeldByCurrentThread() : "This lock shouldn't need to be recursive";
        mLock.lock();
    }

    public void clear() {
        assert mLock.isHeldByCurrentThread();
        if (mFetches.size() == mRequests.size()) {
            mRequests.clear();
        } else {
            mRequests.removeAll(mFetches);
        }
        mFetches.clear();
    }

    public void unlock() {
        assert mLock.isHeldByCurrentThread();
        mLock.unlock();
    }


    private Bitmap bitmapForTile(MapTile tile, boolean synchronous) {
        if (synchronous) {
            byte[] data = mTileCache.get(tile);
            if (data != null) {
                return BitmapFactory.decodeByteArray(data, 0, data.length);
            }
        }
        OSTileSource[] sources = synchronous ? mVolatileSynchronousSources : mVolatileAsynchronousSources;
        for (OSTileSource source : sources) {
            // Don't try to fetch if the network is down.
            if (source.isNetwork() && !mNetworkReachable) {
                continue;
            }
            byte[] data = source.dataForTile(tile);
            if (data == null) {
                continue;
            }
            mTileCache.putAsync(new MapTile(tile), data);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        // TODO how are we handling errors?
        return null;
    }

    private void joinAll() {
        assert mStopThread : "Threads should be stopped when we join.";

        boolean interrupted = false;
        for (TileFetchThread t : mAsynchronousFetchThreads) {
            // Loosely modeled after android.os.SystemClock.sleep():
            //   https://github.com/android/platform_frameworks_base/blob/android-4.2.2_r1/core/java/android/os/SystemClock.java#L108
            for (; ; ) {
                try {
                    t.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private List<OSTileSource> getTileSourcesForConfiguration(MapConfiguration mapConfiguration) throws FailedToLoadException {
        if (mapConfiguration == null) {
            throw new IllegalStateException("Null MapConfiguration given");
        }

        // TODO: should these be unloaded first too?
        unloadCurrentSources();

        final String apiKey = mapConfiguration.getApiKey();

        if (!apiKey.equals(EMPTY_API_KEY)) {
            mSources.add(new WMSTileSource(apiKey, mPackageName,
                    mapConfiguration.isPro(), mapConfiguration.getDisplayedProducts()));
        }

        File offlineSource = mapConfiguration.getOfflineSource();

        if (offlineSource != null && offlineSource.exists()) {
            if (offlineSource.isDirectory()) {
                mSources.addAll(localTileSourcesInDirectory(offlineSource));
            } else {
                mSources.add(DBTileSource.openFile(offlineSource));
            }
        }

        return mSources;
    }

    private void unloadCurrentSources() {
        if (mSources == null || mSources.isEmpty()) {
            Log.d(CLASS_TAG, "Nothing to unload");
            return;
        }

        for (OSTileSource source : mSources) {
            try {
                source.close();
            } catch (IOException e) {
                Log.d(CLASS_TAG, "Tile unloading error", e);
            }
        }

        mSources.clear();
    }

    private Collection<OSTileSource> localTileSourcesInDirectory(File tileSource) {
        ArrayList<OSTileSource> ret = new ArrayList<OSTileSource>();
        File[] files = tileSource.listFiles();
        if (files == null) {
            return ret;
        }
        for (File file : files) {
            if (isOSTile(file)) {
                try {
                    ret.add(DBTileSource.openFile(file));
                } catch (FailedToLoadException e) {
                    Log.v(CLASS_TAG, "Failed to load " + file.getPath(), e);
                }
            }
        }
        return ret;
    }

    private boolean isOSTile(File file) {
        return file.getName().endsWith(OSTILES);
    }

    private void onNetworkChange() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        // We don't care about network changes, but we do care about our general connected state - i.e do we have any kind of
        // network connection

        // getActiveNetworkInfo is the obvious call to use, but is apparently pretty buggy
        // http://code.google.com/p/android/issues/detail?id=11891
        // http://code.google.com/p/android/issues/detail?id=11866

        final int NETWORK_TYPES[] = getNetworkTypes();

        boolean reachable = false;
        for (int testType : NETWORK_TYPES) {
            NetworkInfo nwInfo = connectivityManager.getNetworkInfo(testType);
            if (nwInfo != null && nwInfo.isConnectedOrConnecting()) {
                reachable = true;
                break;
            }
        }

        boolean wasReachable = mNetworkReachable;
        mNetworkReachable = reachable;
        if (reachable && !wasReachable) {
            // We have a network. If this is newly available, we should pump any outstanding requests.
        }
    }

    private static int[] getNetworkTypes() {
        final int[] NETWORK_TYPES = {
                ConnectivityManager.TYPE_ETHERNET,
                ConnectivityManager.TYPE_BLUETOOTH,
                ConnectivityManager.TYPE_WIMAX,
                ConnectivityManager.TYPE_WIFI,
                ConnectivityManager.TYPE_MOBILE,
                ConnectivityManager.TYPE_DUMMY
        };
        return NETWORK_TYPES;
    }

    // A non-private function so we don't get TileFetcher.access$2 in Traceview.
    void threadFunc() {
        while (!mStopThread) {
            // Pull an object off the stack, or wait till an object is added.
            // Wait until the queue is not empty.
            MapTile tile = null;

            mLock.lock();
            try {
                while (tile == null) {
                    tile = mFetches.pollFirst();
                    if (tile == null) {
                        try {
                            mFull.await();
                        } catch (InterruptedException e) {
                            if (mStopThread) {
                                return;
                            }
                        }
                    }
                }
            } finally {
                mLock.unlock();
            }

            Bitmap bmp = bitmapForTile(tile, false);
            mTileServiceDelagate.tileReadyAsyncCallback(tile, bmp);
        }
    }

    static final AtomicLong sThreadNum = new AtomicLong();

    private class TileFetchThread extends Thread {
        public TileFetchThread() {
            // tchan: Do not lower the priority, or scrolling can make tiles load very slowly.
            //this.setPriority(Thread.MIN_PRIORITY);

            this.setName("TileFetchThread-" + sThreadNum.incrementAndGet());
        }

        @Override
        public void run() {
            threadFunc();
        }
    }
}

