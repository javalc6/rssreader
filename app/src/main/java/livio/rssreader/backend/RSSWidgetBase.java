package livio.rssreader.backend;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import livio.rssreader.BuildConfig;
import livio.rssreader.R;
import livio.rssreader.RSSReader;

import workers.RSSReaderWorker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.widget.RemoteViews;

import static livio.rssreader.RSSReader.PREF_FEEDS_LANGUAGE;
import static livio.rssreader.RSSReader.uniqueWorkerName;


abstract public class RSSWidgetBase extends AppWidgetProvider {//widget-theme

    private int counter = 0;
    private static final String PREFS_NAME = "RSSWidget";
    private static final String WPREF_NEWS_FEED = "news_feed_";

    private final static String tag = "RSSWidgetBase";

    abstract protected int getLayout();//widget-theme

    @Override
    public void onEnabled(Context context) {
        Log.i(tag,"onEnabled");
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        Log.i(tag,"onUpdate: "+counter);
        counter++;
// To prevent any ANR timeouts, use AsyncThread

// For each widget that needs an update, get the text that we should display:
//   - Create a RemoteViews object for it
//   - Set the text in the RemoteViews object
//   - Tell the AppWidgetManager to show that views object for the widget.
/* for further development
        final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];
            String titlePrefix = ExampleAppWidgetConfigure.loadTitlePref(context, appWidgetId);
            updateAppWidget(context, appWidgetManager, appWidgetId, titlePrefix);
        }
*/

        String item = getItemfromfile(context);
        if (item != null)
            appWidgetManager.updateAppWidget(new ComponentName(context, this.getClass()),
                    updateView(context, item));
//        else mTask = (GetNews) new GetNews().execute(context);
// preparation for future widget enhancement
// set news_feed in preference for each widget
// this line will be moved in WidgetConfigure.java
        SharedPreferences.Editor prefs_edit = context.getSharedPreferences(PREFS_NAME, 0).edit();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (int appWidgetId : appWidgetIds) {
            String feed_id = prefs.getString(RSSReader.PREF_FEED_ID, null);//lang
            if (feed_id == null) {
                String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
                FeedsDB feedsDB = FeedsDB.getInstance();
                feed_id = feedsDB.getDefaultFeedId(pref_lang);//lang
            }

            UserDB ft = UserDB.getInstance(context, prefs);
// following two lines will be moved in WidgetConfigure.java
            String[] feed_data = ft.getFeedData(feed_id);
            prefs_edit.putString(WPREF_NEWS_FEED + appWidgetId, feed_data[1]);//lang
            prefs_edit.apply();
        }
        doPeriodicWork(context, ExistingPeriodicWorkPolicy.KEEP);//workmanager
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.d(tag, "onDeleted");

        SharedPreferences.Editor prefs_edit = context.getSharedPreferences(PREFS_NAME, 0).edit();
        for (int appWidgetId : appWidgetIds) {
// do clean-up
            prefs_edit.remove(WPREF_NEWS_FEED + appWidgetId);
        }
        prefs_edit.apply();
    }

    public void onDisabled(Context context) {
        Log.d(tag, "onDisabled");
//        WorkManager.getInstance().cancelUniqueWork(uniqueWorkerName); - disabled to avoid interference with main activity
        super.onDisabled(context);
    }


    private RemoteViews updateView(Context context, String description) {
        Log.d(tag,"updateView");
        RemoteViews updateViews = new RemoteViews(BuildConfig.APPLICATION_ID, getLayout());//widget-theme
        updateViews.setTextViewText(R.id.message, Html.fromHtml(description));
// When user clicks on widget, launch main activity
        updateViews.setOnClickPendingIntent(R.id.widget,
                PendingIntent.getActivity(context, 0, new Intent(context, RSSReader.class), 0));
        return updateViews;
    }

    private void doPeriodicWork(Context context, ExistingPeriodicWorkPolicy epwp) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int refresh_timer = Integer.parseInt(prefs.getString(RSSReader.PREF_REFRESH_TIMER, "3600"));
        Log.d(tag, "doPeriodicWork: "+ refresh_timer);
        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(RSSReaderWorker.class, refresh_timer, TimeUnit.SECONDS)//workmanager
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("RSSReader")
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(uniqueWorkerName,  epwp, periodicWorkRequest);//workmanager
    }


    private String getItemfromfile(Context context) {
        FeedsDB feedsDB = FeedsDB.getInstance();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String feed_id = prefs.getString(RSSReader.PREF_FEED_ID, null);//lang
        if (feed_id == null) {
            String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
            feed_id = feedsDB.getDefaultFeedId(pref_lang);//lang
        }
        File feedFile = new File(context.getCacheDir(), feed_id.concat(".cache"));

        if (feedFile.exists())
            try {
                ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile));
                RSSFeed feed = (RSSFeed) is.readObject();
                is.close();
                if (feed.size() > 0)
                    return feed.getItem(0).toString();
            } catch (ClassNotFoundException e) {
                Log.i(tag,"ClassNotFoundException");
            } catch (IOException e) {
                Log.i(tag,"IOException");
            }
        return null;
    }

}
