package livio.rssreader;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import tools.FormFactorUtils;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.annotation.SuppressLint;
import android.widget.LinearLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Locale;


/*
ShowHelp come intent richiede il parametro 'help' che rappresenta il nome del file di aiuto da aprire ed eventualmente la sezione da aprire separata da /

Navigazione interna:

per selezionare voce 'bookmark'
help:bookmark

le seguenti modalità devono ancora essere implementate:
per selezionare il file 'dict'
help:/dict

per selezionare il file 'dict', voce 'bookmark'
help:/dict/bookmark


 */

public final class ShowHelp extends AppCompatActivity {
    private static final String tag = "ShowHelp";
    private static final String BASE_DIRECTORY_HELP = "help/";

    public static final String HELP_SCHEME = "help:";	//zzhelp, support for help screens (proof of concept)

    private SmartPager smartPager;
    private ImageButton backbutton;
    private ImageButton fwdbutton;

    private String help_filename;
    private final ArrayList<String> help_content = new ArrayList<>();

    private static final String SAVE_currentItem_ID = "currentitem";
    private static final String SAVE_helpfilename_ID = "filename";
//    private static final String SAVE_msg_ID = "msg";//serve a qualcosa in ShowHelp ? eliminarlo in futuro

    /** Called to save instance state: put critical variables here! */
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        int position = smartPager.getCurrentItem();
        outState.putInt(SAVE_currentItem_ID, position);
        outState.putString(SAVE_helpfilename_ID, help_filename);
    }

    @SuppressLint("NewApi")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(tag, "onCreate");
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {//zzedge-2-edge
            EdgeToEdge.enable(this);//importante: deve essere eseguito prima di setContentView()
        }

        setContentView(R.layout.showhelp);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar t = getSupportActionBar(); //ab
        if (t != null) {//ab
            t.setDisplayHomeAsUpEnabled(!FormFactorUtils.isArc(this)); //show back arrow in actionbar on Android devices, but not on Chromebook devices
        }

        backbutton = findViewById(R.id.backbutton);
        fwdbutton = findViewById(R.id.fwdbutton);

        int position;
        if (savedInstanceState == null) {
            position = 0;//default
            help_filename = getIntent().getStringExtra("help");
            if (help_filename == null)
                help_filename = getString(R.string.helpfile);//default help file
            int idx = help_filename.indexOf('/');
            String selector = null, anchor = null;
            if (idx != -1) {
                selector = help_filename.substring(idx + 1);
                help_filename = help_filename.substring(0, idx);
            } else {
                idx = help_filename.indexOf('#');
                if (idx != -1) {
                    anchor = help_filename.substring(idx + 1);
                    help_filename = help_filename.substring(0, idx);
                }
            }
            readHelpFile(help_content);//inizializza help_content
            if (selector != null)
                position = getPos(selector);
            else if (anchor != null)
                position = getPosAnchor(anchor);
            if (position < 0) // getPos may return -1 if not found, reset to 0
                position = 0;
        } else {
            position = savedInstanceState.getInt(SAVE_currentItem_ID);
            help_filename = savedInstanceState.getString(SAVE_helpfilename_ID);
            readHelpFile(help_content);//inizializza help_content
        }
