package uk.co.ordnancesurvey.osmobilesdk.locations;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.observables.AndroidObservable;
import rx.functions.Action0;
import rx.functions.Func1;

public class LocationServiceImpl implements LocationService {

    private static final long DEFAULT_FREQUENCY = 1000 * 60 * 5;
    private static final float DEFAULT_LATITUDE = 50.937870f;
    private static final float DEFAULT_LONGITUDE = -1.470595f;

    private final Context mContext;
    private final LocationManager mLocationManager;
    private final IntentFilter mProviderFilter
            = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
    private final List<Pair<Subscriber<? super Location>, Long>> mUpdateSubscribers = Collections
            .synchronizedList(new ArrayList<Pair<Subscriber<? super Location>, Long>>());
    private final boolean mHasGps;

    private long mCurrentFrequency = DEFAULT_FREQUENCY;
    private GoogleLocationStrategy mGoogleLocationStrategy = new GoogleLocationStrategy();
    private Location mCachedLocation = getDefaultLocation();
    private boolean mCanEmitLocations = false;

    @Inject
    public LocationServiceImpl(Context context, LocationManager locationManager) {
        mContext = context;
        mLocationManager = locationManager;

        // CS:OFF - Line length
        /**
         * Note: this is not strong enough.  Kindle returns DummyLocationProvider
         * LocationProvider gpsProvider = mManager.getProvider(LocationManager.GPS_PROVIDER);
         * mHasGps = gpsProvider != null;
         * Source: http://stackoverflow.com/questions/7990267/android-check-gps-availability-on-device
         */
        // CS:ON
        mHasGps = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        mCanEmitLocations = canEmitLocations();
        Location lastKnownLocation = getLastKnownLocation();
        if (lastKnownLocation != null) {
            cache(lastKnownLocation);
        }
    }

    @Override
    public boolean canEmitLocations() {
        boolean gpsEnabled = isGpsEnabled();
        boolean networkEnabled = isNetworkEnabled();
        return gpsEnabled || networkEnabled;
    }

    @Override
    public Subscription subscribeToLocationChanges(final Subscriber<Location> subscriber,
                                                   long frequency) {
        mUpdateSubscribers
                .add(new Pair<Subscriber<? super Location>, Long>(subscriber, frequency));

        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (mGoogleLocationStrategy.isBetterLocation(location, mCachedLocation)) {
                    cache(location);
                    subscriber.onNext(location);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        return Observable
                .create(new Observable.OnSubscribe<Location>() {
                    @Override
                    public void call(Subscriber<? super Location> subscriber) {
                        mCurrentFrequency = getHighestRequestedFrequency();
                        adjustRequestFrequency(mCurrentFrequency, listener);
                    }
                }).doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        subscriber.onNext(mCachedLocation);
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (mUpdateSubscribers.size() == 0) {
                            mLocationManager.removeUpdates(listener);
                        } else {
                            mCurrentFrequency = getHighestRequestedFrequency();
                            adjustRequestFrequency(mCurrentFrequency, listener);
                        }
                    }
                })
                .subscribe(subscriber);
    }

    @Override
    public Subscription subscribeToServiceAvailabilityChanges(
            final Subscriber<Boolean> subscriber) {

        return AndroidObservable
                .fromBroadcast(mContext, mProviderFilter)
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        boolean canEmit = canEmitLocations();
                        subscriber.onNext(canEmit);
                    }
                })
                .map(new Func1<Intent, Boolean>() {
                    @Override
                    public Boolean call(Intent intent) {
                        return canEmitLocations();
                    }
                })
               .filter(new Func1<Boolean, Boolean>() {
                   @Override
                   public Boolean call(Boolean canEmitLocations) {
                       boolean allowThrough = canEmitLocations != mCanEmitLocations;
                       return allowThrough;
                   }
               })
                .map(new Func1<Boolean, Boolean>() {
                    @Override
                    public Boolean call(Boolean canEmitLocations) {
                        mCanEmitLocations = canEmitLocations;
                        return canEmitLocations;
                    }
                })
                .subscribe(subscriber);
    }

    @Override
    public void unsubscribeFromLocationChanges(Subscriber<Location> subscriber) {
        int index = -1;

        for (int i = 0; i < mUpdateSubscribers.size(); i++) {
            if (subscriber.equals(mUpdateSubscribers.get(i).first)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return;
        }

        if (mUpdateSubscribers.size() > 0) {
            mUpdateSubscribers.remove(index);
        }
        subscriber.unsubscribe();
    }

    private void adjustRequestFrequency(long frequency, LocationListener listener) {
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    frequency, 0, listener);
        }
        if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    frequency, 0, listener);
        }
    }

    private void cache(Location location) {
        if (location == null) {
            return;
        }
        mCachedLocation = location;
    }

    /**
     * @return the location of Ordnance Survey HQ - PO requirement
     */
    private Location getDefaultLocation() {
        Location location = new Location("OSMaps");
        location.setLatitude(DEFAULT_LATITUDE);
        location.setLongitude(DEFAULT_LONGITUDE);
        return location;
    }

    private long getHighestRequestedFrequency() {
        long frequency = DEFAULT_FREQUENCY;

        for (Pair<Subscriber<? super Location>, Long> pair : mUpdateSubscribers) {
            long listenerFreq = pair.second;

            if (listenerFreq < frequency) {
                frequency = listenerFreq;
            }
        }
        return frequency;
    }

    private Location getLastKnownLocation() {
        Location bestLocation = null;

        Location gpsLocation = mLocationManager
                .getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (gpsLocation != null
                && mGoogleLocationStrategy.isBetterLocation(gpsLocation, mCachedLocation)) {
            bestLocation = gpsLocation;
        }

        Location networkLocation = mLocationManager
                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (networkLocation != null
                && mGoogleLocationStrategy.isBetterLocation(networkLocation, mCachedLocation)) {
            bestLocation = networkLocation;
        }
        return bestLocation;
    }

    private boolean isGpsEnabled() {
        if (!mHasGps) {
            return false;
        }
        // TODO: determine the reliability of this check on Google devices - Some kindles use
        // TODO: a dummy provider for GPS...
        return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private boolean isNetworkEnabled() {
        return mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}
