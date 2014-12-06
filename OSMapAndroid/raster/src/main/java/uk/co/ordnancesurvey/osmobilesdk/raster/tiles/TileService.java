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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    private static final String TILE_CACHE = "uk.co.ordnancesurvey.osmobilesdk.raster.TILE_CACHE";

    private final Context mContext;
    private final FilenameFilter mFileFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(OSTILES);
        }
    };
    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mFull = mLock.newCondition();
    private final HashSet<MapTile> mRequests = new HashSet<>();
    private final LinkedList<MapTile> mFetches = new LinkedList<>();
    private final TileCache mTileCache;
    private final TileFetchThread mAsynchronousFetchThreads[] = new TileFetchThread[]{
            new TileFetchThread(),
            new TileFetchThread(),
            //new TileFetchThread(),
            //new TileFetchThread(),
            //new TileFetchThread(),
    };

    private final NetworkAccessMonitor mNetworkMonitor;

    private final TileServiceDelegate mTileServiceDelagate;

    private volatile OSTileSource[] mVolatileSynchronousSources = new OSTileSource[0];
    private volatile OSTileSource[] mVolatileAsynchronousSources = new OSTileSource[0];

    private MapConfiguration mMapConfiguration;

    private boolean mStopThread = true;

    public TileService(Context context, TileServiceDelegate tileServiceDelegate) {
        if (context == null) {
            throw new IllegalArgumentException("Null Context");
        }
        mContext = context;

        mTileServiceDelagate = tileServiceDelegate;
        mTileCache = createTileCache();

        mNetworkMonitor = new NetworkAccessMonitor(mContext);
    }

    public void start(MapConfiguration mapConfiguration) throws FailedToLoadException {
        if (mapConfiguration == null) {
            throw new IllegalStateException("Null MapConfiguration given");
        }

        mMapConfiguration = mapConfiguration;

        unloadSources(mVolatileSynchronousSources);
        unloadSources(mVolatileAsynchronousSources);

        mVolatileSynchronousSources = loadSyncTileSources();
        mVolatileAsynchronousSources = loadAsyncTileSources();

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

        mNetworkMonitor.start();
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

        mNetworkMonitor.stop();
    }

    public void resetTileRequests() {
        if (!mLock.isHeldByCurrentThread()) {
            mLock.lock();
        }

        if (mFetches.size() == mRequests.size()) {
            mRequests.clear();
        } else {
            mRequests.removeAll(mFetches);
        }
        mFetches.clear();
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
            if (source.isNetwork() && !mNetworkMonitor.hasNetworkAccess()) {
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

    /**
     * Tile Sources
     */
    private OSTileSource[] loadAsyncTileSources() {
        final String apiKey = mMapConfiguration.getApiKey();
        final String packageName = mContext.getPackageName();
        final List<OSTileSource> sources = new ArrayList<>();

        if (!apiKey.equals(EMPTY_API_KEY)) {
            sources.add(new WMSTileSource(apiKey, packageName, mMapConfiguration.isPro(),
                    mMapConfiguration.getDisplayedProducts()));
        }
        return sources.toArray(new OSTileSource[sources.size()]);
    }

    private OSTileSource[] loadSyncTileSources() throws FailedToLoadException {
        final File offlineSource = mMapConfiguration.getOfflineSource();
        final List<OSTileSource> sources = new ArrayList<>();

        if (offlineSource.isDirectory()) {
            File[] files = offlineSource.listFiles(mFileFilter);
            if (files != null) {
                for (File file : files) {
                    sources.add(DBTileSource.openFile(file));
                }
            }
        } else {
            sources.add(DBTileSource.openFile(offlineSource));
        }
        return sources.toArray(new OSTileSource[sources.size()]);
    }

    private void unloadSources(OSTileSource[] sources) {
        for (OSTileSource source : sources) {
            try {
                source.close();
            } catch (IOException e) {
                Log.d(CLASS_TAG, "Tile unloading error", e);
            }
        }
    }

    /**
     * Tile Cache
     */
    private TileCache createTileCache() {
        final String packageName = mContext.getPackageName();

        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = activityManager.getMemoryClass();
        int memoryMB = memoryClass / 2;
        int diskMB = 128;
        File cacheDir = new File(mContext.getCacheDir(), TILE_CACHE);
        int appVersion;
        try {
            appVersion = mContext.getPackageManager().getPackageInfo(packageName, 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(CLASS_TAG, "Failed to get package version for " + packageName, e);
            appVersion = 1;
        }

        return TileCache.newInstance(memoryMB, diskMB, cacheDir, appVersion);
    }


    /**
     * Threading for tile loading
     */
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

    /**
     * Network Access Monitor
     */
    private static class NetworkAccessMonitor {

        private static final int[] NETWORK_TYPES = {
                ConnectivityManager.TYPE_ETHERNET,
                ConnectivityManager.TYPE_BLUETOOTH,
                ConnectivityManager.TYPE_WIMAX,
                ConnectivityManager.TYPE_WIFI,
                ConnectivityManager.TYPE_MOBILE,
                ConnectivityManager.TYPE_DUMMY
        };

        private final Context mContext;
        private final ConnectivityManager mManager;
        private final IntentFilter mFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateAccessState();
            }
        };

        private boolean mHasNetwork = true;

        public NetworkAccessMonitor(Context context) {
            mContext = context;
            mManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            updateAccessState();
        }

        public boolean hasNetworkAccess() {
            return mHasNetwork;
        }

        public void start() {
            mContext.registerReceiver(mReceiver, mFilter);
        }

        public void stop() {
            mContext.unregisterReceiver(mReceiver);
        }

        private void updateAccessState() {
            boolean reachable = false;

            for (int type : NETWORK_TYPES) {
                NetworkInfo nwInfo = mManager.getNetworkInfo(type);
                if (nwInfo != null && nwInfo.isConnectedOrConnecting()) {
                    reachable = true;
                    break;
                }
            }

            boolean wasReachable = mHasNetwork;
            mHasNetwork = reachable;
            if (reachable && !wasReachable) {
                // TODO: We have a network. If this is newly available, we should pump any outstanding requests.
            }
        }
    }
}
