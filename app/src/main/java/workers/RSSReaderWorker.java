package workers;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Message;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.nio.charset.UnsupportedCharsetException;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import livio.rssreader.R;
import livio.rssreader.RSSReader;
import livio.rssreader.RSSWidget;
import livio.rssreader.RSSWidgetDark;
import livio.rssreader.backend.FeedsDB;
import livio.rssreader.backend.UserDB;
import livio.rssreader.backend.RSSFeed;
import livio.rssreader.backend.RSSFeedResult;
import tools.LocalBroadcastManager;//added after deprecation of orignal class from Google
import tools.WebFetch;
import tools.WebResponse;


import static livio.rssreader.RSSReader.PREF_FEEDS_LANGUAGE;
import static livio.rssreader.RSSReader.RSS_ACCEPT_MIME;
import static livio.rssreader.RSSReader.RSS_USER_AGENT;

public final class RSSReaderWorker extends Worker {

    public RSSReaderWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {//default constructor
        super(appContext, workerParams);
    }

    private static final String tag = RSSReaderWorker.class.getSimpleName();
    // Notify an update
    public static final int MSG_UPDATE = -1;
    // Notify an error
    public static final int MSG_ERROR = -2;
    // Notify an alternate url
    public static final int MSG_ALTERNATE = -3;

    private static final boolean debug = true; // shall be false in production

