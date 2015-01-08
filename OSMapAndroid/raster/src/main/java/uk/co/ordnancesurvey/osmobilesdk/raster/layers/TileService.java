/**
 * OpenSpace Android SDK Licence Terms
 *
 * The OpenSpace Android SDK is protected by © Crown copyright – Ordnance Survey 2013.[https://github.com/OrdnanceSurvey]
 *
 * All rights reserved (subject to the BSD licence terms as follows):.
 *
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * Neither the name of Ordnance Survey nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 */
package uk.co.ordnancesurvey.osmobilesdk.raster.layers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.adapters.LayerSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.adapters.LocalLayerSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.adapters.RemoteLayerSource;
import uk.co.ordnancesurvey.osmobilesdk.utilities.network.NetworkStateMonitor;

public class TileService {

    private static final String CLASS_TAG = TileService.class.getName();
    private static final String EMPTY_API_KEY = "";
    private static final String OSTILES = ".ostiles";
    private static final int AVAILABLE_CORES = Runtime.getRuntime().availableProcessors();

    private final Context mContext;
    private final LinkedList<MapTile> mRequests = new LinkedList<>();
    private final List<Subscription> mCurrentSubscriptions = new ArrayList<>();
    private final TileCache mTileCache;
    private final TileServiceDelegate mTileServiceDelegate;
    private final NetworkStateMonitor mNetworkMonitor;
    private final FilenameFilter mFileFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(OSTILES);
        }
    };
    Observable<TileFetch> mFetchObservable =  Observable.create(new Observable.OnSubscribe<TileFetch>() {
        @Override
        public void call(Subscriber<? super TileFetch> subscriber) {
            TileFetch fetch = fetchTile();
            if (fetch != null) {
                subscriber.onNext(fetch);
            }
        }
    });

    private Set<LocalLayerSource> mLocalSources = new CopyOnWriteArraySet<>();
    private Set<RemoteLayerSource> mRemoteSources = new CopyOnWriteArraySet<>();

    private MapConfiguration mMapConfiguration;

    public TileService(Context context, NetworkStateMonitor networkStateMonitor,
                       TileServiceDelegate tileServiceDelegate) {
        if (context == null) {
            throw new IllegalArgumentException("Null Context");
        }
        mContext = context;

        mTileServiceDelegate = tileServiceDelegate;

        mTileCache = TileCache.getInstance(mContext);
        mNetworkMonitor = networkStateMonitor;
    }

    public void start(MapConfiguration mapConfiguration) throws FileNotFoundException {
        if (mapConfiguration == null) {
            throw new IllegalStateException("Null MapConfiguration given");
        }

        mMapConfiguration = mapConfiguration;

        unloadLocalSources();

        loadLocalSources();
        loadRemoteSources();

        mCurrentSubscriptions.clear();

        mNetworkMonitor.start();
    }

    public void shutdown() {
        for (Subscription subscription : mCurrentSubscriptions) {
            subscription.unsubscribe();
        }

        mCurrentSubscriptions.clear();
        mNetworkMonitor.stop();
    }

    public void resetTileRequests() {
        mRequests.clear();
    }

    public Bitmap requestBitmapForTile(MapTile mapTile) {
        Bitmap bitmap = getTileBitmapSync(mapTile);

        if (bitmap != null) {
            mRequests.remove(mapTile);
            return bitmap;
        }
        Log.d(CLASS_TAG, "Tile requested");

        Subscription subscription = requestTile(mapTile);
        mCurrentSubscriptions.add(subscription);

        return null;
    }

    private void loadLocalSources() throws FileNotFoundException {
        final File offlineSource = mMapConfiguration.getOfflineSource();

        if(offlineSource == null || !offlineSource.exists()) {
            return;
        }

        if(!mLocalSources.isEmpty()) {
            unloadLocalSources();
        }

        if (offlineSource.isDirectory()) {
            File[] files = offlineSource.listFiles(mFileFilter);
            if (files != null) {
                for (File file : files) {

                    mLocalSources.add(new LocalLayerSource(file));
                }
            }
        } else {
            mLocalSources.add(new LocalLayerSource(offlineSource));
        }
    }

    private void loadRemoteSources() {
        final String apiKey = mMapConfiguration.getApiKey();
        final String packageName = mContext.getPackageName();

        if (!apiKey.equals(EMPTY_API_KEY)) {
            mRemoteSources.add(new RemoteLayerSource(apiKey, packageName, mMapConfiguration.isPro(),
                    mMapConfiguration.getBasemap().getMapLayers()));
        }
    }

    private void unloadLocalSources() {
        for (LocalLayerSource source : mLocalSources) {
            try {
                source.close();
            } catch (IOException e) {
                Log.d(CLASS_TAG, "Tile unloading error", e);
            }
        }
    }

    private Subscription requestTile(MapTile mapTile) {
        mRequests.add(mapTile);
        return mFetchObservable
                .observeOn(Schedulers.newThread())
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<TileFetch>() {
                    @Override
                    public void onCompleted() {
                        Log.d(CLASS_TAG, "Completed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(CLASS_TAG, "Loading error", e);
                    }

                    @Override
                    public void onNext(TileFetch tileFetch) {
                        Log.d(CLASS_TAG, "Tile fetched");
                        mRequests.remove(tileFetch.mapTile);
                        if (mTileServiceDelegate != null) {
                            mTileServiceDelegate.tileReadyAsyncCallback(tileFetch.mapTile,
                                    tileFetch.bitmap);
                        }
                    }
                });
    }

    private TileFetch fetchTile() {
        // Pull an object off the stack, or wait till an object is added.
        // Wait until the queue is not empty.
        if (mRequests.isEmpty()) {
            Log.d(CLASS_TAG, "No Requests");
            return null;
        }

        TileFetch fetch = new TileFetch();
        MapTile tile = mRequests.get(0);

        fetch.mapTile = tile;
        fetch.bitmap = getTileBitmap(tile);
        return fetch;
    }

    private Bitmap getTileBitmap(MapTile mapTile) {
        Bitmap bitmap = getTileBitmapSync(mapTile);

        if (bitmap == null) {
            bitmap = getTileBitmapAsync(mapTile);
        }
        return bitmap;
    }

    private Bitmap getTileBitmapSync(MapTile mapTile) {
        byte[] data = mTileCache.get(mapTile);
        if (data != null) {
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return null;
    }

    private Bitmap getTileBitmapAsync(MapTile tile) {

        if(!mNetworkMonitor.hasNetworkAccess()) {
            return null;
        }

        for (LayerSource source : mRemoteSources) {
            // Don't try to fetch if the network is down.
            byte[] data = source.getTileData(tile);
            if (data == null) {
                continue;
            }
            mTileCache.putAsync(new MapTile(tile), data);
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        // TODO how are we handling errors?
        return null;
    }

    private static class TileFetch {
        MapTile mapTile;
        Bitmap bitmap;
    }
}
