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
package uk.co.ordnancesurvey.osmobilesdk.raster.layers.adapters;

import android.net.Uri;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import uk.co.ordnancesurvey.osmobilesdk.raster.MapTile;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Layer;

public class RemoteLayerSource implements LayerSource {

    private final static String CLASS_TAG = RemoteLayerSource.class.getSimpleName();

    private final Layer[] mLayers;
    private final String mApiKey;
    private final String mApiKeyPackageName;
    private final boolean mIsPro;

    public RemoteLayerSource(String apiKey, String apiKeyPackageName, boolean isPro,
                             Layer[] layers) {
        mLayers = layers;
        mApiKey = apiKey;
        mApiKeyPackageName = apiKeyPackageName;
        mIsPro = isPro;
    }

    @Override
    public byte[] getTileData(MapTile tile) {
        String uriString = uriStringForTile(tile);
        if (uriString == null) {
            return null;
        }

        try {
            URL url = new URL(uriString);
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            if (response.code() == HttpURLConnection.HTTP_OK) {
                return response.body().bytes();
            }
        } catch (MalformedURLException e) {
            throw new Error("Caught MalformedURLException where it should never happen", e);
        } catch (IOException e) {
            Log.e(CLASS_TAG, "Unable to load tile");
        }
        return null;
    }

    private String uriStringForTile(MapTile tile) {

        Layer layer = tile.layer;

        if (!isProductSupported(layer.getProductCode())) {
            return null;
        }

        float bboxX0 = layer.getTileSizeInMetres() * tile.x;
        float bboxY0 = layer.getTileSizeInMetres() * tile.y;
        float bboxX1 = bboxX0 + layer.getTileSizeInMetres();
        float bboxY1 = bboxY0 + layer.getTileSizeInMetres();

        String uriString = "https://" + (mIsPro ? "osopenspacepro" : "openspace") +
                ".ordnancesurvey.co.uk/osmapapi/ts" +
                "?FORMAT=image/png" +
                "&SERVICE=WMS" +
                "&VERSION=1.1.1" +
                "&EXCEPTIONS=application/vnd.ogc.se_inimage" +
                "&SRS=EPSG:27700" +
                "&STYLES=" +
                "&REQUEST=GetMap" +
                "&KEY=" + Uri.encode(mApiKey) +
                "&appId=" + Uri.encode(mApiKeyPackageName) +
                "&WIDTH=" + layer.getTileSizeInPixels() +
                "&HEIGHT=" + layer.getTileSizeInPixels() +
                "&BBOX=" + bboxX0 + "," + bboxY0 + "," + bboxX1 + "," + bboxY1 +
                "&LAYERS=" + Uri.encode(layer.getLayerCode()) +
                "&PRODUCT=" + Uri.encode(layer.getProductCode());

        return uriString;
    }

    boolean isProductSupported(String productCode) {
        if (mLayers == null) {
            return true;
        }

        for (int i = 0; i < mLayers.length; i++) {
            if (mLayers[i].getProductCode().equals(productCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Do not use this method; this only returns URIs for requests using the ZoomMap TileMatrixSet.
     */
//    @Override
//    String uriStringForTile(MapTile tile) {
//        Layer layer = tile.layer;
//        String productCode = layer.getProductCode();
//
//        if (!isProductSupported(productCode)) {
//            return null;
//        }
//
//        /**
//         * NB. this only works for ZoomMap TileMatrixSet
//         */
//        if (productCode.length() == 4 && productCode.startsWith("CS")) {
//            // TODO: Magic number
//            int mapHeight = Math.round(1344000 / layer.getTileSizeInMetres());
//            String wmtsCode = productCode.substring(2);
//
//            int tileRow = mapHeight - 1 - tile.y;
//            int tileCol = tile.x;
//
//            // Use Uri.encode() instead of URLEncoder.encode():
//            //   - It works for path elements (not just query keys/values).
//            //   - It doesn't make us catch UnsupportedEncodingException.
//            // TODO: handle non-pro API keys.
//            String uriString = "https://osopenspacepro.ordnancesurvey.co.uk/osmapapi/wmts/" +
//                    Uri.encode(mApiKey) +
//                    "/ts?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&LAYER=osgb&STYLE=default&FORMAT=image/png&TILEMATRIXSET=ZoomMap" +
//                    "&TILEMATRIX=" + Uri.encode(wmtsCode) +
//                    "&TILEROW=" + tileRow +
//                    "&TILECOL=" + tileCol +
//                    "&appId=" + Uri.encode(mApiKeyPackageName);
//
//            //Log.v(TAG, uriString);
//
//            return uriString;
//        }
//
//        return null;
//    }

}
