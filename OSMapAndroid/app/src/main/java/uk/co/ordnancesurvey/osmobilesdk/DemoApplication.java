package uk.co.ordnancesurvey.osmobilesdk;

import android.app.Application;

import java.util.Arrays;
import java.util.List;

import dagger.ObjectGraph;
import uk.co.ordnancesurvey.osmobilesdk.di.DemoAppModule;
import uk.co.ordnancesurvey.osmobilesdk.locations.di.LocationsModule;

public class DemoApplication extends Application {
    private ObjectGraph graph;

    @Override public void onCreate() {
        super.onCreate();

        graph = ObjectGraph.create(new DemoAppModule(this.getApplicationContext()));//().toArray());
    }

//    protected List<Object> getModules() {
//        return Arrays.asList(
//                new LocationsModule(this),
//                new DemoAppModule()
//        );
//    }

    public void inject(Object object) {
        graph.inject(object);
    }
}
