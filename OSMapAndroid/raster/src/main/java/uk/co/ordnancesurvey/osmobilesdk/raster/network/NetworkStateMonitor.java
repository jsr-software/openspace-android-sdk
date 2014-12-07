package uk.co.ordnancesurvey.osmobilesdk.raster.network;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import rx.Subscription;
import rx.android.observables.AndroidObservable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Class the observe network connectivity changes and record if network access is possible
 * TODO: add a subscriber to start?
 */
public class NetworkStateMonitor {

    private static final int[] NETWORK_TYPES = {
            ConnectivityManager.TYPE_ETHERNET,
            ConnectivityManager.TYPE_BLUETOOTH,
            ConnectivityManager.TYPE_WIMAX,
            ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_DUMMY
    };

    private final Context mContext;
    private final ConnectivityManager mManager;
    private Subscription mNetworkSubscription;

    private boolean mHasNetwork = true;

    public NetworkStateMonitor(Context context) {
        mContext = context;
        mManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mHasNetwork = getAccessState();
    }

    public boolean hasNetworkAccess() {
        return mHasNetwork;
    }

    public void start() {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mNetworkSubscription = AndroidObservable.fromBroadcast(mContext, filter)
                .map(new Func1<Intent, Boolean>() {
                    @Override
                    public Boolean call(Intent intent) {
                        return getAccessState();
                    }
                })
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean hasNetwork) {
                        mHasNetwork = hasNetwork;
                    }
                });
    }

    public void stop() {
        if(mNetworkSubscription != null && !mNetworkSubscription.isUnsubscribed()) {
            mNetworkSubscription.unsubscribe();
        }
        mNetworkSubscription = null;
    }

    private boolean getAccessState() {
        boolean reachable = false;

        for (int type : NETWORK_TYPES) {
            NetworkInfo nwInfo = mManager.getNetworkInfo(type);
            if (nwInfo != null && nwInfo.isConnectedOrConnecting()) {
                reachable = true;
                break;
            }
        }
        return reachable;
    }
}
