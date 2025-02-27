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
/*Main classes:
  RSSReader: front-end and rss manager
  RSSReaderWorker: rss worker engine

Secondary classes:
  ListFeeds: shows a list of feeds to the end user, called by SelectCategory
  NewCategoryDialog
  NewFeedDialog: dialog to define a new feed, called by ListFeeds
  PreferencesFragXML: manage app settings
  RSSWidget,RSSWidgetDark: app widget
  SelectCategory: shows a list of categories, called by RSSReader
  SelectColors: color customization
  ShowCredits: show credits
  ShowHelp: show help
  ShowItem: show an item retrieved from a rssfeed, called by RSSReader

Backend classes:
  FeedsDB: db of feeds
  FeedsTree: directory handler for categoriesand feeds
  RSSFeed: feed processor
  RSSItem: an item of a specific feed
  TTSEngine: text to speech engine interface

File handling list of feeds:
  userFeeds0.ser: serialized user feeds - managed by FeedsTree.java

Files generated during runtime:
  <feed_id>.cache : serialized RSSFeed.java (list of items in a specific feed)
  <feed_id> := <user feed id> | <category letter><sequence number>
  <user feed id> := number
*/
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.UtteranceProgressListener;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.PopupMenu;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import livio.rssreader.backend.CardContent;
import livio.rssreader.backend.FeedsDB;
import livio.rssreader.backend.UserDB;
import livio.rssreader.backend.RSSFeed;
import livio.rssreader.backend.RSSItem;
import livio.rssreader.backend.TTSEngine;
import tools.FileHandler;
import tools.FileManager;
import tools.LocalBroadcastManager;//added after deprecation of orignal class from Google
import tools.ReportBug;
import workers.RSSReaderWorker;

import static livio.rssreader.SelectCategory.ID_CATEGORY;
import static livio.rssreader.SelectColors.getThemeColors;
import static livio.rssreader.SelectColors.setNightMode;
import static livio.rssreader.backend.TTSEngine.utteranceId_last;
import static livio.rssreader.backend.TTSEngine.utteranceId_oneshot;
import static tools.ColorBase.isDarkColor;
import static tools.FileManager.EXTENDED_BACKUP_MIMETYPE_ONEDRIVE;


public final class RSSReader extends AppCompatActivity implements FileHandler, AudioManager.OnAudioFocusChangeListener {

    public static final int DIALOG_RSS_ERROR_ID = 0;
    public static final int DIALOG_HTTP_ERROR_ID = 1;
    public static final int DIALOG_BAD_ANSWER_ID = 2;
    public static final int DIALOG_CONN_ERROR_ID = 3;
    public static final int DIALOG_INTERRUPTED_ID = 4;
    public static final int DIALOG_MIMETYPE_ERROR_ID = 5;

    private static final int REQUEST_CODE_PREFERENCES = 1;
    private static final int REQUEST_SELECT_FEED = 2;
//    private static final int REQUEST_EXTENDED_RESTORE = 3;

    public final static String ID_ITEM = "livio.rssreader.item";

    static final String APP_FOLDER = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ?
            "livio"+File.separator+"rssreader" : Environment.DIRECTORY_DOWNLOADS;

    private static final String EXTENDED_BACKUP_FILENAME = "rssreader.backup";
    private static final String BACKUP_FILENAME = "backup.z";

    private static final String PREF_BACKUP_TIME = "backup_time";	// backup date
    private static final String PREF_BACKUP_ORIGIN = "backup_origin";	// backup origin

    private static final String PREF_BACKUP_DIALOG = "backup_dialog_ts";

    public static final String PREF_THEME = "theme";
    public static final String PREF_THEME_AUTO = "theme_auto";
    public static final String PREF_FONTSIZE = "fontsize";
    public static final String PREF_LT_TEXTCOLOR = "lt_text_color";
    public static final String PREF_LT_HYPERLINKCOLOR = "lt_hyperlink_color";
    public static final String PREF_LT_GENERICCOLOR = "lt_generic_color";
    public static final String PREF_DT_TEXTCOLOR = "dt_text_color";// dark theme colors
    public static final String PREF_DT_HYPERLINKCOLOR = "dt_hyperlink_color";// dark theme colors
    public static final String PREF_DT_GENERICCOLOR = "dt_generic_color";	// dark theme colors
    public static final String PREF_REFRESH_TIMER = "refresh_timer";
    public static final String PREF_MAX_TITLES = "max_titles";
    public static final String PREF_FEEDS_LANGUAGE = "feeds_language";
    public static final String PREF_DOWNLOAD_IMAGES = "download_images";
    public static final String PREF_USE_EXTERNAL_BROWSER = "use_external_browser";
    public static final String PREF_SMART_TITLES = "smart_titles";
    public static final String PREF_CLIENT_VERSION = "client_version";
    public static final String PREF_FEED_ID = "news_feed";

    public static final String PREF_BRICIOLA = "briciola";//used in place of Settings.Secure.ANDROID_ID, DO NOT insert this in backable_prefs

    private final static String tag = "RSSReader";

    public final static String uniqueWorkerName = "RSSReaderService";

