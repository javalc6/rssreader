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
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.RequiresApi;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Stack;

import livio.rssreader.backend.RSSFeed;
import livio.rssreader.backend.RSSItem;
import livio.rssreader.backend.TTSEngine;

public final class ShowItem extends AppCompatActivity implements AudioManager.OnAudioFocusChangeListener {
	private final String tag = "ShowItem";
	private String language;

    private SmartPager smartPager;
    private ImageButton backbutton;
    private ImageButton fwdbutton;
    private boolean rtl; 
    private boolean use_external_browser;

    private TTSEngine mTts;
    private TTSEngine.TtsState tts_play;//speech

    private static final String utteranceId_oneshot = "oneshot";
    protected static final String utteranceId_first = "first";
    protected static final String utteranceId_interim = "interim";
    private static final String utteranceId_last = "last";

    private static final String SAVE_currentItem_ID = "currentitem";
    private static final String SAVE_language_ID = "language";
    private static final String SAVE_msg_ID = "msg";

    /** Called to save instance state: put critical variables here! */
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        smartPager.onSaveInstanceState(outState);
        int position = smartPager.getCurrentItem();
        if (rtl)
            position = smartPager.getCount() - 1 - position;
        outState.putInt(SAVE_currentItem_ID, position);
        outState.putString(SAVE_language_ID, language);
    }

	@SuppressLint("NewApi")
	public void onCreate(Bundle savedInstanceState) {
        Intent startingIntent = getIntent();
        boolean light_theme = startingIntent.getBooleanExtra("light_theme", false);
        if (light_theme) { // light background
            setTheme(R.style.ThemeLightHiContrast);
        } else { // dark background
            setTheme(R.style.ThemeHiContrast);
        }

        super.onCreate(savedInstanceState);
   	 	Log.i(tag, "onCreate");

        setContentView(R.layout.showitem);
        rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        use_external_browser = startingIntent.getBooleanExtra("external_browser", false);
        ActionBar t = getSupportActionBar();
		if (t != null) {
			t.setDisplayHomeAsUpEnabled(true);
		}

        backbutton = findViewById(R.id.backbutton);
        fwdbutton = findViewById(R.id.fwdbutton);

        try {
            smartPager = new SmartPager(findViewById(R.id.smartpager), savedInstanceState, this,
                    startingIntent.getBundleExtra(RSSReader.ID_ITEM));
        } catch (IOException e) {
            Log.i(tag,"IOException reading feedFile in SmartPager");
            finish();//exit!
            return;
        }


        mTts = new TTSEngine(this, new UtteranceProgressListener() {
            @Override
            public void onDone(String utteranceId) {
//                        Log.i(tag, "onDone:" + utteranceId);
                if (utteranceId_last.equals(utteranceId) || utteranceId_oneshot.equals(utteranceId)) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int result = am.abandonAudioFocus(ShowItem.this); // ignore result
                    invalidateOptionsMenu(); //play
                }
            }

            @Override
            public void onError(String utteranceId) {
//                        Log.i(tag, "onError:" + utteranceId);
                if (utteranceId_last.equals(utteranceId) || utteranceId_oneshot.equals(utteranceId)) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int result = am.abandonAudioFocus(ShowItem.this); // ignore result
                    invalidateOptionsMenu(); //play
                }
            }

            @Override
            public void onStart(String utteranceId) {
//                        Log.i(tag, "onStart:" + utteranceId);
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
//attiviamo bottombox finchè non termina la voce, usiamo startAnimation() per sovrascrivere l'eventuale animazione ongoing
                                    bottombox.startAnimation(new AlphaAnimation(1, 1));
                                }
                            }//public void run() {
                        });
                    }
*/
            }
        });

        int position;
        if (savedInstanceState == null) {
            String default_language = startingIntent.getStringExtra("default_language");
            language = smartPager.feed.getLanguage(default_language);
//            Log.d(tag, "language:" + language);
            position = smartPager.getCount() - 1 - startingIntent.getIntExtra("position", 0);//ShowItem shows items in reversed order compared to RSSReader
        } else {
            position = savedInstanceState.getInt(SAVE_currentItem_ID);
            language = savedInstanceState.getString(SAVE_language_ID);
        }