//        Log.d(tag, "position:" + position);
        smartPager = new SmartPager(findViewById(R.id.smartpager), this, position,
                findViewById(R.id.indicators));
    }
    /*
        public void onNewIntent (Intent intent) {
            Log.i(tag, "onNewIntent, intent: " + intent);
        }
    */
    public void onTrimMemory(int level) {
//TODO
//liberare la memoria quando (level == TRIM_MEMORY_UI_HIDDEN)
        Log.i(tag, "onTrimMemory, level: " + level);
        super.onTrimMemory(level);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.helpmenu, menu);
        menu.findItem(R.id.menu_about).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {//ab
        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  //actionbar
            finish();//ab
            return true;    //ab
        } else if (itemId == R.id.menu_about) {//                stopExtendedSpeech();
            new About_DF().show(getSupportFragmentManager(), "about");  //df
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class About_DF extends AppCompatDialogFragment { //df

        public About_DF() {	// required empty constructor
        }

        @SuppressLint("InflateParams")
        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            // Inflate the about message contents
            View messageView = inflater.inflate(R.layout.about, null, false);
            return new MaterialAlertDialogBuilder(getActivity())
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                    .setView(messageView)
                    .create();
        }
    }

    @Override
    public void onDestroy() {
        if (smartPager != null)
            smartPager.clearWebViews(true);
        //        	smartPager.getHomeView().clearCache(true);
        super.onDestroy();
    }

    private void readHelpFile(ArrayList<String> help_content) {
        if (help_filename == null) {
            Log.d(tag, "missing help file");
//            help_content.add("missing help file");
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(//try to open help file with locale
                    new InputStreamReader(getAssets().open(BASE_DIRECTORY_HELP + help_filename + "." + Locale.getDefault().getLanguage() + ".hlp")));
        } catch (IOException e) {
            try {
                reader = new BufferedReader(//try to open help file in default language
                        new InputStreamReader(getAssets().open(BASE_DIRECTORY_HELP + help_filename + ".hlp")));
            } catch (IOException ex) {
                Log.d(tag, "IOException: "+e);
//                help_content.add("IOException");
            }
        } finally {
            if (reader != null) {
                try {
                    help_content.clear();//remove old content
                    String mLine;
                    while ((mLine = reader.readLine()) != null) {
                        if (!mLine.startsWith("//")) //check it is not a comment
                            help_content.add(mLine);
                    }
                    reader.close();
                } catch (IOException e) {
                    Log.d(tag, "IOException: "+e);
//                    help_content.add("IOException");
                }
            }
        }
    }

    private int getPos(String word) {
        if (!word.isEmpty()) {
            String match = "<h3>" + word + "</h3>";
            int pos = 0;
            for (String item : help_content) {
                if (item.startsWith(match))
                    return pos;
                pos++;
            }
        }
        return -1;
    }

    private int getPosAnchor(String word) {//positioning based on anchor (new method)
        if (!word.isEmpty()) {
            String match = "<a name=\""+ word + "\"></a>";
            int pos = 0;
            for (String item : help_content) {
                if (item.contains(match))
                    return pos;
                pos++;
            }
        }
        return -1;
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

    // http://developer.android.com/reference/android/support/v4/view/PagerAdapter.html
    class SmartPager extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final ViewPager2 viewPager;
        final Context context;
        final int n_items;
        final LinearLayout indicators;

        private final static int defaultSizeInDp = 12;
        private static final String help_style = "body{margin-right:32px;word-wrap:break-word;background-color:#E8E8E8} hr{height:1px;border:0px;} img{max-width:100%;height:auto; display:block;margin-left:auto;margin-right:auto} A{text-decoration:none}";
        private static final String help_style_API_M = "#main{display:flex;}#section1{order:1;margin:10px}#section2{order:2;}@media screen and (max-width: 560px) {#main{flex-wrap:wrap;}} img{max-width:100%;height:auto; display:block;margin-left:auto;margin-right:auto} A{text-decoration:none}";

        SmartPager(ViewPager2 vpager, Context ctx, int position, LinearLayout indicators) {
            viewPager = vpager;
            n_items = help_content.size();
            vpager.setAdapter(this);
            Resources res = getResources();
            show_nav_buttons(position);
            this.indicators = indicators;
            if (indicators != null)
                if (n_items > 1) { // attiva gli indicatori di posizione solo se il numero di elementi è basso ma superiore a 1
                    vpager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                        @Override
                        public void onPageSelected(int pos) {
                            super.onPageSelected(pos);
//                            Log.d("onPageSelected", "pos:"+pos);
// nota: il loop parte da 1, perchè in posizione 0 c'è il filler1 che va saltato
                            for (int i = 1; i < indicators.getChildCount(); i++) {
                                View view = indicators.getChildAt(i);
                                view.setSelected((i - 1) == pos);//(i-1) è dovuto al fatto che in posizione 0 c'è il filler1 che va saltato
                            }
                            show_nav_buttons(pos);
                        }
                    });
                    int dimens = ((int) res.getDisplayMetrics().density * defaultSizeInDp);
                    View filler1 = new View(ctx);//filler
                    filler1.setLayoutParams((new LinearLayout.LayoutParams(dimens, dimens, 1)));
                    indicators.addView(filler1);
                    for (int i = 0; i < n_items; i++) {
                        //ottieni larghezza e altezza di 'indicators' e poi calcola i vari parametri per posizionare i pallini
                        View view = new View(ctx);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dimens, dimens);
                        lp.setMargins(i == 0 ? dimens / 2 : dimens, 0, 0, 0);
                        view.setLayoutParams(lp);
                        view.setBackgroundResource(R.drawable.indicator_circle);
                        view.setSelected(i == position);
                        indicators.addView(view);
                    }
                    View filler2 = new View(ctx);//filler
                    filler2.setLayoutParams((new LinearLayout.LayoutParams(dimens, dimens, 1)));
                    indicators.addView(filler2);
                } else {
                    indicators.setVisibility(View.GONE);
                }
            context = ctx;
            viewPager.setCurrentItem(position, false);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {//17-10-2021: ViewPager2
            WebView wv = new WebView(context);
            wv.setWebViewClient(new WebViewClient() {
                                    @Override
                                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                        return overrideUrlLoading(url);
                                    }
                                    @Override
                                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//                     if (!request.hasGesture()) return false;<--attenzione ad abilitarla, può dare problemi
                                        return overrideUrlLoading(request.getUrl().toString());
                                    }
                                }
            );

            wv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            return new ViewHolder(wv);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {//17-10-2021: ViewPager2
//            Log.d("onBindViewHolder", "pos:"+position);
            WebView wv = ((ViewHolder) holder).getWebView();
            String help = help_content.get(position);
/*codice per gestire l'affiancamento del testo con l'immagine nel caso di tablet e schermi larghi, richiesto SDK >= Android M
  importante: modificare i files .hlp per poter usare il questa nuova funzione con nuova struttura mettendo anche id="section2" nel tag img:

<div id="main"><div id="section1">blocco testo</div><img id="section2" src="images/blabla.png" alt="blabla" width="240px" height="400px"></div>

*/
            String html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>" + help_style_API_M + "</style></head><body>" + help + "</body></html>";
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);
//old code:            wv.loadDataWithBaseURL("file:///android_asset/", "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><style>" + help_style + "</style></head><body>" + help + "</body></html>", "text/html", "utf-8", null);

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
            viewPager.setCurrentItem(item, smoothScroll);
        }