    public static final String RSS_ACCEPT_MIME = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.5";
    public static final String RSS_USER_AGENT = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; unknown) Firefox/62.0";//dummu user agent to get expected response from most remote servers
    ////////////////////////////////////////////
    private SharedPreferences prefs;

    private boolean mCreateRecoveryFile;
    private final static long zzBackup_age = 7L * 24L * 3600L * 1000L;

    private static String latest_feed_id = null;
    static String message_publisher = "unknown";//publisher
    //colorpicker

    private static final String style_tpl = " A {color: #%06x;} body {background-color:#%06x;color:#%06x;margin-right:32px;word-wrap:break-word;} hr {background-color:#%06x;color:#%06x;height:1px;border:0px;} img {max-width:100%%;height:auto;} "; // nota il %% per evitare eccezioni con String.format()

    private TTSEngine mTts;

    public static Locale getLocale(String lang_code) {
        if (lang_code.startsWith("en"))
            return Locale.ENGLISH;
        else if (lang_code.startsWith("fr"))
            return Locale.FRENCH;
        else if (lang_code.startsWith("it"))
            return Locale.ITALIAN;
        else if (lang_code.startsWith("de"))
            return Locale.GERMAN;
        else {//other languages, e.g. spanish
            int split = lang_code.indexOf('-');
            if (split != -1) { // split language country
                return new Locale(lang_code.substring(0, split), lang_code.substring(split + 1));
            } else return new Locale(lang_code);
        }
    }

    private final FileManager mBackupRestore = new FileManager(this, this, true, APP_FOLDER);

    /** Called when the activity is first created. */
    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        setNightMode(prefs);//importante: deve essere eseguito prima di super.onCreate()
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG)
            Log.d(tag, "onCreate");
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {//zzedge-2-edge
            EdgeToEdge.enable(this);//importante: deve essere eseguito prima di setContentView()
        }

        setContentView(R.layout.main);

        ReportBug.enableMonitor(this);

        System.setProperty("http.keepAlive", "false"); // workaround to avoid responseCode = -1 problem

        mCreateRecoveryFile = false;//autobackup
        File owndir = getExternalFilesDir(null);//autobackup
        if (owndir != null) {//autobackup
            File backup_file = new File(owndir, BACKUP_FILENAME);
            if (backup_file.exists()) {
                long age = System.currentTimeMillis() - backup_file.lastModified();
                if (age > zzBackup_age) // file is too old
                    mCreateRecoveryFile = true;
            } else mCreateRecoveryFile = true;
        }//autobackup


        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mTts = new TTSEngine(this, new UtteranceProgressListener() {
            @Override
            public void onDone(String utteranceId) {
                Log.i(tag, "onDone:" + utteranceId);
                if (utteranceId_last.equals(utteranceId) || utteranceId_oneshot.equals(utteranceId)) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int result = am.abandonAudioFocus(RSSReader.this); // ignore result
                    invalidateOptionsMenu(); //play - update icon
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.i(tag, "onError:" + utteranceId);
                if (utteranceId_last.equals(utteranceId) || utteranceId_oneshot.equals(utteranceId)) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int result = am.abandonAudioFocus(RSSReader.this); // ignore result
                    invalidateOptionsMenu(); //play - update icon
                }
            }

            @Override
            public void onStart(String utteranceId) {
                Log.i(tag, "onStart:" + utteranceId);
/* for future development
                    if (utteranceId_first.equals(utteranceId) || utteranceId_oneshot.equals(utteranceId)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (bottombox == null) {
                                    ImageButton speakpagebutton2 = (ImageButton) DictionaryBase.this.findViewById(R.id.speakpagebutton2);
                                    if (speakpagebutton2 != null)
                                        speakpagebutton2.setImageResource(R.drawable.btn_stop);
                                } else {
                                    ImageButton speakpagebutton = (ImageButton) bottombox.findViewById(R.id.speakpagebutton);
                                    speakpagebutton.setImageResource(R.drawable.btn_stop);
                                    bottombox.startAnimation(new AlphaAnimation(1, 1));
                                }
                            }//public void run() {
                        });
                    }
*/
            }
        });


        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swiperefresh);
        swipeRefresh.setOnRefreshListener(
                () -> {
//                        Log.i(tag, "onRefresh called from SwipeRefreshLayout");
                    refresh(false);
                }
        );
        if (savedInstanceState == null){//started from scratch ?

            swipeRefresh.setRefreshing(true);//show refresh icon at first launch
            String pref_version = prefs.getString(PREF_CLIENT_VERSION, null);
            if ((pref_version == null) || !pref_version.equals(BuildConfig.VERSION_NAME)) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(PREF_CLIENT_VERSION, BuildConfig.VERSION_NAME);
                editor.apply();
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("RSSReaderService"));//register message receiver, must be before first doPeriodicWork()
        doPeriodicWork();//workmanager

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri intent_data = intent.getData();
            if (intent_data != null) {
                if (BuildConfig.DEBUG)
                    Log.d(tag, "intent uri: " + intent_data);
//TODO: process feed via intent
//command: adb shell am start -W -a android.intent.action.VIEW -d "<replace with url to feed>"  -t "application/rss+xml"
            }
        }
    }

    @Override
    public void onDestroy() {
        if (BuildConfig.DEBUG)
            Log.d(tag, "onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        int[] ids = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, RSSWidget.class));
        int[] ids_dark = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, RSSWidgetDark.class));
        if (ids.length + ids_dark.length == 0) {//no widgets exist
            WorkManager.getInstance(this).cancelUniqueWork(uniqueWorkerName);
        }


        if (mCreateRecoveryFile)//autobackup
            mBackupRestore.createAutoRecovery(BACKUP_FILENAME);//autobackup

        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }


