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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import livio.rssreader.backend.FeedsDB;
import livio.rssreader.backend.UserDB;
import livio.rssreader.backend.Item;
import livio.rssreader.backend.ItemArrayAdapter;

import android.annotation.SuppressLint;
import android.app.Activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import androidx.fragment.app.ListFragment;
import tools.FormFactorUtils;
import tools.OPMLOutline;
import tools.OPMLParser;
import tools.SimpleFileManager;

import android.util.Log;
import android.view.Menu;

import android.view.MenuItem;

import android.view.View;
import android.widget.ListView;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import static livio.rssreader.backend.UserDB.DEFAULT_FEED_ID;
import static livio.rssreader.backend.UserDB.FEED_SIZE;

// called by SelectCategory activity
public final class ListFeeds extends AppCompatActivity implements NewFeedDialog.EditNameDialogListener {
	private final static String tag = "ListFeeds";
    private static String cat;
    private static int catIdx;//cat index
    private static ArrayList<String[]> currentUserFeeds;//user feeds, list of {title, url, feed_id, cat, timestamp} related to current category
    private static String[][][] nativeFeeds;

    private final static int MAX_NUM_OPML_IMPORT = 1000; // maximum number of imported opml outlines

    @SuppressLint("NewApi")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        private SimpleFileManager mFileHandler;

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {//03-12-2022: sostituisce onActivityCreated deprecated
            super.onViewCreated(view, savedInstanceState);
	        final Activity act = getActivity();
            prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(act);

            mFileHandler = new SimpleFileManager(act, this::decodeFile, this::encodeFile);//zzimport

            udb = UserDB.getInstance(act, prefs);

            boolean importOPML = false;
            Intent startingIntent = act.getIntent();
            if (startingIntent != null) {
                Bundle b = startingIntent.getBundleExtra(SelectCategory.ID_CATEGORY);
                if (b != null)	{
                    cat = b.getString("category");
                    importOPML = b.getBoolean("import", false);
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

            if (importOPML) {
                importOPML();
            }
        }

        public void importOPML() {
            mFileHandler.openFileSAF("*/*");
        }

        @Override
        public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
            super.onViewStateRestored(savedInstanceState);
            final Activity act = getActivity();
            final FloatingActionButton fab = act.findViewById(R.id.addfab);
            if (fab != null) {
                fab.setOnClickListener(v -> {
                    NewFeedDialog editNameDialog = NewFeedDialog.newInstance(new String[FEED_SIZE]);// new feed
                    editNameDialog.show(((ListFeeds)act).getSupportFragmentManager(), "new_feed_dialog");
                });
            }
        }

        public void onFinishEditDialog(@NonNull String[] feed) {//add or replace feed
            ItemArrayAdapter iaa = (ItemArrayAdapter) getListAdapter();
            if (iaa != null) {
                if (feed[2] != null) {//just replacing existing feed?
                    int position = updateFeed(feed);
                    if (position != -1) {
                        udb.synctoFile(getActivity());
                        Item item = iaa.getItem(position);
                        item.setName(feed[0]);
                    } else Log.e(tag, "incorrect position returned by updateFeed");
                } else {
                    addFeed(cat, feed);
                    udb.synctoFile(getActivity());
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
            } else Log.e(tag, "incorrect feed id " + j);
        }

        private int updateFeed(String[] feed) {//replace feed
            for (int j = 0; j < currentUserFeeds.size(); j++) {
                if (feed[2].equals(currentUserFeeds.get(j)[2])) {
    //                Log.d(tag, "update feed: "+feed[2]);
                    currentUserFeeds.set(j, feed);
                    udb.updateFeed(feed);
                    if (catIdx >= nativeFeeds.length) // user category
                        return j;//user feed
                    else return j + nativeFeeds[catIdx].length;//user feed
                }
            }
            return -1;//error, not found
        }

        private void addFeed(String cat, @NonNull String[] feed) {//add feed
            udb.addFeed(feed);//eseguire prima di ogni altra azione, in quanto viene assegnato l'id del feed con feed[2]
            feed[3] = cat; // host category
            feed[4] = Long.toString((System.currentTimeMillis()/1000)); //timestamp
            currentUserFeeds.add(feed); // last item in feedArray is the user list
        }

//todo: implement performance efficient solution in future
        private boolean containsURL(@NonNull String url) {
            for (String[] feed: currentUserFeeds)
                if (feed[1].equals(url))
                    return true;
            return false;
        }

        public boolean decodeFile(@NonNull InputStream is) {//zzimport
            OPMLParser op = new OPMLParser();
            try {
                ItemArrayAdapter iaa = (ItemArrayAdapter) getListAdapter();
                assert iaa != null;
                ArrayList<OPMLOutline> outlines = op.parse(is);
//                Log.d(tag, "Number of outlines: " + outlines.size());
                int count = 0;
                for (OPMLOutline outline: outlines) {
//                    Log.d(tag, outline.getType() + " - " + outline.getText() + " - " + outline.getUrl());
                    String url = outline.getUrl();
                    if (!containsURL(url)) {//check for duplicate urls
                        String[] feed = new String[FEED_SIZE];
                        feed[0] = outline.getText();
                        feed[1] = url;
                        feed[2] = null;
                        addFeed(cat, feed);
                        iaa.add(new Item(feed[0], false));
                        if (++count >= MAX_NUM_OPML_IMPORT)
                            break;
                    }
                }
                if (count > 0) {
                    udb.synctoFile(getActivity());
                    iaa.notifyDataSetChanged();
                }
                return true;
            } catch (IOException | ParserConfigurationException | SAXException e) {
                Log.e(tag, e.toString());
                return false;
            }
        }

        public boolean encodeFile(@NonNull OutputStream os) {//zzexport
            //todo with export feature
            Log.d(tag, "encodeFile not implemented");
            return false;
        }
    }
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.select_menu, menu);
        return true;
    }    


    @Override
    public  boolean onPrepareOptionsMenu(Menu menu) {
        FeedsFragment ff =  (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
        if (ff != null) {
            boolean visible = !currentUserFeeds.isEmpty();
            menu.findItem(R.id.menu_del_feed).setVisible(visible);
            menu.findItem(R.id.menu_select_all).setVisible(visible);
            menu.findItem(R.id.menu_move_feed).setVisible(visible);
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
        } else if (itemId == R.id.menu_import_opml) { // import OPML file
            FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
            if (ff != null)
                ff.importOPML();
            return true;
        } else if (itemId == R.id.menu_select_all) { // select all user feeds
            FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
            if (ff != null) {
                ItemArrayAdapter iaa = (ItemArrayAdapter) ff.getListAdapter();
                int j = (catIdx >= nativeFeeds.length) ? 0 : nativeFeeds[catIdx].length;
                for (int i = j; i < iaa.getCount(); i++)
                    iaa.getItem(i).setChecked(true);
                iaa.notifyDataSetChanged();
            }
            return true;
        } else if (itemId == R.id.menu_del_feed) { // delete feed(s)
            FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
            if (ff != null) {
                ItemArrayAdapter iaa = (ItemArrayAdapter) ff.getListAdapter();
                int n_items = 0;
                int j = (catIdx >= nativeFeeds.length) ? 0 : nativeFeeds[catIdx].length;
                for (int i = j; i < iaa.getCount(); ) {
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
        } else if (itemId == R.id.menu_move_feed) { // move feed(s)
            FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
            if (ff != null) {
                selectCategoryDialog(ff);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onFinishEditDialog(@NonNull String[] feed) {//twin - linked to new_feed_dialog
        FeedsFragment ff =  (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
        if (ff != null) {
            ff.onFinishEditDialog(feed);
        } else {
            Log.d(tag, "null FeedsFragment on ListFeeds.onFinishEditDialog");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {//zzimport
//        Log.i(tag,"result: "+resultCode+", requestCode: "+requestCode);
        FeedsFragment ff = (FeedsFragment) getSupportFragmentManager().findFragmentById(R.id.feeds);
        if (ff == null || !ff.mFileHandler.processActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
    }

    private void selectCategoryDialog(@NonNull FeedsFragment ff) {//custom dialog to select target category
        final ItemArrayAdapter iaa = (ItemArrayAdapter) ff.getListAdapter();
        assert iaa != null;
        if (isAnyCheckedItems(iaa)) {
            final ArrayList<String> catList = new ArrayList<>();
            final String[] categories = getResources().getStringArray(R.array.categories_list);//localized names
            for (int k = 0; k < FeedsDB.categories.length; k++)
                catList.add(categories[k]);

            for (int k = 0; k < ff.udb.getUserCats().size(); k++)//user categories
                catList.add(ff.udb.getUserCat(k)[0]);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.move_to_category)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .setSingleChoiceItems(catList.toArray(new String[0]), -1, (dialog, which) -> {
                        Log.d(tag, "which=" + which);
                        if (which != catIdx) {//target category != current category
                            int n_items = 0;
                            int j = (catIdx >= nativeFeeds.length) ? 0 : nativeFeeds[catIdx].length;
                            for (int i = j; i < iaa.getCount(); ) {
                                Item aitem = iaa.getItem(i);
                                if (aitem == null)
                                    break;
                                if (aitem.isChecked()) {
                                    iaa.remove(aitem);
                                    String[] feed = ff.getFeed(j);
                                    if (which < FeedsDB.categories.length)
                                        feed[3] = FeedsDB.categories[which][2];
                                    else feed[3] = ff.udb.getUserCat(which - FeedsDB.categories.length)[2];
                                    ff.updateFeed(feed);
                                    n_items++;
                                } else i++;
                                j++;
                            }
                            if (n_items > 0) {
                                ff.udb.synctoFile(this);
                                iaa.notifyDataSetChanged();
                            }
                        } else {//clear user choices
                            recreate();
                        }
                        dialog.dismiss();
                    }).show();
        } else Snackbar.make(findViewById(android.R.id.content), getString(R.string.noitems_please_select), Snackbar.LENGTH_SHORT).show();
    }

    private boolean isAnyCheckedItems(@NonNull ItemArrayAdapter iaa) {
        for (int i = 0; i < iaa.getCount(); i++) {
            if (iaa.getItem(i).isChecked())
                return true;
        }
        return false;
    }

}	

