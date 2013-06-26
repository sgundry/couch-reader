package com.gumtree.couchreader;

import org.ektorp.DbAccessException;
import org.ektorp.android.util.EktorpAsyncTask;

import android.util.Log;

public abstract class CouchReaderEktorpAsyncTask extends EktorpAsyncTask {

    @Override
    protected void onDbAccessException(DbAccessException dbAccessException) {
        Log.e(MainActivity.TAG, "DbAccessException in background", dbAccessException);
    }

}