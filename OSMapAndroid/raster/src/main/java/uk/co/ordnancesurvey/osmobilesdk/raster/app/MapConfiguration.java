package uk.co.ordnancesurvey.osmobilesdk.raster.app;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public class MapConfiguration implements Parcelable {

    public static class Builder {

        private String mApiKey = "";
        private File mOfflineSource = null;
        private String[] mProducts = null;
        private boolean mIsPro = false;

        public MapConfiguration build() {
            return new MapConfiguration(this);
        }

        public Builder setOfflineTileSource(File offlineSource) {
            mOfflineSource = offlineSource;
            return this;
        }

        public Builder setOnlineApiKey(String apiKey) {
            mApiKey = apiKey;
            return this;
        }

        public Builder setDisplayedProducts(String[] products) {
            mProducts = products;
            return this;
        }

        public Builder setIsPro(boolean isPro) {
            mIsPro = isPro;
            return this;
        }
    }

    private final String mApiKey;
    private final File mOfflineSource;
    private String[] mProducts;
    private final boolean mIsPro;

    private MapConfiguration(Builder builder) {
        mApiKey = builder.mApiKey;
        mOfflineSource = builder.mOfflineSource;
        mProducts = builder.mProducts;
        mIsPro = builder.mIsPro;
    }

    public String getApiKey() {
        return mApiKey;
    }

    public String[] getDisplayedProducts() {
        return mProducts;
    }

    public File getOfflineSource() {
        return mOfflineSource;
    }

    public boolean isPro() {
        return mIsPro;
    }

    /**
     * Parcelling
     */
    private MapConfiguration(Parcel parcel){
        mApiKey = parcel.readString();
        mOfflineSource = (File) parcel.readSerializable();
        parcel.readStringArray(mProducts);
        mIsPro = parcel.readByte() == 0;
    }

    @Override
    public int describeContents(){
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mApiKey);
        dest.writeSerializable(mOfflineSource);
        dest.writeStringArray(mProducts);
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
