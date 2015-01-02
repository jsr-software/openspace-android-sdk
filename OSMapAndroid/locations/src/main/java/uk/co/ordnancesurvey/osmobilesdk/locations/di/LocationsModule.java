package uk.co.ordnancesurvey.osmobilesdk.locations.di;

import android.content.Context;
import android.hardware.SensorManager;
import android.location.LocationManager;

import javax.inject.Singleton;

import dagger.Provides;
import uk.co.ordnancesurvey.osmobilesdk.locations.AzimuthService;
import uk.co.ordnancesurvey.osmobilesdk.locations.AzimuthServiceImpl;
import uk.co.ordnancesurvey.osmobilesdk.locations.LocationService;
import uk.co.ordnancesurvey.osmobilesdk.locations.LocationServiceImpl;

/**
 * Note that this library module requires a Context to be provided by the client Dagger module
 * to compile.
 */
@dagger.Module( library=true, complete = false)
public class LocationsModule {

    @Provides @Singleton
    LocationService provideLocationService(Context context) {
        return new LocationServiceImpl(context, provideLocationManager(context));
    }

    @Provides @Singleton
    AzimuthService provideAzimuthService(Context context) {
        return new AzimuthServiceImpl(context, provideSensorManager(context));
    }

    SensorManager provideSensorManager(Context context) {
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    LocationManager provideLocationManager(Context context) {
        return (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
}