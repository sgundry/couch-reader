package com.gumtree.couchreader;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;

import org.ektorp.CouchDbConnector;
import org.ektorp.DbAccessException;
import org.ektorp.DbPath;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.impl.StdCouchDbInstance;

/// A class that manages a connection to (currently) just *one* database for a Couchbase-lite instance.
public class CouchContext {

    public static final java.lang.String TAG = "CouchContext";

    private static final String     DATABASE_URL    = "http://192.168.1.2:5984";
    private static final String     DATABASE_NAME   = "test";

    private Context                 context;
    private CBLServer               server;
    private CBLiteHttpClient        httpClient;
    private StdCouchDbInstance      dbInstance;
    private CouchDbConnector        dbConnector;

    private boolean                 connected = false;

    //static initaliser to ensure that CBL:// URLs are handled properly
    {
        CBLURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    public CouchContext(Context context) {
        this.context = context;
    }
    public void start() {
        startCBL(context.getFilesDir().getAbsolutePath());
        startEtkorp(); // Async, returns before connection is ready.
    }

    public boolean isConnected() {
        return connected;
    }

    public CouchDbConnector getConnector() {
        assert(dbConnector != null);
        return dbConnector;
    }

    public CBLDatabase getDatabase() {
        assert(server != null);
        return server.getDatabaseNamed(DATABASE_NAME);
    }

    protected void onConnected() {
        // do nothing
        connected = true;
    }

    private void startCBL(String filesDir) {
        try {
            server = new CBLServer(filesDir); // Use private app directory
        }
        catch(DbAccessException e) {
            Log.e(TAG, "Cannot connect to CouchDB-lite server, error: " + e.getMessage());
            e.printStackTrace();
        }
        catch(Exception e) {
            Log.e(TAG, "Unable to create CouchDB-lite server", e);
        }

        Log.d(TAG, "CouchDB-lite started, yay!" );
    }

    private void startEtkorp()
    {
        Log.v(TAG, "Starting ektorp");

        if(httpClient != null) {
            httpClient.shutdown();
        }

        httpClient  = new CBLiteHttpClient(server);
        dbInstance  = new StdCouchDbInstance(httpClient);

        Log.d(TAG, "All database names: " + TextUtils.join(",", dbInstance.getAllDatabases()));
        Log.d(TAG, String.format("Database %s exists? %b", "test", dbInstance.checkIfDbExists(new DbPath("test"))));

        CouchReaderEktorpAsyncTask startupTask = new CouchReaderEktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                dbConnector = dbInstance.createConnector(DATABASE_NAME, true);
                Log.d(TAG, "Connected to database " + dbConnector.getDatabaseName());
                assert(DATABASE_NAME.equals(dbConnector.getDatabaseName()));
            }

            @Override
            protected void onSuccess() {
                Log.d(TAG, "Starting replication");
                assert(dbConnector != null);

                startReplications();
                onConnected();
            }
        };

        startupTask.execute();
        try {
            startupTask.get();
        }
        catch(Exception e) {
            Log.e(TAG, "Startup task failed to complete");
        }
    }

    private void startReplications() {

        String databaseUrl = DATABASE_URL + "/" + DATABASE_NAME;

        final ReplicationCommand pushReplicationCommand = new ReplicationCommand.Builder()
                .source(DATABASE_NAME)
                .target(databaseUrl)
                .continuous(true)
                .build();

        CouchReaderEktorpAsyncTask pushReplication = new CouchReaderEktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                ReplicationStatus status = dbInstance.replicate(pushReplicationCommand);
                Log.d(TAG, "Push replication command executed: okay? " + status.isOk());
                if(!status.isOk())
                {
                    Log.e(TAG, "Status command failed: " + status.toString());
                }
            }
        };

        pushReplication.execute();

        final ReplicationCommand pullReplicationCommand = new ReplicationCommand.Builder()
                .source(databaseUrl)
                .target(DATABASE_NAME)
                .continuous(true)
                .build();

        CouchReaderEktorpAsyncTask pullReplication = new CouchReaderEktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                ReplicationStatus status = dbInstance.replicate(pullReplicationCommand);
                Log.d(TAG, "Pull replication command executed: okay? " + status.isOk());
                if(!status.isOk())
                {
                    Log.e(TAG, "Status command failed: " + status.toString());
                }
            }
        };

        pullReplication.execute();
    }
};