//        mWebView.clearCache(true);    
// clear cache to avoid uncontrolled growing        
        deleteFiles(new Date().getTime() - DateUtils.DAY_IN_MILLIS);

        super.onDestroy();
    }

    protected void onPause() { // persistent data should be saved here!
        if (BuildConfig.DEBUG)
            Log.d(tag, "onPause");
        stopTTS(); //speech
        super.onPause();
    }


    private void restart() {
// workaround to handle issues on recreate() -> http://stackoverflow.com/questions/10844112/runtimeexception-performing-pause-of-activity-that-is-not-resumed
        new Handler().postDelayed(this::recreate, 1);
    }

    private void deleteFiles(long limit) {
        File dir = getCacheDir();
        if (dir != null && dir.isDirectory()) {
            String feed_id = prefs.getString(PREF_FEED_ID, null);
            String feedFileName = feed_id != null ? feed_id.concat(".cache") : null;

            for (File child : dir.listFiles()) {
                if (child.isFile() && child.lastModified() < limit) {
                    if ((feedFileName == null) || (!feedFileName.equals(child.getName()))) {
                        child.delete();
//                        Log.i(tag, child.getName() + " deleted!");
                    }
                }
            }
        }
    }

    private void deleteAllFiles() {//delete any cache file!
        File dir = getCacheDir();
        if (dir != null && dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                if (child.isFile()) {
                    child.delete();
//                    Log.i(tag, child.getName() + " deleted!");
                }
            }
        }
    }

    //work-around of options menu hardware key (e.g. LG L3)  to avoid NullPointerException at com.android.internal.policy.impl.PhoneWindow.onKeyUpPanel
    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        if (keycode == KeyEvent.KEYCODE_MENU) {
            openOptionsMenu();
            return true;
        }
        return super.onKeyDown(keycode, e);
    }

    private MenuItem ttsplay;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (BuildConfig.DEBUG)
            Log.d(tag, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        ttsplay = menu.findItem(R.id.menu_play);
        ttsplay.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }    
    

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (BuildConfig.DEBUG)
            Log.d(tag, "onPrepareOptionsMenu");
        MenuItem play = menu.findItem(R.id.menu_play);//play
        if (play != null)
            play.setIcon(R.drawable.ic_play_arrow_white_36dp);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
