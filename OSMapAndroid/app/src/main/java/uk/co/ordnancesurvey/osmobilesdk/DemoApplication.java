package uk.co.ordnancesurvey.osmobilesdk;

import android.app.Application;

import dagger.ObjectGraph;
import uk.co.ordnancesurvey.osmobilesdk.di.DemoAppModule;

public class DemoApplication extends Application {
    private ObjectGraph graph;

    @Override public void onCreate() {
        super.onCreate();

        graph = ObjectGraph.create(new DemoAppModule(this.getApplicationContext()));
    }

    public void inject(Object object) {
        graph.inject(object);
    }
}
