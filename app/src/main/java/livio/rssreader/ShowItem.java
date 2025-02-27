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
import android.speech.tts.UtteranceProgressListener;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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

import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback;
import livio.rssreader.backend.RSSFeed;
import livio.rssreader.backend.RSSItem;
import livio.rssreader.backend.TTSEngine;

public final class ShowItem extends AppCompatActivity implements AudioManager.OnAudioFocusChangeListener {
	private final String tag = "ShowItem";
	private String language;

    private SmartPager smartPager;
    private ImageButton backbutton;
    private ImageButton fwdbutton;
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        smartPager.onSaveInstanceState(outState);
        outState.putInt(SAVE_currentItem_ID, smartPager.getCurrentItem());
        outState.putString(SAVE_language_ID, language);
    }

	@SuppressLint("NewApi")
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
   	 	Log.i(tag, "onCreate");
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {//zzedge-2-edge
            EdgeToEdge.enable(this);//importante: deve essere eseguito prima di setContentView()
        }

        setContentView(R.layout.showitem);

        Intent startingIntent = getIntent();
        use_external_browser = startingIntent.getBooleanExtra("external_browser", false);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
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
            position = smartPager.getItemCount() - 1 - startingIntent.getIntExtra("position", 0);//ShowItem shows items in reversed order compared to RSSReader
        } else {
            position = savedInstanceState.getInt(SAVE_currentItem_ID);
            language = savedInstanceState.getString(SAVE_language_ID);
        }