// Handle item selection
        int itemId = item.getItemId();
        if (itemId == R.id.menu_preferences) {
            showPreferences();
            return true;
        } else if (itemId == R.id.menu_publisher) {//publisher
            new Publisher_DF().show(getSupportFragmentManager(), "publisher");  //df
            return true;
        } else if (itemId == R.id.rss_refresh) {// refresh news
            refresh(true);
            return true;
        } else if (itemId == R.id.menu_import_opml) {//zzimport
            selectCategoryDialogImport();
            return true;
        } else if (itemId == R.id.menu_play) {
            if (mTts.isSpeaking()) {
                stopTTS();
                return true;
            }
            String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, getString(R.string.default_feed_language_code));
            String feed_id = prefs.getString(PREF_FEED_ID, null);//lang
            if (feed_id == null) {
                FeedsDB feedsDB = FeedsDB.getInstance();
                feed_id = feedsDB.getDefaultFeedId(pref_lang);//lang
            }
            File feedFile = new File(getCacheDir(), feed_id.concat(".cache"));
            if (feedFile.exists()) {
                try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile))) {
                    RSSFeed feed = (RSSFeed) is.readObject();
                    if ((feed != null) && mTts.checkTTS(feed.getLanguage(pref_lang))) {
                        List<RSSItem> items = feed.getAllItems();
                        if (!items.isEmpty()) {
                            String[] segments = new String[items.size()];
                            int k = 0;
                            boolean smart_titles = prefs.getBoolean(PREF_SMART_TITLES, false);
                            for (RSSItem ritem : items) {
                                segments[k++] = ritem.getTitle(smart_titles);
                            }
                            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
// Request audio focus for playback
                            if (am != null) {
                                int result = am.requestAudioFocus(this,
                                        AudioManager.STREAM_MUSIC,// Use the music stream.
                                        AudioManager.AUDIOFOCUS_GAIN);// Request permanent focus.

                                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                    // Start playback.
                                }
                            }
                            if (mTts.speakSegments(segments))
                                item.setIcon(R.drawable.ic_stop_white_36dp);//play
                        }
                    }
                } catch (IOException e) {
                    //do nothing
                    Log.i(tag, "IOException in onOptionsItemSelected");
                } catch (ClassNotFoundException e) {
                    //do nothing
                    Log.i(tag, "ClassNotFoundException in onOptionsItemSelected");
                }
            } else Log.i(tag, "feedFile does not exist on R.id.menu_play");
            return true;
        } else if (itemId == R.id.menu_backup) {
            doBackup(mBackupRestore);
            return true;
        } else if (itemId == R.id.menu_restore) {//scopedstorage (purtroppo in Android 11 non si riesce ad usare in modo sicuro filename2uri_downloads_Q(), quindi usiamo per il restore comunque il SAF, come nel caso di storage esterno
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                mBackupRestore.openFileSAF(EXTENDED_BACKUP_MIMETYPE_ONEDRIVE, false);//scopedstorage, use SAF on Android 11 or later
            } else {
//18-09-2022: nuova gestione con PopupMenu per evitare problemi in ChromeOS basato su Android 11 (o superiore)
//PopupMenu risolve problema del passaggio del mouse sopra la voce del menu 'restore' che apre il filemanager su ChromeOS in modo indesiderato
                PopupMenu popup = showRestorePopup(findViewById(R.id.toolbar), Gravity.END);

                popup.setOnMenuItemClickListener(item1 -> {
//                    mDrawerLayout.closeDrawer(mDrawerList);
                    int itemId1 = item1.getItemId();
                    if (itemId1 == R.id.menu_ext_storage) {//restore from external storage
                        mBackupRestore.readExternalFile();
                        return true;
                    } else if (itemId1 == R.id.menu_local_storage) {//restore from local storage (direct)
                        mBackupRestore.readLocalFile(EXTENDED_BACKUP_FILENAME, BACKUP_FILENAME);//scopedstorage
                        return true;
                    }
                    return false;//never happen
                });
            }
            return true;
        } else if (itemId == R.id.menu_exit) {
            finishAffinity();//nn
            return true;
        } else if (itemId == R.id.menu_help) {
            Intent intent = new Intent(this, ShowHelp.class);
            intent.putExtra("help", getString(R.string.helpfile));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private PopupMenu showRestorePopup(View view, int gravity) {
        PopupMenu popup = new PopupMenu(RSSReader.this, view, gravity);
        Menu popmenu = popup.getMenu();
        popmenu.add(Menu.NONE, R.id.menu_local_storage, Menu.NONE, R.string.local_storage);//menu_local_storage is hidden in buildNavDrawerItems()
        popmenu.add(Menu.NONE, R.id.menu_ext_storage, Menu.NONE, R.string.ext_storage);//menu_ext_storage is hidden in buildNavDrawerItems()

        popup.show();
        return popup;
    }

    private static void doBackup(FileManager mBackupRestore) {
        if ((Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) || !mBackupRestore.createFileSAF(EXTENDED_BACKUP_FILENAME)) {//scopedstorage
            final String AUTHORITY_FP = BuildConfig.APPLICATION_ID + ".FileProvider";
            mBackupRestore.saveFile(EXTENDED_BACKUP_FILENAME, BACKUP_FILENAME, AUTHORITY_FP, R.mipmap.ic_launcher);
        }
    }


    private void refresh(boolean showrefresh) {
        doPeriodicWork();//workmanager
        if (showrefresh) {
            SwipeRefreshLayout swipeRefresh = findViewById(R.id.swiperefresh);
            swipeRefresh.setRefreshing(true);
        }
    }

    @SuppressLint("NewApi")
    private void setTitle(String title, String subtitle, String publisherlink) {//publisher
        message_publisher = publisherlink;
        ActionBar t = getSupportActionBar();
        if (t != null) {
            t.setTitle(title);
            t.setSubtitle(subtitle);
        }
    }

    protected void onResume() {
        super.onResume();
        if (BuildConfig.DEBUG)
            Log.d(tag, "onResume");

        RecyclerView cardlist = findViewById(R.id.cardList);
        if (cardlist.getLayoutManager() == null) {
// use a linear layout manager
            final RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
            cardlist.setLayoutManager(mLayoutManager);

        }
        final FloatingActionButton fab = findViewById(R.id.mainfab);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                Intent activity = new Intent(RSSReader.this, SelectCategory.class);
                startActivityForResult(activity, REQUEST_SELECT_FEED);

                SwipeRefreshLayout swipeRefresh = findViewById(R.id.swiperefresh);
                swipeRefresh.setRefreshing(false);
            });
        }

        boolean isFileUptodate = false;//for future use

        String feed_id = prefs.getString(PREF_FEED_ID, null);//lang
        if (feed_id == null) {
            String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, getString(R.string.default_feed_language_code));
            FeedsDB feedsDB = FeedsDB.getInstance();
            feed_id = feedsDB.getDefaultFeedId(pref_lang);//lang
        }
        File feedFile = new File(getCacheDir(), feed_id.concat(".cache"));
        if (feedFile.exists()) {
            long interval = Long.parseLong(prefs.getString(PREF_REFRESH_TIMER, "3600")) * 1000L;
            long age = System.currentTimeMillis() - feedFile.lastModified();
            if (age < interval)
                isFileUptodate = true;
            try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile))) {
                RSSFeed feed = (RSSFeed) is.readObject();
                renderFeed(feed, cardlist); latest_feed_id = feed_id;
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace(); // do nothing
            }
        }

