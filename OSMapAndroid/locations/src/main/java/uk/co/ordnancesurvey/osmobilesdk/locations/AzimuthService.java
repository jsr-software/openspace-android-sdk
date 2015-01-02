package uk.co.ordnancesurvey.osmobilesdk.locations;

import rx.Subscriber;

public interface AzimuthService {

    /**
     * Subscribe to azimuth changes through the given {@link rx.Subscriber}
     */
    void subscribeToAzimuthChanges(Subscriber<Float> subscriber);

    /**
     * Unsubscribe from azimuth changes for the given {@link rx.Subscriber}
     */
    void unsubscribeFromAzimuthChanges(Subscriber<Float> subscriber);
}