/*        void onSaveInstanceState(Bundle outState) {
//            outState.putInt(SAVE_currentItem_ID, viewPager.getCurrentItem());
            homeView.saveInstanceState(outState); // handling webview persistence
        }*/

        void clearWebViews(boolean includeDiskFiles) {		//17-10-2021: ViewPager2
            try {
                RecyclerView rv = ((RecyclerView) viewPager.getChildAt(0));
                if (rv != null) {
                    ViewHolder vh = (ViewHolder) rv.findViewHolderForAdapterPosition(0);
                    if (vh != null) {
                        vh.getWebView().clearCache(includeDiskFiles);
                        Log.d(tag, "successful clearWebViews");
                    }
                }
            } catch (RuntimeException re) {
                Log.d(tag, "RuntimeException in clearWebViews", re);
//ignore it, we were just trying to clear the webview cache
            }
        }

        void show_nav_buttons(int position) {
            if (position > 0)
                backbutton.setVisibility(View.VISIBLE);
            else backbutton.setVisibility(View.INVISIBLE);
            if (position < n_items - 1)
                fwdbutton.setVisibility(View.VISIBLE);
            else fwdbutton.setVisibility(View.INVISIBLE);
        }

        boolean overrideUrlLoading(String url) {
            Log.d(tag, "shouldOverrideUrlLoading, url: " + url);
            url = url.trim();
            if (url.startsWith("http:")||url.startsWith("https:")) {
                Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                try {
                    startActivity(myIntent);
                } catch (ActivityNotFoundException | SecurityException e) {
                    Log.d(tag, "Exception: " + e.getMessage());
                    return false;//proceed online using the original link
                }
                return true;
            } else if (url.startsWith(HELP_SCHEME)) {//zzhelp, help screen
                try {
                    url = url.substring(HELP_SCHEME.length());//delete scheme
                    String word = URLDecoder.decode(url, "UTF-8");
                    int pos = getPos(word);
                    if (pos == -1)
                        pos = getPosAnchor(word);
                    if (pos != -1)
                        smartPager.setCurrentItem(pos, false);
                } catch (IllegalArgumentException e) { // report exception silently
                    Log.d(tag, "IllegalArgumentException on: " + url);
                } catch (UnsupportedEncodingException e) { // never happen
                    Log.d(tag, "UnsupportedEncodingException");
                }
                return true;
            } else return false;
        }

        @Override
        public int getItemCount() {
            return n_items;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {//17-10-2021: ViewPager2
            private final WebView wv;

            public ViewHolder(View v) {
                super(v);
                wv = (WebView) v;
            }

            public WebView getWebView() {
                return wv;
            }
        }

    }

}
