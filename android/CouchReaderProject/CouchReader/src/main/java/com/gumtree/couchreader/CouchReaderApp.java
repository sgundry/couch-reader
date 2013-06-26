package com.gumtree.couchreader;

import android.app.Application;
import android.os.Bundle;
import android.util.Log;

public class CouchReaderApp extends Application {

    private static final java.lang.String TAG = "CouchReaderApp";
    private CouchContext  couchContext = null;

    public CouchContext getCouchContext() {
        return couchContext;
    }

    @Override
    public void onCreate() {
        super.onCreate(); /// @todo start before or after our stuff?

        couchContext = new CouchContext(this) {
            @Override
            protected void onConnected() {
                super.onConnected();
                Log.d(TAG, "Connected to Couch, notifying listeners");
            }
        };

        couchContext.start(); /// @todo Async call, handle for activity waiting for onConnected()
    }
};