//        Log.d(tag, "position:" + position);
        smartPager.setCurrentItem(position, false);
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

        int n_items = smartPager.getItemCount();
        int pos = smartPager.getCurrentItem();

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
                RSSItem article = smartPager.feed.getItem(smartPager.getItemCount() - 1 - smartPager.getCurrentItem());//ShowItem shows items in reversed order compared to RSSReader
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
                        if ((segments.length > 0) && mTts.speakSegments(segments))
                            item.setIcon(R.drawable.ic_stop_white_36dp);//play
                    }
                }
            }
            return true;
        } else if (itemId == R.id.menu_share) {
            try {
                if (smartPager.feed != null) {
                    RSSItem article = smartPager.feed.getItem(smartPager.getItemCount() - 1 - smartPager.getCurrentItem());//ShowItem shows items in reversed order compared to RSSReader
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
        movepage(false);
    }
    public void backpage(View view) {
        backpage();
    }

    private void fwdpage() {
        movepage(true);
    }
    public void fwdpage(View view) {
        fwdpage();
    }

    private void movepage(boolean up) {
        int pos = smartPager.getCurrentItem();
        if (up) {
            if (pos < smartPager.getItemCount() - 1) {
                smartPager.setCurrentItem(pos + 1, true); // scroll
            }
        } else {
            if (pos > 0) {
                smartPager.setCurrentItem(pos - 1, true); // scroll
            }
        }
        invalidateOptionsMenu();
    }

    public boolean dispatchKeyEvent(KeyEvent event) {//15-02-2025, added DPAD support
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    backpage();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    fwdpage();
                    return true;
                default:
//                    Log.d(tag, "key:" + keyCode);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public class WebViewPlus extends WebView { // extended web view
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
    class SmartPager extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final ViewPager2 viewPager;
        final Context context;
        final int n_items;
        final Bundle payload;
        private RSSFeed feed;
        final boolean text_mode;
        final int background;

        SmartPager(ViewPager2 vpager, Bundle savedInstanceState, Context ctx, Bundle payload) throws IOException {
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
            vpager.registerOnPageChangeCallback(new OnPageChangeCallback() {
                @Override
                public void onPageSelected(int pos) {
                    super.onPageSelected(pos);
//                    Log.d("onPageSelected", "pos:"+pos);
                    RSSItem rssitem = feed.getItem(n_items - 1 - pos);//ShowItem shows items in reversed order compared to RSSReader
                    ActionBar t = getSupportActionBar();
                    if ((t != null) && (rssitem != null)) {
                        t.setTitle(Html.fromHtml(rssitem.getTitle(false)));
                    }
                    invalidateOptionsMenu();//<--necessario per aggiornare le icone < > nell'action bar in alto
                }
            });
            context = ctx;
            if (savedInstanceState != null) {
                viewPager.setCurrentItem(savedInstanceState.getInt(SAVE_currentItem_ID), false);
            }
        }


        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {//17-10-2021: ViewPager2
// workaround for issue https://issuetracker.google.com/issues/123006042
// workaround is a simplified version of https://github.com/android/views-widgets-samples/blob/master/ViewPager2/app/src/main/java/androidx/viewpager2/integration/testapp/NestedScrollableHost.kt
// workaround limitation: gli scroll orizzontali sono passati direttamente al viewpager che bypassano il webviewplus, ok per help e showitem, meno bene per il dizionario offline
            recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                int lastX;
                int lastY;
                int touchSlop;
                boolean waitingFirst;
                boolean disallowInterceptTouch;

                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
//                    Log.d(tag, "onInterceptTouchEvent+"+e.getAction());
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = (int) e.getX();
                            lastY = (int) e.getY();
                            touchSlop = ViewConfiguration.get(context).getScaledEdgeSlop();
                            waitingFirst = true;
                            disallowInterceptTouch = false;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            int dx = Math.abs((int) e.getX() - lastX);
                            int dy = Math.abs((int) e.getY() - lastY);
                            int scaledDx = dx / 2;// assuming ViewPager2 touch-slop is 2x touch-slop of child
                            if (waitingFirst && (scaledDx > touchSlop || dy > touchSlop)) {
                                waitingFirst = false;
                                if (dy > scaledDx) {
                                    // Gesture is perpendicular, disallow all parents to intercept
                                    disallowInterceptTouch = true;
                                }
                            }
                            if (disallowInterceptTouch)
                                rv.requestDisallowInterceptTouchEvent(true);
                            break;
                    }
                    return false;
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                }
            });
        }


        int getCurrentItem() {
            return viewPager.getCurrentItem();
        }

        void setCurrentItem(int item, boolean smoothScroll){
//            Log.d("setCurrentItem", "pos:"+item);
            int pos = item; 
            if ((pos >= 0) && (pos < smartPager.getItemCount() - 1)) {
                fwdbutton.setAlpha(1f);
                fwdbutton.animate().alpha(0f).setDuration(6000).start();
            }
            if (pos > 0) {
                backbutton.setAlpha(1f);
                backbutton.animate().alpha(0f).setDuration(6000).start();
            }
            viewPager.setCurrentItem(item, smoothScroll);

        }

        void onSaveInstanceState(Bundle outState) {
//            homeView.saveInstanceState(outState); // handling webview persistence
        }

        void clearWebViews(boolean includeDiskFiles) {		//17-10-2021: ViewPager2
            try {
                RecyclerView rv = ((RecyclerView) viewPager.getChildAt(0));
                if (rv != null) {
                    ViewHolder vh = (ViewHolder) rv.findViewHolderForAdapterPosition(0);
                    if (vh != null) {
                        vh.getWebViewPlus().clearCache(includeDiskFiles);
                        Log.d(tag, "successful clearWebViews");
                    }
                }
            } catch (RuntimeException re) {
                Log.d(tag, "RuntimeException in clearWebViews", re);
//ignore it, we were just trying to clear the webview cache
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {//17-10-2021: ViewPager2
            WebViewPlus wvp = new WebViewPlus(null, context, payload.getInt("background"), payload.getString("html_style"), text_mode);
            wvp.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return new ViewHolder(wvp);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {//17-10-2021: ViewPager2
//            Log.d("onBindViewHolder", "pos:"+position);
            WebViewPlus wv = ((ViewHolder) holder).getWebViewPlus();
            RSSItem item = feed.getItem(n_items - 1 - position);//ShowItem shows items in reversed order compared to RSSReader
            if (item != null) {
                wv.loadDataWithBaseURL(item.getPubDate(context)
                        + " <a href=\""+item.getLink()+"\">"+getString(R.string.full_story)+"</a><br><br>" +
                        (text_mode ? Html.fromHtml(item.getDescription()) : item.getDescription()), "text/html", "utf-8");
            } else {
                wv.loadDataWithBaseURL(getString(R.string.msg_error), "text/html", "utf-8");
                Log.i(tag, "instantiateItem, item = null !");
            }

        }

        @Override
        public int getItemCount() {//17-10-2021: ViewPager2
            return n_items;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {//17-10-2021: ViewPager2
            private final WebViewPlus wvp;

            public ViewHolder(View v) {
                super(v);
                wvp = (WebViewPlus) v;
            }

            public WebViewPlus getWebViewPlus() {
                return wvp;
            }
        }
    }

}
