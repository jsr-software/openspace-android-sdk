package uk.co.ordnancesurvey.osmobilesdk.di;

import android.content.Context;

import javax.inject.Singleton;

import dagger.Provides;
import uk.co.ordnancesurvey.osmobilesdk.MainActivity;
import uk.co.ordnancesurvey.osmobilesdk.locations.di.LocationsModule;

@dagger.Module(includes= LocationsModule.class, injects= MainActivity.class)
public class DemoAppModule {

    private final Context mApplicationContext;

    public DemoAppModule(Context applicationContext) {
        mApplicationContext = applicationContext;
    }

    @Provides @Singleton Context provideApplicationContext() {
        return mApplicationContext;
    }
}
