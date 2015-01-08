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
package uk.co.ordnancesurvey.osmobilesdk.raster.app;


import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;

import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLMapRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSMap;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.LayerCatalog;

public final class MapView extends FrameLayout {
    private final GLMapRenderer mMapRenderer;
    private final OSMap mMap;

    public MapView(Context context, AttributeSet set) {
        super(context, set);
        GLMapRenderer map = init(context, null);
        mMapRenderer = map;
        mMap = map;

    }

    public MapView(Context context) {
        super(context);
        GLMapRenderer map = init(context, null);
        mMapRenderer = map;
        mMap = map;
    }

    public MapView(Context context, MapConfiguration mapConfiguration) {
        super(context);
        GLMapRenderer map = init(context, mapConfiguration);
        mMapRenderer = map;
        mMap = map;
    }

    private GLMapRenderer init(Context context, MapConfiguration mapConfiguration) {
        LayoutParams fill = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.FILL);

        GLMapRenderer map = new GLMapRenderer(context, mapConfiguration);
        map.setLayoutParams(fill);
        addView(map);

        if (mapConfiguration == null || mapConfiguration.getBasemap().getMapLayers() == null) {
            // If no map stack specified then use the default layers; these are available to Free and Pro
            // API key users.
            map.setMapLayers(LayerCatalog.getDefaultLayers());

        } else {
            // Otherwise use the map stack specified by the user
            map.setMapLayers(mapConfiguration.getBasemap().getMapLayers());
        }
        return map;
    }

    public OSMap getMap() {
        return mMap;
    }


    /**
     * Must be forwarded from the containing Activity/Fragment.
     * Saving/restoring instance state is not supported yet.
     * @param savedInstanceState saved bundle
     */
    public final void onCreate(Bundle savedInstanceState) {
    }

    /**
     * Must be forwarded from the containing Activity/Fragment.
     */
    public final void onDestroy() {
        mMapRenderer.onDestroy();
    }

    /**
     * Must be forwarded from the containing Activity/Fragment.
     */
    public final void onLowMemory() {
    }

    /**
     * Must be forwarded from the containing Activity/Fragment.
     */
    public final void onPause() {
        mMapRenderer.onPause();
    }

    /**
     * Must be forwarded from the containing Activity/Fragment.
     */
    public final void onResume() {
        mMapRenderer.onResume();
    }

    /**
     * Must be forwarded from the containing Activity/Fragment.
     * Saving/restoring instance state is not supported yet.
     * @param outState outgoing bundle
     */
    public final void onSaveInstanceState(Bundle outState) {
    }

    public void setMapConfiguration(MapConfiguration mapConfiguration) {
        mMap.setMapConfiguration(mapConfiguration);
    }
}