//randomly, suggest user to do backup
        if (random_backup_hint.nextInt(20) == 0) {
            long ts = prefs.getLong(PREF_BACKUP_TIME, -1);
            if (ts == -1) {//backup never perfomed
                UserDB ft = UserDB.getInstance(this, prefs);
                if (!ft.getUserFeeds().isEmpty()) {//data to save?
                    ts = prefs.getLong(PREF_BACKUP_DIALOG, -1);
                    if (ts == -1) {//first time ?
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putLong(PREF_BACKUP_DIALOG, System.currentTimeMillis());
                        editor.apply();
                        new BackupHint_DF(prefs, mBackupRestore).show(getSupportFragmentManager(), "backuphint");
                    }
                }
            }
        }
    }

    final Random random_backup_hint = new Random();// generatore casuale per il backup hint

    private void renderFeed(RSSFeed feed, RecyclerView cardlist) {
        if (isFinishing()) return; // don't update UI if activity is finishing!

        if (feed == null) {
            setTitle("Unvailable feed", "Please select another feed", "Unvailable feed");
            return;
        }
        if (feed.size() == 0) {
            setTitle(feed.getTitle(), "Please select another feed", formatPublisher(feed.getTitle(), feed.getPublisherLink()));
            return;
        }

        setTitle(feed.getTitle(), feed.getPubDate(), formatPublisher(feed.getTitle(), feed.getPublisherLink()));

        List<CardContent> cards = new ArrayList<>();
        List<RSSItem> itemlist = feed.getAllItems();
        boolean smart_titles = prefs.getBoolean(PREF_SMART_TITLES, false);
        for (RSSItem item: itemlist)
            cards.add(new CardContent(item.getNicePubDate(this), item.getTitle(smart_titles)));

//        cardlist.setHasFixedSize(true);

        CardAdapter ca = (CardAdapter) cardlist.getAdapter();
        if (ca == null)
            cardlist.setAdapter(new CardAdapter(cards, cardlist, this, getResources()));
        else ca.replacesCards(cards);
    }

    private String formatPublisher(String title, String link) {
        return String.format(getString(R.string.msg_publisher), title, link);
    }

    private void showPreferences() {
        Intent settingsActivity;
        settingsActivity = new Intent(this, PreferencesFragXML.class);

        startActivityForResult(settingsActivity, REQUEST_CODE_PREFERENCES);

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swiperefresh);
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mBackupRestore.processRequestPermissionsResult(requestCode);
        } else {// permission denied
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_permission_denied), Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//			Log.i(tag,"result: "+resultCode);
        if (requestCode == REQUEST_CODE_PREFERENCES) {
            if (resultCode == RESULT_OK)  {
// some preferences changed, update timer and refresh UI
//theme changed - recreate()
                restart();
            }
        } else if (requestCode == REQUEST_SELECT_FEED) {
            if (resultCode == RESULT_OK)  {
                refresh(true);
            }
        } else if (!mBackupRestore.processActivityResult(requestCode, resultCode, data)) // pass result to BackupRestore
            super.onActivityResult(requestCode, resultCode, data);
    }


////////////////////////////////////////////
// classes to interact with RSSReaderService
////////////////////////////////////////////
    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {//receives messages from RSSReaderWorker
        @Override
        public void onReceive(Context context, Intent intent) {
            SwipeRefreshLayout swipeRefresh = findViewById(R.id.swiperefresh);
            swipeRefresh.setRefreshing(false);
            // Get extra data included in the Intent
            int what = intent.getIntExtra("what", 0);
            if (BuildConfig.DEBUG)
                Log.d(tag, "onReceive: "+what);
            switch (what) {
                case RSSReaderWorker.MSG_UPDATE:
                    Log.i(tag,"RSSReaderWorker.MSG_UPDATE received");
//        	        topbar.setBackgroundColor(Color_Fresh_Content);
                    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.primary)));

                    String feed_id = prefs.getString(PREF_FEED_ID, null);//lang
                    if (feed_id == null) {
                        String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, getString(R.string.default_feed_language_code));
                        FeedsDB feedsDB = FeedsDB.getInstance();
                        feed_id = feedsDB.getDefaultFeedId(pref_lang);//lang
                    }
                    File feedFile = new File(getCacheDir(), feed_id.concat(".cache"));
                    if (feedFile.exists())
                        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile))) {
                            RSSFeed feed = (RSSFeed) is.readObject();
                            RecyclerView cardlist = findViewById(R.id.cardList);
                            renderFeed(feed, cardlist); latest_feed_id = feed_id;
                        } catch (IOException e) {
                            Log.i(tag,"IOException in IncomingHandler");
                        } catch (ClassNotFoundException e) {
                            Log.i(tag,"ClassNotFoundException in IncomingHandler");
                        }
                    else Log.i(tag,"feedFile does not exists in IncomingHandler");
                    break;
                case RSSReaderWorker.MSG_ERROR:
                    int arg1 = intent.getIntExtra("arg1", 0);
                    int arg2 = intent.getIntExtra("arg2", 0);
                    Log.i(tag,"RSSReaderWorker.MSG_ERROR received: "+arg1);
                    if (!isFinishing()) {
                        int color_id = R.color.orange_primary;//defaul error color
                        switch (arg1) {
                            case DIALOG_RSS_ERROR_ID:
                                Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_rss_error), Snackbar.LENGTH_LONG).show();
                                break;
                            case DIALOG_HTTP_ERROR_ID:
                                Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_http_error) + arg2, Snackbar.LENGTH_LONG).show();
                                break;
                            case DIALOG_BAD_ANSWER_ID:
                                Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_bad_answer) + arg2, Snackbar.LENGTH_LONG).show();
                                break;
                            case DIALOG_CONN_ERROR_ID:
                                Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_connection_problem), Snackbar.LENGTH_LONG).show();
                                color_id = R.color.purple_accent;
                                break;
                            case DIALOG_INTERRUPTED_ID:
                                break;
                            case DIALOG_MIMETYPE_ERROR_ID:
                                Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_bad_mimetype), Snackbar.LENGTH_LONG).show();
                                break;
                            default:
                                Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_generic_problem), Snackbar.LENGTH_LONG).show();
                        }
                        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(color_id)));
                    }
                    break;
                case RSSReaderWorker.MSG_ALTERNATE:
