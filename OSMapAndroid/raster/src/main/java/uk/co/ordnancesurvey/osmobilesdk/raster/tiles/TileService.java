package uk.co.ordnancesurvey.osmobilesdk.raster.tiles;

import android.content.Context;
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

    private final Context mContext;
    private final String mPackageName;

    private List<OSTileSource> mSources = new ArrayList<>();

    public TileService(Context context) {
        mContext = context;
        mPackageName = mContext.getPackageName();
    }

    public List<OSTileSource> getTileSourcesForConfiguration(MapConfiguration mapConfiguration) throws FailedToLoadException {
        if (mapConfiguration == null) {
            throw new IllegalStateException("Null MapConfiguration given");
        }

        // TODO: should these be unloaded first too?
        mSources.clear();

        final String apiKey = mapConfiguration.getApiKey();

        if(!apiKey.equals(EMPTY_API_KEY)){
            mSources.add(new WMSTileSource(apiKey, mPackageName,
                    mapConfiguration.isPro(), mapConfiguration.getDisplayedProducts()));
        }

        File offlineSource = mapConfiguration.getOfflineSource();

        if(offlineSource != null && offlineSource.exists()) {
            if(offlineSource.isDirectory()) {
                mSources.addAll(localTileSourcesInDirectory(mContext, offlineSource));
            } else {
                mSources.add(DBTileSource.openFile(mContext, offlineSource));
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

    private Collection<OSTileSource> localTileSourcesInDirectory(Context context, File dir) {
        ArrayList<OSTileSource> ret = new ArrayList<OSTileSource>();
        File[] files = dir.listFiles();
        if (files == null) {
            return ret;
        }
        for (File f : files) {
            if (!f.getName().endsWith(".ostiles")) {
                continue;
            }
            try {
                ret.add(DBTileSource.openFile(context, f));
            } catch (FailedToLoadException e) {
                Log.v(CLASS_TAG, "Failed to load " + f.getPath(), e);
            }
        }
        return ret;
    }
}

