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
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Parcelable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.annotation.SuppressLint;
import android.widget.LinearLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Stack;

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

    private SmartPager smartPager;
    private ImageButton backbutton;
    private ImageButton fwdbutton;
    private boolean rtl; 

    private String help_filename;
    private final ArrayList<String> help_content = new ArrayList<>();

    private static final String SAVE_currentItem_ID = "currentitem";
    private static final String SAVE_helpfilename_ID = "filename";

    /** Called to save instance state: put critical variables here! */
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        int position = smartPager.getCurrentItem();
        if (rtl)
            position = smartPager.getCount() - 1 - position;
        outState.putInt(SAVE_currentItem_ID, position);
        outState.putString(SAVE_helpfilename_ID, help_filename);
    }

    @SuppressLint("NewApi")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(tag, "onCreate");

        setContentView(R.layout.showhelp);
        rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        ActionBar t = getSupportActionBar();
        if (t != null) {
            t.setDisplayHomeAsUpEnabled(true);
        }

        backbutton = findViewById(R.id.backbutton);
        fwdbutton = findViewById(R.id.fwdbutton);

        int position;
        if (savedInstanceState == null) {
            position = 0;//default
            if (getIntent().getStringExtra("help") != null) {
                help_filename = getIntent().getStringExtra("help");
				if (help_filename == null)
					help_filename = getString(R.string.helpfile);//default help file
                int idx = help_filename.indexOf('/');
                String selector = "";//default: no selector
                if (idx != -1) {
                    selector = help_filename.substring(idx + 1);
                    help_filename = help_filename.substring(0, idx);
                }
                readHelpFile(help_content);//inizializza help_content
                if (selector.length() > 0)
                    position = getPos(selector);
                if (position < 0) // getPos may return -1 if not found, reset to 0
                    position = 0;
            }
        } else {
            position = savedInstanceState.getInt(SAVE_currentItem_ID);
            help_filename = savedInstanceState.getString(SAVE_helpfilename_ID);
            readHelpFile(help_content);//inizializza help_content
        }
//        Log.d(tag, "position:" + position);
        smartPager = new SmartPager(findViewById(R.id.smartpager), this, position,
                findViewById(R.id.indicators));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.helpmenu, menu);
        menu.findItem(R.id.menu_about).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.menu_about) {//                stopExtendedSpeech();
            new About_DF().show(getSupportFragmentManager(), "about");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class About_DF extends AppCompatDialogFragment {

        public About_DF() {	// required empty constructor
        }

        @NonNull
        @SuppressLint("InflateParams")
        @Override
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
        if (word.length() > 0) {
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
    }

    // http://developer.android.com/reference/android/support/v4/view/PagerAdapter.html
    class SmartPager extends PagerAdapter implements OnPageChangeListener {
        final Stack<WebView> idlelist = new Stack<>();
        final ViewPager viewPager;
        WebView primaryItem;
        final WebView homeView; // "home page"
        final Context context;
        final int n_items;
        final LinearLayout indicators;

        private final static int defaultSizeInDp = 12;
        private static final String help_style = "body{margin-right:32px;word-wrap:break-word;background-color:#E8E8E8} hr{height:1px;border:0px;} img{max-width:100%;height:auto; display:block;margin-left:auto;margin-right:auto} A{text-decoration:none}";
        private static final String help_style_API_M = "#main{display:flex;}#section1{order:1;margin:10px}#section2{order:2;}@media screen and (max-width: 560px) {#main{flex-wrap:wrap;}} img{max-width:100%;height:auto; display:block;margin-left:auto;margin-right:auto} A{text-decoration:none}";

        SmartPager(ViewPager vpager, Context ctx, int position, LinearLayout indicators) {
            viewPager = vpager;
            n_items = help_content.size();
            vpager.setAdapter(this);
            Resources res = getResources();
            if (rtl)
                position = n_items - 1 - position;
            show_nav_buttons(position);
            this.indicators = indicators;
            if (indicators != null)
                if (n_items > 1) { // attiva gli indicatori di posizione solo se il numero di elementi è basso ma superiore a 1
                    vpager.addOnPageChangeListener(this);
                    int dimens = ((int) res.getDisplayMetrics().density * defaultSizeInDp);
                    View filler1 = new View(ctx);//filler
                    filler1.setLayoutParams((new LinearLayout.LayoutParams(dimens, dimens, 1)));
                    indicators.addView(filler1);
                    for (int i = 0; i < n_items; i++) {
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
            homeView = new WebView(ctx);
            viewPager.setCurrentItem(position, false); 
        }

        @Override
        public int getCount() {
            return n_items;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            WebView wv;
            if (position == 0) // page 0
                wv = homeView;
            else if (idlelist.empty())
                wv =  new WebView(context);
            else {
                wv = idlelist.pop();
            }
            String help = help_content.get(rtl ? n_items - 1 - position: position);
            String html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>" + help_style_API_M + "</style></head><body>" + help + "</body></html>";
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null);

            container.addView(wv);
            return wv;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object view) {
            container.removeView((WebView) view);
            if (position != 0) {// page > 0
                ((WebView) view).loadUrl("about:blank");// clean-up view to re-cycle

                idlelist.push((WebView) view);
            }
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object view) {
//            Log.d("setPrimaryItem", "pos:"+position);
            primaryItem = (WebView) view;
        }

        int getCurrentItem() {
            return viewPager.getCurrentItem();
        }

        void setCurrentItem(int item, boolean smoothScroll){
//            Log.d("setCurrentItem", "pos:"+item);
            viewPager.setCurrentItem(item, smoothScroll);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
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
            if (!idlelist.empty()) {
                WebView tv = idlelist.peek();
                tv.clearCache(includeDiskFiles);
            }
        }


        void printInfo(String msg) {
            printInfo(msg, "text/html", "utf-8");
        }

        void printInfo(String msg, String type, String coding) {
//        	setMultiviewMode(false);
            viewPager.setCurrentItem(0, false);
            homeView.loadDataWithBaseURL("file:///android_asset/", "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><style>" + help_style + "</style></head><body>" + msg + "</body></html>", type, coding, null);

        }

        void show_nav_buttons(int position) {
            if (position > 0)
                backbutton.setVisibility(View.VISIBLE);
            else backbutton.setVisibility(View.INVISIBLE);
            if (position < n_items - 1)
                fwdbutton.setVisibility(View.VISIBLE);
            else fwdbutton.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
//do nothing
        }

        @Override
        public void onPageSelected(int position) {
//            Log.d("SmartPager", "onPageSelected: "+position);
// nota: il loop parte da 1, perchè in posizione 0 c'è il filler1 che va saltato
            if (rtl)
                position = n_items - 1 - position;
            for (int i = 1; i < indicators.getChildCount(); i++) {
                View view = indicators.getChildAt(i);
                view.setSelected((i - 1) == position);//(i-1) è dovuto al fatto che in posizione 0 c'è il filler1 che va saltato
            }
            show_nav_buttons(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
//do nothing
        }

    }

}
