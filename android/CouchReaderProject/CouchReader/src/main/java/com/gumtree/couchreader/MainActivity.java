package com.gumtree.couchreader;

import android.app.ListActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewMapEmitBlock;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;
import com.google.gson.Gson;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;

import org.codehaus.jackson.JsonNode;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DbPath;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.android.util.CouchbaseViewListAdapter;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;
import org.ektorp.DbAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Doc {

    public static String getValue(JsonNode json, String key) {
        return getStringValue(json, key);
    }

    public static String getValue(JsonNode json, String key, String defaultValue) {
        return getStringValue(json, key, defaultValue);
    }

    public static String getStringValue(JsonNode json, String key) {
        return getStringValue(json, key, "");
    }

    public static String getStringValue(JsonNode json, String key, String defaultValue) {
        JsonNode item = json.get(key);
        if(item != null) {
            return item.getTextValue();
        }
        else {
            return defaultValue;
        }
    }

    public static int getIntValue(JsonNode json, String key){
        return getIntValue(json, key, 0);
    }

    public static int getIntValue(JsonNode json, String key, int defaultValue) {
        JsonNode item = json.get(key);
        if(item != null) {
            return item.getIntValue();
        }
        else {
            return defaultValue;
        }
    }

    public static boolean getBooleanValue(JsonNode json, String key) {
        return getBooleanValue(json, key, false);
    }

    public static boolean getBooleanValue(JsonNode json, String key, boolean defaultValue) {
        JsonNode item = json.get(key);
        if(item != null) {
            return item.getBooleanValue();
        }
        else {
            return defaultValue;
        }
    }
};

class Feed {
    public int id;
    public String title;
    public int num_unread = 0;
    public boolean is_folder = false;

    public Feed(String title, int num_unread, boolean is_folder)
    {
        this.title = title;
        this.num_unread = num_unread;
        this.is_folder = is_folder;
    }

    public Feed(JsonNode doc) {
        this.title      = Doc.getStringValue(doc, "title", "");
        this.num_unread = Doc.getIntValue(doc, "num_unread", 0);
        this.is_folder  = Doc.getStringValue(doc, "type").equals("folder");
    }

    public boolean isFolder() {
        return this.is_folder;
    }

    public boolean hasUnread() {
        return this.num_unread > 0;
    }
};

class FeedListAdapter extends CouchbaseViewListAdapter {

    protected MainActivity parent;

    public FeedListAdapter(MainActivity parent, CouchDbConnector couchDbConnector, ViewQuery viewQuery, boolean followChanges) {
        super(couchDbConnector, viewQuery, followChanges);
        this.parent = parent;
    }

    private static class ViewHolder {
        ImageView feed_icon;
        TextView feed_title;
        TextView feed_num_unread;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = convertView;

        if(v == null) {
            LayoutInflater vi =
                    (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            v = vi.inflate(R.layout.list_item_feed, null);
            ViewHolder vh  = new ViewHolder();

            vh.feed_icon        = (ImageView) v.findViewById(R.id.feed_icon);
            vh.feed_title       = (TextView)  v.findViewById(R.id.feed_title);
            vh.feed_num_unread  = (TextView)  v.findViewById(R.id.feed_num_unread);

            v.setTag(vh);
        }

        ViewResult.Row row  = getRow(position);
        ViewHolder vh       = (ViewHolder)v.getTag();
        Feed feed           = new Feed(row.getValueAsNode());

        vh.feed_icon.setImageResource(feed.isFolder() ? R.drawable.folder : R.drawable.rss);
        vh.feed_title.setText(feed.title);
        vh.feed_num_unread.setText(feed.num_unread > 0 ? String.format("%d", feed.num_unread) : "");

        return v;
    }
};

public class MainActivity extends ListActivity {

    public static final java.lang.String TAG = "MainActivity";

    private CBLServer           server;
    private CBLiteHttpClient    httpClient;
    private StdCouchDbInstance  dbInstance;
    private CouchDbConnector    dbConnector;
    private CBLDatabase         db;

    private static final String     DATABASE_URL    = "http://192.168.1.2:5984";
    private static final String     DATABASE_NAME   = "test";
    public static final String      dFeedsDocName   = "feeds";
    public static final String      dFeedsDocId      = "_design/" + dFeedsDocName;
    public static final String      byIdViewName    = "by_id";

    protected ReplicationCommand pushReplicationCommand;
    protected ReplicationCommand pullReplicationCommand;

    //static initaliser to ensure that CBL:// URLs are handled properly
    {
        CBLURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.util.Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_feeds);

        startCBL();
        startEtkorp();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
       // super.onListItemClick(l, v, position, id);

        ViewResult.Row row = ((FeedListAdapter)this.getListAdapter()).getRow(position);
        Feed feed = new Feed(row.getValueAsNode());

        Log.d(TAG, "Doc: " + row.getValueAsNode().toString());
        Log.d(TAG,  String.format("Feed clicked: title=%s folder=%b", feed.title, feed.isFolder()));

        if(feed.isFolder()) {
            // start new FeedListActivity (or reset this one?) with this folder as root
        }
        else {
            // start EntryListActivity corresponding to this feed
        }
    }

    protected void startCBL() {
        String filesDir = getFilesDir().getAbsolutePath();

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

        // Install all view definitions needed by the application
        CBLDatabase db = server.getDatabaseNamed(DATABASE_NAME);

        // feeds/_view/by_title
        {
            CBLView view = db.getViewNamed(String.format("%s/%s", dFeedsDocName, byIdViewName));
            view.setMapReduceBlocks(new CBLViewMapBlock() {

                @Override
                public void map(Map<String, Object> doc, CBLViewMapEmitBlock emitter) {
                    Object obj = doc.get("type");
                    if(obj != null) {
                        //emitter.emit(title.toString(), doc);
                        String type = obj.toString();
                        if(type.equals("feed") || type.equals("folder")) {
                            emitter.emit(doc.get("_id").toString(), doc);
                        }
                    }
                }
            }, null, "1.0");
        }

    }

    protected void startEtkorp()
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

               //Gson gson = new Gson();
               //String json = gson.toJson( feed );
               //Feed feed = new Feed("Another blog", 5, false);
               //dbConnector.create("45679", feed);

                startReplications();
                initListAdapters();
           }
        };

        startupTask.execute();
    }

    public void startReplications() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String defaultDatabaseUrl = DATABASE_URL + "/" + DATABASE_NAME;

        pushReplicationCommand = new ReplicationCommand.Builder()
                .source(DATABASE_NAME)
                .target(prefs.getString("sync_url", defaultDatabaseUrl))
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

        pullReplicationCommand = new ReplicationCommand.Builder()
                .source(prefs.getString("sync_url", defaultDatabaseUrl))
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

    protected void initListAdapters() {

        ViewQuery feedViewQuery = new ViewQuery().designDocId(dFeedsDocId).viewName(byIdViewName).descending(false);
        feedListAdapter = new FeedListAdapter(this, dbConnector, feedViewQuery, true);
        this.setListAdapter(feedListAdapter);
        //ListView listview = getListView();
        //listview.setAdapter(feedListAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.feeds, menu);
        return true;
    }
    
}
