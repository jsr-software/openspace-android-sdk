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

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Basemap;

public class MapConfiguration implements Parcelable {


    public static class Builder {

        private String mApiKey = "";
        private File mOfflineSource = null;
        private Basemap mBasemap = null;
        private boolean mIsPro = false;

        public MapConfiguration build() {
            return new MapConfiguration(this);
        }

        public Builder setOfflineTileSource(File offlineSource) {
            if (offlineSource == null || !offlineSource.exists()) {
                throw new IllegalArgumentException("Null or non-existent file");
            }
            mOfflineSource = offlineSource;
            return this;
        }

        public Builder setOnlineApiKey(String apiKey) {
            mApiKey = apiKey;
            return this;
        }

        public Builder setBaseMap(Basemap basemap) {
            mBasemap = basemap;
            return this;
        }

        public Builder setIsPro(boolean isPro) {
            mIsPro = isPro;
            return this;
        }
    }

    private final String mApiKey;
    private final File mOfflineSource;
    private final Basemap mBasemap;
    private final boolean mIsPro;

    private MapConfiguration(Builder builder) {
        mApiKey = builder.mApiKey;
        mOfflineSource = builder.mOfflineSource;
        mBasemap = builder.mBasemap;
        mIsPro = builder.mIsPro;
    }

    public String getApiKey() {
        return mApiKey;
    }

    public File getOfflineSource() {
        return mOfflineSource;
    }

    public Basemap getBasemap() {
        return mBasemap;
    }

    public boolean isPro() {
        return mIsPro;
    }

    /**
     * Parcelling
     */
    private MapConfiguration(Parcel parcel) {
        mApiKey = parcel.readString();
        mOfflineSource = (File) parcel.readSerializable();
        mBasemap = (Basemap) parcel.readSerializable();
        mIsPro = parcel.readByte() == 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mApiKey);
        dest.writeSerializable(mOfflineSource);
        dest.writeSerializable(mBasemap);
        dest.writeByte((byte) (mIsPro ? 1 : 0));
    }

    public static final Creator CREATOR = new Creator() {
        public MapConfiguration createFromParcel(Parcel in) {
            return new MapConfiguration(in);
        }

        public MapConfiguration[] newArray(int size) {
            return new MapConfiguration[size];
        }
    };
}