    private int requestId;


    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            if (debug)
                Log.d(tag, "executing task:"+getTags());
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            String feed_id = prefs.getString(RSSReader.PREF_FEED_ID, null);
            FeedsDB feedsDB = FeedsDB.getInstance();
            if (feed_id == null) {
                String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
                feed_id = feedsDB.getDefaultFeedId(pref_lang);
            }
            if (isConnected(context)) {
                Thread mTask = GetFeedTask(feed_id, context, feedsDB);
                mTask.start();
            } else {
                if (debug)
                    Log.i(tag, "No network connection");
                sendNotification(MSG_ERROR, RSSReader.DIALOG_CONN_ERROR_ID, 0, context);
            }
            // If there were no errors, return SUCCESS
            return Result.success();
        } catch (Throwable throwable) {

            // If there were errors, return FAILURE
            Log.e(tag, "Error during work execution", throwable);
            return Result.failure();
        }
    }

    private Thread GetFeedTask(final String feed, Context context, FeedsDB feedsDB) {
        return new Thread(() -> {
            Log.i(tag,"GetFeedTask: "+feed);
            Message result;//l'uso di Message è obsoleto, andrà eliminato
            int what, arg1 = 0, arg2 = 0;
            try {
                SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                UserDB ft = UserDB.getInstance(getApplicationContext(), prefs);
                String[] feed_data = ft.getFeedData(feed);
                String feed_url = URLUtil.guessUrl(feed_data[1]); //guessUrl() sanitizes user input
                WebFetch fetch = new WebFetch(); // we create here the WebFetch object, not in onCreate() method!
                long timer = System.nanoTime();

                RSSFeed rssfeed = RSSFeed.getInstance(context.getCacheDir(), feed);

//	ETag and LastMod handling
                boolean conditional_request = false;//used to handle 304 response from strange servers
                String etag = rssfeed.getETag();
                if (etag != null) {
                    fetch.set_ETag(etag);
                    conditional_request = true;
                } else {//note: methods etag e lastmod are alternative and exclude each other
                    String lastmod = rssfeed.getLastMod();
                    if (lastmod != null) {
                        fetch.set_LastMod(lastmod);
                        conditional_request = true;
                    }
                }
                fetch.set_UA(RSS_USER_AGENT);
                fetch.set_Accept(RSS_ACCEPT_MIME);
//response code 304: some servers sent 304 in place of 200 OK, even if we didn't sent a conditional request
//in case a server send 304, we handle it like a 200 OK, to improve user experience
                HttpURLConnection conn = fetch.connect(feed_url); //http
				conn.setReadTimeout(15000);//avoid infinite timeout
                WebResponse mResponse = fetch.openStream(conn, ++requestId);
                if (requestId != mResponse.requestId) {
                    Log.i(tag,"GetFeedTask: cancelling an old request");
                    return;
                }
                String mime_type = mResponse.type;
                if (mime_type == null) {
                    Log.i(tag,"mime_type is null");
                    return;
                }
                int comma_pos = mime_type.indexOf(',');
                if (comma_pos != -1) {//some servers send ',' instead ';'
                    mime_type = mime_type.substring(0, comma_pos);//truncate
                }
                if (conditional_request && (mResponse.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED)) {
                    Log.i(tag,"GetFeedTask: 304 received");
                    // 304: re-use cache content
                    what = MSG_UPDATE;
                } else if ((mResponse.responseCode != HttpURLConnection.HTTP_OK) && (mResponse.responseCode != HttpURLConnection.HTTP_NOT_MODIFIED)) {
                    Log.i(tag, "bad response code: " + mResponse.responseCode +" for " + feed_url);
                    if (!UserDB.isUserFeed(feed))//report error if it is a native feed
                        Log.d(tag, "Bad response code ("+ mResponse.responseCode +") for " + feed_url);
                    what = MSG_ERROR; arg1= RSSReader.DIALOG_HTTP_ERROR_ID; arg2 = mResponse.responseCode;
                } else if (!mime_type.equals("text/xml") && !mime_type.equals("text/rss+xml")
                        && !(mime_type.startsWith("application") && mime_type.contains("xml"))//application/rss+xml,application/atom+xml,application/xml,...
                        && !mime_type.equals("text/html")) {//text/html is sent by weird servers
                    Log.i(tag, "bad mime type: " + mime_type);
                    if (!UserDB.isUserFeed(feed))//report error if it is a native feed
                        Log.d(tag, "Bad mime type ("+ mime_type +") for " + feed_url);
                    what = MSG_ERROR; arg1 = RSSReader.DIALOG_MIMETYPE_ERROR_ID;
                } else {
                    RSSFeedResult feed_result = rssfeed.doProcessStream(fetch.getStream(), mResponse.coding, Integer.parseInt(prefs.getString(RSSReader.PREF_MAX_TITLES, "20")), feed_url, feed_data[0]);
/* moved after block, because connection has to be closed both in positive case and in negative cases
                    fetch.closeStream();
                    conn.disconnect();
*/
                    double delta = (System.nanoTime() - timer)/1000000.0;
                    Log.i(tag,"time to fetch rss: "+delta+" ms, result code: "+feed_result.resultCode);
                    if (feed_result.resultCode >= 0) {
                        rssfeed.serialize(getApplicationContext(), mResponse.ETag, mResponse.LastMod);
                        //                    if (isCancelled()) result = null;
                        what = MSG_UPDATE;
                    } else if (feed_result.resultCode == RSSFeed.zRedirectFeed) {
                        ft.setFeedUrl(context, feed, feed_result.newfeedurl);
                        Log.i(tag,"redirecting feed...");
                        what = MSG_ALTERNATE;
                    } else {
                        Log.i(tag,"unknown feed format: "+feed_result.resultCode);
                        if (!UserDB.isUserFeed(feed))//report error if it is a native feed
                            Log.d(tag, "unknown feed format for " + feed_url);
                        //                    if (isCancelled()) result = null;
                        what = MSG_ERROR; arg1= RSSReader.DIALOG_BAD_ANSWER_ID; arg2 = feed_result.resultCode;
                    }
                }
                fetch.closeStream();
                conn.disconnect();
                //send Update message
            } catch (SocketException e) {
                Log.i(tag,"GetFeedTask: "+e);
                what = MSG_ERROR; arg1 = RSSReader.DIALOG_CONN_ERROR_ID;
            } catch (InterruptedIOException e) {
                Log.i(tag,"GetFeedTask: "+e);
                what = MSG_ERROR; arg1 = RSSReader.DIALOG_INTERRUPTED_ID;
            } catch (IOException | UnsupportedCharsetException e) {
                Log.i(tag,"GetFeedTask: "+e);
                what = MSG_ERROR; arg1 = RSSReader.DIALOG_RSS_ERROR_ID;
            }

            if (debug)
                Log.i(tag, "onPostExecute: "+what);
            sendNotification(what, arg1, arg2, context);
//                refresh_pending = false;
            int[] ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, RSSWidget.class));
            int[] ids_dark = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, RSSWidgetDark.class));
            if ((what == MSG_UPDATE) && (ids.length + ids_dark.length > 0)) {//refresh widgets
                SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);

                String feed_id = prefs.getString(RSSReader.PREF_FEED_ID, null);
                if (feed_id == null) {
                    String pref_lang = prefs.getString(PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
                    feed_id = feedsDB.getDefaultFeedId(pref_lang);
                }
                File feedFile = new File(context.getCacheDir(), feed_id.concat(".cache"));

                if (feedFile.exists()) {
                    try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile))) {
                        RSSFeed feed1 = (RSSFeed) is.readObject();
                        if (feed1.size() > 0) {
                            if (ids.length > 0) {//update light widgets
                                Intent intent = new Intent(context, RSSWidget.class);
                                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                                context.sendBroadcast(intent);
                            }
                            if (ids_dark.length > 0) {//update dark widgets
                                Intent intent = new Intent(context, RSSWidgetDark.class);
                                intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids_dark);
                                context.sendBroadcast(intent);
                            }
                        }
                    } catch (IOException e) {
                        Log.i(tag, "onPostExecute: IOException");
                    } catch (ClassNotFoundException e) {
                        Log.i(tag, "onPostExecute: ClassNotFoundException");
                    }
                }
            }

        });
    }

    private void sendNotification(int what, int arg1, int arg2, Context context) {//l'uso di Message è obsoleto, andrà eliminato
        if (debug)
            Log.i(tag,"Sending Notification: "+what);
        Intent intent = new Intent("RSSReaderService");
        intent.putExtra("what", what);
        intent.putExtra("arg1", arg1);
        intent.putExtra("arg2", arg2);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private boolean isConnected(Context context) {
// reference: http://developer.android.com/training/monitoring-device-state/connectivity-monitoring.html
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnectedOrConnecting();
        } catch (SecurityException e) {
            return true; // we don't know due to security exception, so let's assume that connection is available
        }
    }

}