//autodiscovery
                    Log.i(tag,"RSSReaderService.MSG_ALTERNATE received");
                    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.teal_primary)));
                    refresh(true);
                    break;
                default:
            }
        }
    };

    private void doPeriodicWork() {
        int refresh_timer = Integer.parseInt(prefs.getString(RSSReader.PREF_REFRESH_TIMER, "3600"));
        if (BuildConfig.DEBUG)
            Log.d(tag, "doPeriodicWork: "+ refresh_timer);
        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(RSSReaderWorker.class, refresh_timer, TimeUnit.SECONDS)//workmanager
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("RSSReader")
                .build();
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(uniqueWorkerName,  ExistingPeriodicWorkPolicy.REPLACE, periodicWorkRequest);//workmanager
    }

    @Override
    public String getMimeType() {
        return getString(R.string.backup_mimetype);
    }


    public String encodeFile() throws JSONException {
// write origin of backup in preferences		
        SharedPreferences.Editor editor = prefs.edit();
        final Calendar c = Calendar.getInstance();
        long timestamp = c.getTimeInMillis();
        editor.putLong(PREF_BACKUP_TIME, timestamp);
        editor.putString(PREF_BACKUP_ORIGIN, "encodeBackup");
        editor.apply();
// common part					
        JSONObject obj = new JSONObject();
        obj.put("version", Integer.valueOf(1)); // version 1 of backup file format
        obj.put("name", BuildConfig.APPLICATION_ID); // package name
        obj.put("timestamp", timestamp); // backup timestamp in milliseconds
// userfeeds part
        UserDB ft = UserDB.getInstance(this, prefs);
        obj.put("userfeeds", new JSONArray(ft.getUserFeeds()));
// usercats part
        obj.put("usercats", new JSONArray(ft.getUserCats()));
// prefs part
        Map<String, ?> pref = prefs.getAll();
        JSONObject jprefs = new JSONObject();
        if (pref != null) {
            for(Entry<String, ?> entry : pref.entrySet())
                if (!entry.getKey().equals("config_date") && !entry.getKey().equals(PREF_CLIENT_VERSION) &&
                        (entry.getValue() instanceof String || entry.getValue() instanceof Boolean
                                || entry.getValue() instanceof Integer || entry.getValue() instanceof Long))
                    jprefs.put(entry.getKey(), entry.getValue());
            obj.put("prefs", jprefs);
        }
        return obj.toString();
    }


    public boolean decodeFile(String content) throws JSONException {
        JSONObject obj = new JSONObject(content);
        Long version = obj.optLong("version");
        String name = obj.optString("name");
        Long timestamp = obj.optLong("timestamp");
//		Log.w(tag, "decodeBackup:  " + version);
        if (!name.equals(BuildConfig.APPLICATION_ID))
            return false;
// userfeeds part
        JSONArray juserfeeds = obj.optJSONArray("userfeeds");
        Log.w(tag, "decodeBackup:juserfeeds  " + juserfeeds);
        ArrayList<String[]> listUserFeeds = new ArrayList<>();
        if (juserfeeds != null) {
            for (int i = 0; i < juserfeeds.length(); i++) {
                JSONArray feed = juserfeeds.getJSONArray(i);
                if (feed.length() == UserDB.FEED_SIZE) {
                    String[] aFeed = new String[UserDB.FEED_SIZE];
                    for (int j = 0; j < UserDB.FEED_SIZE; j++) {
                        aFeed[j] = feed.getString(j);
                    }
                    listUserFeeds.add(aFeed);
                } else Log.e(tag, "decodeBackup: incorrect number of elements in " + feed);
            }
        }
// usercats part
        JSONArray jusercats = obj.optJSONArray("usercats");
        Log.w(tag, "decodeBackup:jusercats  " + jusercats);
        ArrayList<String[]> listUserCats = new ArrayList<>();
        if (jusercats != null) {
            for (int i = 0; i < jusercats.length(); i++) {
                JSONArray cat = jusercats.getJSONArray(i);
                if (cat.length() == UserDB.CAT_SIZE) {
                    String[] aCat = new String[UserDB.CAT_SIZE];
                    for (int j = 0; j < UserDB.CAT_SIZE; j++) {
                        aCat[j] = cat.getString(j);
                    }
                    listUserCats.add(aCat);
                } else Log.e(tag, "decodeBackup: incorrect number of elements in " + cat);
            }
        }
//sync to file if needed
        if (listUserFeeds.size() + listUserCats.size() > 0) {
            FeedsDB feedsDB = FeedsDB.getInstance();
            UserDB ft = UserDB.getInstance(this, prefs, feedsDB, listUserFeeds, listUserCats); //create feedstree with restored user feeds
            ft.synctoFile(this); //write restored user feeds to file
        }

// prefs part				
        JSONObject jprefs = obj.optJSONObject("prefs");
//		Log.w(tag, "decodeBackup:jprefs  " + jprefs);
        if (jprefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (Iterator<String> pref = jprefs.keys(); pref.hasNext();) {
                String mPref = pref.next();
                Object tPref = jprefs.get(mPref);
// String, Boolean, Long				
                if (tPref instanceof Boolean) {
                    editor.putBoolean(mPref, (Boolean) tPref);
                } else if (tPref instanceof String) {
                    editor.putString(mPref, (String) tPref);
                } else if (tPref instanceof Integer) {
                    editor.putInt(mPref, (Integer) tPref);
                } else if (tPref instanceof Long) {
                    editor.putLong(mPref, (Long) tPref);
                } else Log.w(tag, "decodeBackup, unexpected pref: " + mPref);
            }
            editor.apply();
            if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {//workaround per EGL error 12291
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            } else {
                restart();
            }
        }
        return true;
    }

    private void stopTTS() {//speech
        if (BuildConfig.DEBUG)
            Log.d(tag, "stopTTS");
        if (ttsplay != null)
            ttsplay.setIcon(R.drawable.ic_play_arrow_white_36dp);//play
        try {
            mTts.stop();
        } catch (IllegalStateException e) {
            // Do nothing: TTS engine is already stopped.
        }
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int result = am.abandonAudioFocus(this); // ignore result
        }
    }

    public void onAudioFocusChange(int focusChange) {//af
        if (BuildConfig.DEBUG)
            Log.d(tag, "onAudioFocusChange: " + focusChange);
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
// Pause playback
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
// Resume playback
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
// Stop playback
                mTts.stop();
                break;
        }
    }

    public static class BackupHint_DF extends AppCompatDialogFragment { 
        SharedPreferences prefs;
        FileManager mBackupRestore;

        public BackupHint_DF() {	// required empty constructor
        }

        public BackupHint_DF(SharedPreferences prefs, FileManager mBackupRestore) {
            this.prefs = prefs;
            this.mBackupRestore = mBackupRestore;
        }


        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setMessage(getString(R.string.backup_hint))
                    .setPositiveButton(getString(R.string.backup), (dialog, id) -> doBackup(mBackupRestore))
                    .setNeutralButton(getString(android.R.string.cancel), (dialog, id) -> {
//do nothing
                    });
            return builder.create();
        }
    }

    public static String cleanupContent(String content, boolean tts) {
        final String li_header = (tts ? "\n" : "\n- ");
        // needed as Html class does not support tags <li>, <ul>, <ol>...
        return Html.fromHtml(content.replaceAll("<script.+?</script>", ""), null, (opening, tag, output, xmlReader) -> {
            if (opening) {
                if (tag.equalsIgnoreCase("li"))
                    output.append(li_header);
                else if (tag.equalsIgnoreCase("hr") || tag.equalsIgnoreCase("br") || tag.equalsIgnoreCase("dt")) // nota: dt deve causare una nuova linea
                    output.append("\n");
            } else if ((tag.equalsIgnoreCase("ul") || tag.equalsIgnoreCase("ol") ||
                    tag.equalsIgnoreCase("dl")|| tag.equalsIgnoreCase("dt")))
                output.append("\n");
        }).toString().replace("\uFFFC", "");
    }

    public static class Publisher_DF extends AppCompatDialogFragment { //df

        public Publisher_DF() {    // required empty constructor
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            // Inflate the about message contents
            View infoView = inflater.inflate(R.layout.publisher, null, false);
            TextView txtView = infoView.findViewById(R.id.publisher_info);
            txtView.setText(Html.fromHtml(message_publisher));
            // When linking text, force to always use default color. This works
            // around a pressed color state bug.
            int defaultColor = txtView.getTextColors().getDefaultColor();
            txtView.setTextColor(defaultColor);
            txtView.setMovementMethod(LinkMovementMethod.getInstance());

            return new AlertDialog.Builder(getActivity())
//                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(getString(R.string.menu_publisher))
                    .setView(infoView)
                    .setNeutralButton(android.R.string.cancel, (dialog, id) -> dialog.cancel()).create();
        }
    }

    private void selectCategoryDialogImport() {//zzimport custom dialog to select target category to import OPML files
        final ArrayList<String> catList = new ArrayList<>();
        final String[] categories = getResources().getStringArray(R.array.categories_list);//localized names
        for (int k = 0; k < FeedsDB.categories.length; k++)
            catList.add(categories[k]);

        UserDB udb = UserDB.getInstance(this, prefs);
        for (int k = 0; k < udb.getUserCats().size(); k++)//user categories
            catList.add(udb.getUserCat(k)[0]);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sel_cat_label)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setSingleChoiceItems(catList.toArray(new String[0]), -1, (dialog, which) -> {
                    Intent listActivity = new Intent(getBaseContext(), ListFeeds.class);
                    Bundle b = new Bundle();
                    String cat;
                    if (which < FeedsDB.categories.length)
                        cat = FeedsDB.categories[which][2];
                    else cat = udb.getUserCat(which - FeedsDB.categories.length)[2];
                    b.putString("category", cat);
                    b.putBoolean("import", true);
                    listActivity.putExtra(ID_CATEGORY, b);

                    startActivityForResult(listActivity, REQUEST_CODE_PREFERENCES);
                    dialog.dismiss();
                }).show();
    }

