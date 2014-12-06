package uk.co.ordnancesurvey.osmobilesdk.raster.tiles;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import uk.co.ordnancesurvey.osmobilesdk.raster.DBTileSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.FailedToLoadException;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSTileSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.WMSTileSource;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;

public class TileService {

    private static final String CLASS_TAG = TileService.class.getName();
    private static final String EMPTY_API_KEY = "";
    private static final String OSTILES = ".ostiles";

    private final String mPackageName;

    private List<OSTileSource> mSources = new ArrayList<>();

    public TileService(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("Null or Empty package name");
        }
        mPackageName = packageName;
    }

    public List<OSTileSource> getTileSourcesForConfiguration(MapConfiguration mapConfiguration) throws FailedToLoadException {
        if (mapConfiguration == null) {
            throw new IllegalStateException("Null MapConfiguration given");
        }

        // TODO: should these be unloaded first too?
        mSources.clear();

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

    public void unloadCurrentSources() {
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
}

