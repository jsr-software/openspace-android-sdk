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
package uk.co.ordnancesurvey.osmobilesdk.raster;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;

public final class TileCache extends CombinedLruCache<MapTile> {

    private static final String CLASS_TAG = TileCache.class.getName();
    private static final String TILE_CACHE = "uk.co.ordnancesurvey.osmobilesdk.raster.TILE_CACHE";

    private static final int DISK_MB = 128;

    private static TileCache INSTANCE;

    private static int sMemoryMB;
    private static int sDiskMB;
    private static File sDir;
    private static int sAppVersion;

    private TileCache(int memoryMB, int diskMB, File dir, int appVersion) {
        super(memoryMB, diskMB, dir, appVersion);
    }

    @Override
    String stringForKey(MapTile key) {
        return key.layer.productCode + "_" + key.x + "_" + key.y;
    }

    public static synchronized TileCache newInstance(Context context) {

        final int memoryMB = getMemoryMB(context);
        final int appVersion = getAppVersion(context);
        final File cacheDir = new File(context.getCacheDir(), TILE_CACHE);

        if (isSameCacheInstance(memoryMB, appVersion, cacheDir)) {
            return INSTANCE;
        }

        INSTANCE = new TileCache(memoryMB, DISK_MB, cacheDir, appVersion);

        TileCache.sMemoryMB = memoryMB;
        TileCache.sDiskMB = DISK_MB;
        TileCache.sDir = cacheDir;
        TileCache.sAppVersion = appVersion;

        return INSTANCE;
    }

    public static int getMemoryMB(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = activityManager.getMemoryClass();
        return memoryClass / 2;
    }

    private static int getAppVersion(Context context) {
        final String packageName = context.getPackageName();
        int appVersion;
        try {
            appVersion = context.getPackageManager().getPackageInfo(packageName, 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(CLASS_TAG, "Failed to get package version for " + packageName, e);
            appVersion = 1;
        }
        return appVersion;
    }

    private static boolean isSameCacheInstance(int memoryMB, int appVersion, File cacheDir) {
        return INSTANCE != null &&
                memoryMB == TileCache.sMemoryMB &&
                DISK_MB == TileCache.sDiskMB &&
                appVersion == TileCache.sAppVersion &&
                cacheDir.equals(TileCache.sDir);
    }
}
