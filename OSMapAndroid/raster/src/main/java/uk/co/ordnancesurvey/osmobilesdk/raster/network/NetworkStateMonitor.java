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
