package com.gumtree.couchreader;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewMapEmitBlock;

import org.codehaus.jackson.JsonNode;
import org.ektorp.CouchDbConnector;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.android.util.CouchbaseViewListAdapter;

import java.util.Map;

class EntryListAdapter extends CouchbaseViewListAdapter {

    protected EntryListActivity parent;

    public EntryListAdapter(EntryListActivity parent, CouchDbConnector couchDbConnector, ViewQuery viewQuery, boolean followChanges) {
        super(couchDbConnector, viewQuery, followChanges);
        this.parent = parent;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView title;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = convertView;

        if(v == null) {
            LayoutInflater vi =
                    (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            v = vi.inflate(R.layout.list_item_entry, null);
            ViewHolder vh  = new ViewHolder();

            vh.icon = (ImageView) v.findViewById(R.id.icon);
            vh.title = (TextView)  v.findViewById(R.id.title);

            v.setTag(vh);
        }

        ViewResult.Row row  = getRow(position);
        ViewHolder vh       = (ViewHolder)v.getTag();

        JsonNode doc    = row.getValueAsNode();

        boolean starred = doc.has("starred") && doc.get("starred").asBoolean();
        boolean unread  = doc.has("unread") && doc.get("unread").asBoolean();

        vh.icon.setImageResource(starred ? R.drawable.gold_star : R.drawable.empty_star);
        vh.title.setText(doc.get("title").asText());
        vh.title.setTypeface(unread ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

        return v;
    }
};

public class EntryListActivity extends ListActivity {

    public static final java.lang.String TAG = "EntryListActivity";

    private static final String     dEntriesDocName     = "entries";
    private static final String     dEntriesDocId       = "_design/" + dEntriesDocName;
    private static final String     byFeedIdViewName    = "by_feed_id";

    public static final String      FEED_ID_MESSAGE = "com.gumtree.couchreader.FEED_ID";

    private CouchContext            couchContext        = null;
    private String                  feedId;
    private boolean                 viewsInitialised    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CouchReaderApp app = ((CouchReaderApp)getApplicationContext());
        assert(app != null);

        couchContext = app.getCouchContext();
        assert(couchContext != null && couchContext.isConnected());

        Intent intent = getIntent();
        if(intent.hasExtra(FEED_ID_MESSAGE)) {
            feedId = intent.getStringExtra(FEED_ID_MESSAGE);
        }
        else {
            throw new RuntimeException("Cannot create entry list, missing feed ID");
        }

        Log.d(TAG, "onCreate: feedId=" + feedId);

        //if(!viewsInitialised) {
            installViews();
        //    viewsInitialised = true;
        //}
        initListAdapters();

        setContentView(R.layout.activity_entries);
    }

    protected void initListAdapters() {

        ViewQuery viewQuery = new ViewQuery().designDocId(dEntriesDocId)
                                             .viewName(byFeedIdViewName)
                                             .key(feedId)
                                             .descending(false);

        setListAdapter(new EntryListAdapter(this, couchContext.getConnector(), viewQuery, true));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.entries, menu);
        return true;
    }

    // Install views for existing CBL and initialise list adapters.
    private void installViews() {

        assert( couchContext != null );
        CBLDatabase db = couchContext.getDatabase();

        // entries/_view/by_feed_id
        {
            String viewName = String.format("%s/%s", dEntriesDocName, byFeedIdViewName);
            db.deleteViewNamed(viewName); // For now, just delete the pre-existing view since they're in flux

            CBLView view = db.getViewNamed(viewName);
            view.setMapReduceBlocks(new CBLViewMapBlock() {

                @Override
                public void map(Map<String, Object> doc, CBLViewMapEmitBlock emitter) {
                    Object type = doc.get("type");
                    if(type != null && type.toString().equals("entry")) {
                        Object feed_id = doc.get("feed_id");
                        if(feed_id != null && feed_id.toString().equals(feedId)) {
                            emitter.emit(feed_id.toString(), doc);
                        }
                    }
                }
            }, null, "1.0");

        }
    }
}
