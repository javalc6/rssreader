package livio.rssreader;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import livio.rssreader.backend.UserDB;
import livio.rssreader.backend.Item;
import livio.rssreader.backend.ItemArrayAdapter;

import android.annotation.SuppressLint;
import android.app.Activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import androidx.fragment.app.ListFragment;
import tools.FormFactorUtils;

import android.util.Log;
import android.view.Menu;

import android.view.MenuItem;

import android.view.View;
import android.widget.ListView;

import static livio.rssreader.backend.UserDB.DEFAULT_FEED_ID;

// called by SelectCategory activity
public final class ListFeeds extends AppCompatActivity implements NewFeedDialog.EditNameDialogListener {
	private final static String tag = "ListFeeds";
    private static String cat;
    private static int catIdx;//cat index
    private static ArrayList<String[]> currentUserFeeds;//user feeds, list of {title, url, feed_id, cat, timestamp} related to current category
    private static String[][][] nativeFeeds;

    @SuppressLint("NewApi")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        Log.i(tag,"ListFeeds.onCreate");
        setContentView(R.layout.frg_listfeeds);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!FormFactorUtils.isArc(this));
        }

	}

/////////////////////////////////////////    
//primary panel (always present)     
	public static class FeedsFragment extends ListFragment implements NewFeedDialog.EditNameDialogListener {
		UserDB udb;
        SharedPreferences prefs;
		
		@Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {//03-12-2022: sostituisce onActivityCreated deprecated
            super.onViewCreated(view, savedInstanceState);
//	        Log.d(tag, "onViewCreated");
	        final Activity act = getActivity();
            prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(act);

            udb = UserDB.getInstance(act, prefs);

            Intent startingIntent = act.getIntent();
            if (startingIntent != null) {
                Bundle b = startingIntent.getBundleExtra(SelectCategory.ID_CATEGORY);
                if (b != null)	{
                    cat = b.getString("category");
                } else Log.e(tag, "missing category!");
            }

            currentUserFeeds = udb.getUserFeeds(cat);
            nativeFeeds = UserDB.getNativeFeeds();
            catIdx = udb.cat2int(cat);

            ArrayList<Item> itemList = new ArrayList<>();
            int size = sizeFeeds();
            for (int k = 0; k < size; k++) {
//                Log.d(tag, "building itemList["+k+"]: "+getFeed(k)[2]);
                itemList.add(new Item(getFeed(k)[0], false));
            }

            String feed_id = prefs.getString(RSSReader.PREF_FEED_ID, DEFAULT_FEED_ID);

            setListAdapter(new ItemArrayAdapter(act, itemList, this, feed_id));
            ListView lv = getListView();
            lv.setTextFilterEnabled(true);

            lv.setOnItemClickListener((parent, v, position, id) -> { //select a feed
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(RSSReader.PREF_FEED_ID, getFeed(position)[2]);
                editor.apply();
                act.setResult(RESULT_OK);
                act.finish();
            });
//long click --> edit (work in progress)
            lv.setOnItemLongClickListener((parent, v, position, id) -> { //edit a feed
                if (isUserFeed(position)) {//only feeds in user category can be edited
                    String[] feed = getFeed(position);
                    NewFeedDialog editNameDialog = NewFeedDialog.newInstance(feed);
                    editNameDialog.show(((ListFeeds)act).getSupportFragmentManager(), "edit_feed_dialog");
                    return true;
                } else return false;
            });

        }

        @Override
        public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
            super.onViewStateRestored(savedInstanceState);
//            Log.d(tag, "onViewStateRestored");
            final Activity act = getActivity();
            final FloatingActionButton fab = act.findViewById(R.id.addfab);
            Log.d(tag, "fab="+fab);
            if (fab != null) {
                fab.setOnClickListener(v -> {
                    Log.d(tag, "setOnClickListener");

                    NewFeedDialog editNameDialog = NewFeedDialog.newInstance(new String[UserDB.FEED_SIZE]);// new feed
                    editNameDialog.show(((ListFeeds)act).getSupportFragmentManager(), "new_feed_dialog");
                });
            }
        }
        public void onFinishEditDialog(String[] feed) {//add or replace feed
            ItemArrayAdapter iaa = (ItemArrayAdapter) getListAdapter();
            if (iaa != null) {
                if (feed[2] != null) {//just replacing existing feed?
                    int position = updateFeed(feed);
                    Item item = iaa.getItem(position);
                    item.setName(feed[0]);
                } else {
                    addFeed(cat, feed);
                    iaa.add(new Item(feed[0], false));
                    final Activity act = getActivity();
                    if (act != null)
                        act.invalidateOptionsMenu();
                }
                iaa.notifyDataSetChanged();
            }
        }

    private String[] getFeed(int position) {
        if (catIdx >= nativeFeeds.length) // user category
            return currentUserFeeds.get(position);
        if (position >= nativeFeeds[catIdx].length) // user feed
            return currentUserFeeds.get(position - nativeFeeds[catIdx].length);
        return nativeFeeds[catIdx][position];
    }

    public String get_feedid(int position) {
        if (catIdx >= nativeFeeds.length) // user category
            return currentUserFeeds.get(position)[2];
        if (position >= nativeFeeds[catIdx].length) // user feed
            return currentUserFeeds.get(position - nativeFeeds[catIdx].length)[2];
        return nativeFeeds[catIdx][position][2];
    }

    private int sizeFeeds() {
        if (catIdx >= nativeFeeds.length) // user category
            return currentUserFeeds.size();
        else return nativeFeeds[catIdx].length + currentUserFeeds.size();
    }

    public boolean isUserFeed(int position) { // user category is the last one !
        if (catIdx >= nativeFeeds.length) // user category
            return true;//user category contains only user feeds
        else return (position >= nativeFeeds[catIdx].length); // user feed
    }

    private void deleteFeed(int j) {//delete feed, after last delete you shall use synctoFile() to update the file!
        int jj = (catIdx >= nativeFeeds.length) ? j : j - nativeFeeds[catIdx].length;

        if (jj >= 0) {
            String feed_id = currentUserFeeds.get(jj)[2];
//            Log.d(tag, "delete feed: "+feed_id);
            if (feed_id.equals(prefs.getString(RSSReader.PREF_FEED_ID, null))) { //deleting current feed?
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(RSSReader.PREF_FEED_ID);
//                editor.putString(PREF_FEED_ID, DEFAULT_FEED_ID); // set the default feed
                editor.apply();
            }
            currentUserFeeds.remove(jj);
            udb.deleteFeed(feed_id);
        } else Log.e(tag, "incorrect feed id");
    }

    private int updateFeed(String[] feed) {//replace feed
        for (int j = 0; j < currentUserFeeds.size(); j++) {
            if (feed[2].equals(currentUserFeeds.get(j)[2])) {
//                Log.d(tag, "update feed: "+feed[2]);
                currentUserFeeds.set(j, feed);
                udb.updateFeed(feed);
                udb.synctoFile(getActivity());
                if (catIdx >= nativeFeeds.length) // user category
                    return j;//user feed
                else return j + nativeFeeds[catIdx].length;//user feed
            }
        }
        return -1;//error, not found
    }

    private void addFeed(String cat, String[] feed) {//add feed
        udb.addFeed(feed);//eseguire prima di ogni altra azione, in quanto viene assegnato l'id del feed con feed[2]
        feed[3] = cat; // host category
        feed[4] = Long.toString((System.currentTimeMillis()/1000)); //timestamp
        currentUserFeeds.add(feed); // last item in feedArray is the user list
        udb.synctoFile(getActivity());
    }

	}
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        Log.i(tag,"onCreateOptionsMenu");
    	getMenuInflater().inflate(R.menu.select_menu, menu);
        menu.findItem(R.id.menu_delfeed).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;    	
    }    


    @Override
    public  boolean onPrepareOptionsMenu(Menu menu) {
//        Log.i(tag,"onPrepareOptionsMenu");
        FeedsFragment ff =  (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
        if (ff != null) {
            menu.findItem(R.id.menu_delfeed).setVisible(currentUserFeeds.size() > 0);
        }
        return super.onPrepareOptionsMenu(menu);
    }    

    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        } else if (itemId == R.id.menu_delfeed) { // delete a feed
            FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
            if (ff != null) {
                ItemArrayAdapter iaa = (ItemArrayAdapter) ff.getListAdapter();
                int n_items = 0;
                int i = 0;
                while (i < iaa.getCount()) {
                    Item aitem = iaa.getItem(i);
                    if (aitem == null)
                        break;
                    if (aitem.isChecked()) {
                        iaa.remove(aitem);
                        ff.deleteFeed(i);
                        n_items++;
                    } else i++;
                }
                if (n_items == 0) {
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_nothing_to_delete), Snackbar.LENGTH_SHORT).show();
                } else {
                    ff.udb.synctoFile(this);
                    iaa.notifyDataSetChanged();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onFinishEditDialog(String[] feed) {//twin - linked to new_feed_dialog
        FeedsFragment ff =  (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
        if (ff != null) {
            ff.onFinishEditDialog(feed);
        } else {
            Log.d(tag, "null FeedsFragment on ListFeeds.onFinishEditDialog");
        }
    }

}	

