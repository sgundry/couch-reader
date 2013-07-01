package com.gumtree.couchreader;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewMapEmitBlock;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ektorp.CouchDbConnector;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.android.util.CouchbaseViewListAdapter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;

class Feed {
    public String id;
    public String title;
    public int num_unread = 0;
    public boolean is_folder = false;

    public Feed(String title, int num_unread, boolean is_folder)     {
        this.title = title;
        this.num_unread = num_unread;
        this.is_folder = is_folder;
    }

    public Feed(JsonNode doc) {
        this.id         = Doc.getStringValue(doc, "_id", "");
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

    protected FeedListActivity parent;

    public FeedListAdapter(FeedListActivity parent, CouchDbConnector couchDbConnector, ViewQuery viewQuery, boolean followChanges) {
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

            vh.feed_icon        = (ImageView) v.findViewById(R.id.icon);
            vh.feed_title       = (TextView)  v.findViewById(R.id.title);
            vh.feed_num_unread  = (TextView)  v.findViewById(R.id.num_unread);

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

public class FeedListActivity extends ListActivity {

    public static final java.lang.String TAG = "FeedListActivity";

    private static final String     dFeedsDocName       = "feeds";
    private static final String     dFeedsDocId         = "_design/" + dFeedsDocName;
    private static final String     byIdViewName        = "by_id_brg3";
    private static final String     byFolderViewName    = "by_folder3";

    public static final String      FOLDER_MESSAGE      = "com.gumtree.couchreader.FOLDER";

    private CouchContext            couchContext        = null;
    private String                  folderName          = "/";

    private static boolean          viewsInitialised    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate()");

        CouchReaderApp app = ((CouchReaderApp)getApplicationContext());
        assert(app != null);

        couchContext = app.getCouchContext();
        assert(couchContext != null && couchContext.isConnected());

        Intent intent = getIntent();
        if(intent.hasExtra(FOLDER_MESSAGE)) {
            folderName = intent.getStringExtra(FOLDER_MESSAGE);
        }

        Log.d(TAG, "onCreate: folderName=" + folderName);

        //if(!viewsInitialised) {
            installViews();
        //    viewsInitialised = true;
        //}

        initListAdapters();

        setContentView(R.layout.activity_feeds);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ViewResult.Row row = ((FeedListAdapter)this.getListAdapter()).getRow(position);
        Feed feed = new Feed(row.getValueAsNode());

        Log.d(TAG, "Doc: " + row.getValueAsNode().toString());
        Log.d(TAG,  String.format("Feed clicked: title=%s folder=%b", feed.title, feed.isFolder()));

        if(feed.isFolder()) {
            // start new feeds list activity corresponding to this folder
            Intent intent = new Intent(this, FeedListActivity.class);
            intent.putExtra(FOLDER_MESSAGE, feed.title);
            startActivity(intent);
        }
        else {
            // start EntryListActivity corresponding to this feed
            Intent intent = new Intent(this, EntryListActivity.class);
            intent.putExtra(EntryListActivity.FEED_ID_MESSAGE, feed.id);
            startActivity(intent);
        }
    }

    protected void initListAdapters() {

        ViewQuery viewQuery;
        if(folderName.equals("/")) {
            viewQuery = new ViewQuery().designDocId(dFeedsDocId)
                                       .viewName(byIdViewName)
                                       .descending(false);
        }
        else
        {
            viewQuery = new ViewQuery().designDocId(dFeedsDocId)
                                       .viewName(byFolderViewName)
                                       .key(folderName)
                                       .descending(false);
        }
        setListAdapter(new FeedListAdapter(this, couchContext.getConnector(), viewQuery, true));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.feeds, menu);
        return true;
    }

    // Install views for existing CBL and initialise list adapters.
    private void installViews() {

        assert( couchContext != null );
        CBLDatabase db = couchContext.getDatabase();

        // feeds/_view/by_folder
        {
            String viewName = String.format("%s/%s", dFeedsDocName, byFolderViewName);
            db.deleteViewNamed(viewName); // For now, just delete the pre-existing view since they're in flux

            CBLView view = db.getViewNamed(viewName);
            view.setMapReduceBlocks(new CBLViewMapBlock() {

                @Override
                public void map(Map<String, Object> doc, CBLViewMapEmitBlock emitter) {
                    Object folders = doc.get("folders");
                    Object type = doc.get("type");
                    if(folders != null && type != null && type.toString().equals("feed")) {
                        //emitter.emit(doc.get("_id").toString(), doc);
                        ObjectMapper mapper = new ObjectMapper();
                        Type listType = new TypeToken<ArrayList<String>>(){}.getType();
                        ArrayList<String> folderNames = new Gson().fromJson(folders.toString(), listType);
                        Log.d(TAG, "Emitting document for folders: " + TextUtils.join(", ", folderNames));
                        for(String name : folderNames ) {
                            Log.d(TAG, "Emitting document: " + doc.toString());
                            emitter.emit(name, doc);
                        }
                    }
                }
            }, null, "1.0");
        }

        // feeds/_view/by_id
        {
            String viewName = String.format("%s/%s", dFeedsDocName, byIdViewName);
            db.deleteViewNamed(viewName); // For now, just delete the pre-existing view since they're in flux

            CBLView view = db.getViewNamed(viewName);
            view.setMapReduceBlocks(new CBLViewMapBlock() {

                @Override
                public void map(Map<String, Object> doc, CBLViewMapEmitBlock emitter) {
                    Object obj = doc.get("type");
                    if(obj != null) {
                        String type = obj.toString();
                        if(type.equals("feed") || type.equals("folder")) {
                            emitter.emit(doc.get("_id").toString(), doc);
                        }
                    }
                }
            }, null, "1.0");
        }
    }
}