//        Log.d(tag, "position:" + position);
        smartPager.setCurrentItem(rtl ? smartPager.getCount() - 1 - position : position, false);
    }

    public void onNewIntent (Intent intent) {
        super.onNewIntent(intent);
        Log.i(tag, "onNewIntent, intent: " + intent);
    }


    @Override
    public void onPause() {
		stopTTS();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }

        if (smartPager != null)
            smartPager.clearWebViews(true);
    //        	smartPager.getHomeView().clearCache(true);
        super.onDestroy();
    }

    private void stopTTS() {//speech
        if (ttsplay != null)
            ttsplay.setIcon(R.drawable.ic_play_arrow_white_36dp);//play
        try {
            mTts.stop();
        } catch (IllegalStateException e) {
            // Do nothing: TTS engine is already stopped.
        }
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = am.abandonAudioFocus(this); // ignore result
    }

    public void onAudioFocusChange(int focusChange) {//af
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
// Pause playback
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
// Resume playback
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
// Stop playback
                stopTTS();
                break;
        }
    }


    private MenuItem ttsplay;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.showitem_menu, menu);
        menu.findItem(R.id.menu_prev).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.findItem(R.id.menu_next).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        ttsplay = menu.findItem(R.id.menu_play);
        ttsplay.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.findItem(R.id.menu_share).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (smartPager == null)
            return super.onPrepareOptionsMenu(menu);

        MenuItem play = menu.findItem(R.id.menu_play);//play
        if (play != null)
            play.setIcon(R.drawable.ic_play_arrow_white_36dp);

        int n_items = smartPager.getCount();
        int pos = smartPager.getCurrentItem();
        if (rtl)
            pos = n_items - 1 - pos;

        if (pos == 0) {// |<
            MenuItem prev = menu.findItem(R.id.menu_prev);
            if (prev != null)
                prev.setIcon(R.drawable.ic_first_page_white_36dp);
        } else if (pos == 1) {// <
            MenuItem prev = menu.findItem(R.id.menu_prev);
            if (prev != null)
                prev.setIcon(R.drawable.ic_keyboard_arrow_left_white_36dp);
        } else if (pos == n_items - 2) {// >
            MenuItem next = menu.findItem(R.id.menu_next);
            if (next != null)
                next.setIcon(R.drawable.ic_keyboard_arrow_right_white_36dp);
        } else if (pos == n_items - 1) {// >|
            MenuItem next = menu.findItem(R.id.menu_next);
            if (next != null)
                next.setIcon(R.drawable.ic_last_page_white_36dp);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            setResult(RESULT_CANCELED);
            stopTTS();
            finish();
            return true;
        } else if (itemId == R.id.menu_prev) {
            backpage();
            return true;
        } else if (itemId == R.id.menu_next) {
            fwdpage();
            return true;
        } else if (itemId == R.id.menu_play) {
            if (mTts.isSpeaking()) {
                stopTTS();
                return true;
            }
            if (smartPager.feed != null) {
                RSSItem article = smartPager.feed.getItem(smartPager.getCount() - 1 - smartPager.getCurrentItem());//ShowItem shows items in reversed order compared to RSSReader
                if (article != null) {
                    String content = article.getDescription();
                    if ((content != null) && mTts.checkTTS(language)) {
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
// Request audio focus for playback
                        int result = am.requestAudioFocus(this,
                                AudioManager.STREAM_MUSIC,// Use the music stream.
                                AudioManager.AUDIOFOCUS_GAIN);// Request permanent focus.

                        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            // Start playback.
                        }

                        String[] segments = RSSReader.cleanupContent(content, true).split("\n+");
                        if (mTts.speakSegments(segments))
                            item.setIcon(R.drawable.ic_stop_white_36dp);//play
                    }
                }
            }
            return true;
        } else if (itemId == R.id.menu_share) {
            try {
                if (smartPager.feed != null) {
                    RSSItem article = smartPager.feed.getItem(smartPager.getCount() - 1 - smartPager.getCurrentItem());//ShowItem shows items in reversed order compared to RSSReader
                    if (article != null) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);// FLAG_ACTIVITY_NEW_DOCUMENT sostituisce FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
                        intent.putExtra(Intent.EXTRA_SUBJECT, article.getTitle(false)); //TODO Html.fromHtml() should be used?
                        intent.putExtra(Intent.EXTRA_TEXT, RSSReader.cleanupContent(article.getDescription() + "\n\n" + article.getLink(), false));
                        startActivity(Intent.createChooser(intent, getString(R.string.menu_share_label)));
                    }
                }
            } catch (ActivityNotFoundException e) {
                Log.d(tag, "ActivityNotFoundException: " + e.getMessage());
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void backpage() {
        movepage(rtl);
    }
    public void backpage(View view) {
        backpage();
    }

    private void fwdpage() {
        movepage(!rtl);
    }
    public void fwdpage(View view) {
        fwdpage();
    }

    private void movepage(boolean up) {
        int pos = smartPager.getCurrentItem();
        if (up) {
            if (pos < smartPager.getCount() - 1) {
                smartPager.setCurrentItem(pos + 1, true); // scroll
            }
        } else {
            if (pos > 0) {
                smartPager.setCurrentItem(pos - 1, true); // scroll
            }
        }
        invalidateOptionsMenu();
    }

    protected class WebViewPlus extends WebView { // extended web view
        String _msg;
        final String style;

        WebViewPlus(Bundle savedInstanceState, Context ctx, int background, String style, boolean text_mode) {
            super(ctx); // workaround for issue: android.view.WindowManager$BadTokenException: Unable to add window -- token null is not for an application
//    		super(getApplicationContext()); old code
            this.style = style;
            WebSettings settings = getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDatabaseEnabled(false); // work-around for SQLiteFullException: database or disk is full
            settings.setLoadsImagesAutomatically(!text_mode);
            setWebViewClient(new WebViewClient() {
                 @Override
                 public boolean shouldOverrideUrlLoading(WebView view, String url) {
                     return overrideUrlLoading(url);//twin
                 }
                 @Override
                 public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//                     if (!request.hasGesture()) return false;<--attenzione ad abilitarla, può dare problemi
                     return overrideUrlLoading(request.getUrl().toString());//twin
                 }

                @RequiresApi(api = Build.VERSION_CODES.M)
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {//API >= 23
                    if (request.isForMainFrame()) {
                        String errorMsg = "<b>" + getString(R.string.msg_error) + " " +error.getDescription() + "</b><br/><br/>" + getString(R.string.msg_connection_problem);
                        view.loadDataWithBaseURL("file:///android_asset/", "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><style type=\"text/css\">"
                                +style+"</style></head><body>" + errorMsg + "</body></html>", "text/html", "utf-8", null);
                    }
                }

            });
            setBackgroundColor(background);  // required
            if (savedInstanceState != null) {
                loadDataWithBaseURL(savedInstanceState.getString(SAVE_msg_ID), "text/html", "utf-8");
            }
        }

        boolean overrideUrlLoading(String url) {
            if (url.startsWith("http:")||url.startsWith("https:")) {
                if (use_external_browser) {
                    Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    try {
                        startActivity(myIntent);
                    } catch (ActivityNotFoundException e) {
                        Log.d(tag, "ActivityNotFoundException: " + e.getMessage());
                    }
                    return true;
                } else return false;
            } else Log.d(tag, "overrideUrlLoading, cannot handle: "+url);
            return true; // nothing to do ???
        }

        void saveInstanceState(Bundle outState) {
            outState.putString(SAVE_msg_ID, _msg);
        }

        private void loadDataWithBaseURL(String msg, String type, String coding) {
//        	msg = msg.replaceAll("<math.+?</math>", ""); // math disabled
            _msg = msg; // save message in case of theme will be changed

//            loadDataWithBaseURL("file:///android_asset/", renderHTML(msg, getStyle(prefs, getResources()), getContext(), prefs.getBoolean(Preferences.PREF_UPPERCASE, false)), type, coding, null);

            loadDataWithBaseURL("file:///android_asset/", "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><style type=\"text/css\">"
                    +style+"</style></head><body>" + msg + "</body></html>", type, coding, null);
        }


    }


    // http://developer.android.com/reference/android/support/v4/view/PagerAdapter.html
    class SmartPager extends PagerAdapter {
        final Stack<WebViewPlus> idlelist = new Stack<>();
        final ViewPager viewPager;
        WebViewPlus primaryItem;
        final WebViewPlus homeView; // "home page"
        final Context context;
        final int n_items;
        final Bundle payload;
        private RSSFeed feed;
        final boolean text_mode;
        final int background;

        SmartPager(ViewPager vpager, Bundle savedInstanceState, Context ctx, Bundle payload) throws IOException {
            viewPager = vpager;
            this.payload = payload;
            text_mode = payload.getBoolean("text_mode", false);
            String feed_id = payload.getString("feed_id");
            File feedFile = new File(getCacheDir(), feed_id.concat(".cache"));
            try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile))) {
                feed = (RSSFeed) is.readObject();
            } catch (ClassNotFoundException e) {
                Log.d(tag,"ClassNotFoundException reading feedFile in SmartPager");
            }

            background = payload.getInt("background");
            n_items = feed != null ? feed.size() : 0;
            if (n_items == 0)//inutile procedere se non c'è nulla da visualizzare, problema dovuto ad eventuali corse critiche
                throw new IOException("SmartPager constructor failed due to n_items = 0");
            vpager.setAdapter(this);
