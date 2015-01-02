package uk.co.ordnancesurvey.osmobilesdk.locations;

import android.location.Location;

import rx.Subscriber;
import rx.Subscription;

public interface LocationService {

    /**
     * @return true is the service can emit locations (either GPS or Network)
     */
    boolean canEmitLocations();

    /**
     * @return a {@link rx.Subscription} to receive location changes
     */
    Subscription subscribeToLocationChanges(Subscriber<Location> subscriber, long frequency);

    /**
     * @return a {@link rx.Subscription} to receive location service availability changes
     */
    Subscription subscribeToServiceAvailabilityChanges(Subscriber<Boolean> subscriber);

    /**
     * Unsubscribes the given {@link .Subscriber}
     */
    void unsubscribeFromLocationChanges(Subscriber<Location> subscriber);
}