/////////////////////////////////////////
// adapter

    public static class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

        private List<CardContent> cardList;
        private final RecyclerView cardview;
        private final RSSReader rssreader;
        private final int background;
        private final int genericcolor;
        private final int hyperlink;
        private final int textcolor;


        CardAdapter(List<CardContent> cards, RecyclerView cardview, RSSReader rssreader, Resources res) {
            cardList = cards;
            this.cardview = cardview;
            this.rssreader = rssreader;
            int[] colors = getThemeColors(rssreader.prefs, res);
            background = colors[0];
            textcolor = colors[1];
            hyperlink = colors[2];
            genericcolor = colors[3];
        }

        void replacesCards(List<CardContent> cards) {
            cardList = cards;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return cardList.size();
        }

        @Override
        public void onBindViewHolder(CardViewHolder contactViewHolder, int i) {
            CardContent ci = cardList.get(i);
            contactViewHolder.vTitle.setText(ci.title);
            contactViewHolder.vContent.setText(Html.fromHtml(ci.content));// inserito Html.fromHtml per gestire ventuali &apos; e altri
//indagare gestione temi
            contactViewHolder.vTitle.setTextColor(genericcolor);
            contactViewHolder.vTitle.setBackgroundColor(background);
            contactViewHolder.vContent.setTextColor(textcolor);
            contactViewHolder.vContent.setBackgroundColor(background);
        }

        @NonNull
        @Override
        public CardViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.cardview, viewGroup, false);
            itemView.setOnClickListener(v -> {
                int position = cardview.getChildAdapterPosition(v);
                Log.i(tag, "item [" + position + "] clicked");

                if (latest_feed_id == null) {
                    Log.i(tag, "feed_id = null !");
                    return;
                }
//    	 Log.i(tag,"description length: " + feed.getItem(position).getDescription().length());

                Intent itemintent = new Intent(rssreader, ShowItem.class);
                boolean text_mode;
                if (rssreader.prefs.getString(PREF_DOWNLOAD_IMAGES, "any").equals("wifi")) {//images can be downloaded only with wifi connectivity?
                    ConnectivityManager cm = (ConnectivityManager)rssreader.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo ni = cm.getActiveNetworkInfo();
                    text_mode = (ni == null) || (ni.getType() != ConnectivityManager.TYPE_WIFI);
                } else text_mode = false;

                itemintent.putExtra("light_theme", !isDarkColor(background));
                itemintent.putExtra("position", position);
                itemintent.putExtra("external_browser", rssreader.prefs.getBoolean(PREF_USE_EXTERNAL_BROWSER, false));
                String pref_lang = rssreader.prefs.getString(PREF_FEEDS_LANGUAGE, rssreader.getString(R.string.default_feed_language_code));
                itemintent.putExtra("default_language", pref_lang);
                Bundle b = new Bundle();
                String style = String.format(style_tpl, (hyperlink & 0xFFFFFF),
                        (background & 0xFFFFFF), (textcolor & 0xFFFFFF), (genericcolor & 0xFFFFFF), (genericcolor & 0xFFFFFF));
                int fontsize = rssreader.prefs.getInt(PREF_FONTSIZE, 16);// 16 is the default font size
// note: padding inserted due to bug in android 4.2 and later (in una lista con oltre 10 elementi sparisce la prima cifra, per cui l'utente vede 6 7 8 9 e poi 0 invece di 10 ! Problema legato al webview all'interno del viewpager
                // API 19
                style += " body {font-size:"+ (Math.round(fontsize / 1.6) / 10.0) +"em;padding:.6em}";

                b.putString("html_style", style);
                b.putInt("background", background);
                b.putBoolean("text_mode", text_mode);
                b.putString("feed_id", latest_feed_id);//latest_feed_id sostituisce feed, per evitare l'eccezione TransactionTooLargeException in certi casi (es: https://www.7iber.com/)

                itemintent.putExtra(ID_ITEM, b);
                rssreader.startActivity(itemintent);

                SwipeRefreshLayout swipeRefresh = rssreader.findViewById(R.id.swiperefresh);
                swipeRefresh.setRefreshing(false);
            });
            return new CardViewHolder(itemView);
        }

        public static final class CardViewHolder extends RecyclerView.ViewHolder {

            final TextView vTitle;
            final TextView vContent;

            CardViewHolder(View v) {
                super(v);
                vTitle = v.findViewById(R.id.title);
                vContent = v.findViewById(R.id.content);
            }
        }
    }

}