//            vpager.addOnPageChangeListener(this);
            context = ctx;
            if (savedInstanceState != null) {
                homeView = new WebViewPlus(savedInstanceState, ctx, background, payload.getString("html_style"), text_mode);
                viewPager.setCurrentItem(savedInstanceState.getInt(SAVE_currentItem_ID), false);
            } else {
                homeView = new WebViewPlus(null, ctx, background, payload.getString("html_style"), text_mode);
                homeView.loadDataWithBaseURL("", "text/html", "utf-8"); //empty page - needed?
            }
        }

        @Override
        public int getCount() {
            return n_items;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            WebViewPlus wv;
            if (position == 0) // page 0
                wv = homeView;
            else if (idlelist.empty())
                wv =  new WebViewPlus(null, context, payload.getInt("background"), payload.getString("html_style"), text_mode);
            else {
                wv = idlelist.pop();
            }
            RSSItem item = feed.getItem(rtl ? position : n_items - 1 - position);//ShowItem shows items in reversed order compared to RSSReader
            if (item != null) {
                wv.loadDataWithBaseURL(item.getPubDate(context)
                        + " <a href=\""+item.getLink()+"\">"+getString(R.string.full_story)+"</a><br><br>" +
                        (text_mode ? Html.fromHtml(item.getDescription()) : item.getDescription()), "text/html", "utf-8");
            } else {
                wv.loadDataWithBaseURL(getString(R.string.msg_error), "text/html", "utf-8");
                Log.i(tag, "instantiateItem, item = null !");
            }

            container.addView(wv);
            return wv;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object view) {
            container.removeView((WebViewPlus) view);
            if (position != 0) {// page > 0
                ((WebViewPlus) view)._msg = "";
                ((WebViewPlus) view).loadUrl("about:blank");// clean-up view to re-cycle
                ((WebViewPlus) view).setBackgroundColor(background);  // required

                idlelist.push((WebViewPlus) view);
            }
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object view) {
//            Log.d("setPrimaryItem", "pos:"+position);
            primaryItem = (WebViewPlus) view;
            ActionBar t = getSupportActionBar();
            RSSItem item = feed.getItem(rtl ? position : n_items - 1 - position);//ShowItem shows items in reversed order compared to RSSReader
            if ((t != null) && (item != null))
                t.setTitle(Html.fromHtml(item.getTitle(false)));
            invalidateOptionsMenu();//<--necessario per aggiornare le icone < > nell'action bar in alto
        }

        int getCurrentItem() {
            return viewPager.getCurrentItem();
        }

        void setCurrentItem(int item, boolean smoothScroll){
//            Log.d("setCurrentItem", "pos:"+item);
            int pos = item; 
            if (rtl)
                pos = smartPager.getCount() - 1 - pos;
            if ((pos >= 0) && (pos < smartPager.getCount() - 1)) {
                fwdbutton.setAlpha(1f);
                fwdbutton.animate().alpha(0f).setDuration(6000).start();
            }
            if (pos > 0) {
                backbutton.setAlpha(1f);
                backbutton.animate().alpha(0f).setDuration(6000).start();
            }
            viewPager.setCurrentItem(item, smoothScroll);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        void onSaveInstanceState(Bundle outState) {
            homeView.saveInstanceState(outState); // handling webview persistence
        }

        @Override
        public void finishUpdate(ViewGroup container) {}


        @Override
        public void startUpdate(ViewGroup container) {}


        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {}

        @Override
        public Parcelable saveState() {
            return null;
        }

        void clearWebViews(boolean includeDiskFiles) {		// clean-up
            int nviews = viewPager.getChildCount();
            for (int i=0; i < nviews; i++) {
                ((WebViewPlus)viewPager.getChildAt(i)).clearCache(includeDiskFiles);
            }
        }

    }

}
