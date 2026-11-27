package livio.rssreader.backend

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Menu
import androidx.core.view.get
import androidx.core.view.size
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager.Companion.getInstance
import livio.rssreader.R
import livio.rssreader.RSSReader
import livio.rssreader.RSSWidget
import livio.rssreader.RSSWidgetDark
import org.xml.sax.XMLReader
import workers.RSSReaderWorker
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.util.Arrays
import java.util.Locale

/*
Instance part (needs a Context and the default SharedPreferences):
  - feed id resolution and cache file handling (read, cleanup)
  - periodic work scheduling and cancellation
  - cards and HTML style generation for the item list
  - category list used by the OPML import dialog

Companion part (stateless utilities, also used by other classes):
  - getLocale, cleanupContent, setTextColorPopupMenu
  - RSS_ACCEPT_MIME, RSS_USER_AGENT
*/
class RSSHelper(
    private val context: Context,
    private val prefs: SharedPreferences
) {


    /**///////////////////////////////////////// */
    // preferences and feed id
    /**///////////////////////////////////////// */

    /** Language of the feeds, from preferences (or the default of the app). */
    fun feedsLanguage(): String = prefs.getString(
        RSSReader.PREF_FEEDS_LANGUAGE,
        context.getString(R.string.default_feed_language_code)
    )!!

    /** Id of the feed currently selected, or the default feed for the preferred language. */
    fun currentFeedId(): String {
        return prefs.getString(RSSReader.PREF_FEED_ID, null)
            ?: FeedsDB.getDefaultFeedId(feedsLanguage()) //lang
    }

    fun refreshTimerSeconds(): Int =
        prefs.getString(RSSReader.PREF_REFRESH_TIMER, "3600")!!.toInt()

    /**///////////////////////////////////////// */
    // work manager
    /**///////////////////////////////////////// */

    /** (Re)schedules the periodic refresh using the timer defined in preferences. */
    fun schedulePeriodicWork() {
        RSSReaderWorker.doPeriodicWork(
            context,
            refreshTimerSeconds(),
            ExistingPeriodicWorkPolicy.REPLACE
        ) //workmanager
    }

    /** Cancels the periodic work if no widget is installed. */
    fun cancelPeriodicWorkIfNoWidgets() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RSSWidget::class.java))
        val idsDark = manager.getAppWidgetIds(ComponentName(context, RSSWidgetDark::class.java))
        if (ids.size + idsDark.size == 0) { //no widgets exist
            getInstance(context).cancelUniqueWork(RSSReaderWorker.uniqueWorkerName)
        }
    }

    /**///////////////////////////////////////// */
    // cache files
    /**///////////////////////////////////////// */

    /** Reads the serialized feed from cache; returns null if missing or unreadable. */
    fun readCachedFeed(feedId: String): RSSFeed? {
        val feedFile = File(context.cacheDir, "$feedId.cache")
        if (!feedFile.exists()) {
            Log.i(TAG, "feedFile does not exist: $feedId")
            return null
        }
        return try {
            ObjectInputStream(FileInputStream(feedFile)).use { it.readObject() as RSSFeed? }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading cache", e)
            null
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Error loading cache", e)
            null
        }
    }

    /** Deletes cache files older than limit, except the one of the current feed. */
    fun deleteCacheFiles(limit: Long) {
        val dir = context.cacheDir
        if (dir != null && dir.isDirectory) {
            val feedId = prefs.getString(RSSReader.PREF_FEED_ID, null)
            val feedFileName = if (feedId != null) ("$feedId.cache") else null

            for (child in dir.listFiles()!!) {
                if (child.isFile && child.lastModified() < limit) {
                    if ((feedFileName == null) || (feedFileName != child.name)) {
                        child.delete()
                    }
                }
            }
        }
    }

    /** Deletes any cache file! */
    fun deleteAllCacheFiles() {
        val dir = context.cacheDir
        if (dir != null && dir.isDirectory) {
            for (child in dir.listFiles()!!) {
                if (child.isFile) {
                    child.delete()
                }
            }
        }
    }

    /**///////////////////////////////////////// */
    // list of items and item viewer
    /**///////////////////////////////////////// */

    fun formatPublisher(title: String, link: String?): String {
        return String.format(context.getString(R.string.msg_publisher), title, link)
    }

    /** Builds the content of the cards shown in the main list. */
    fun buildCards(feed: RSSFeed): MutableList<CardContent> {
        val cards: MutableList<CardContent> = ArrayList()
        val smartTitles = prefs.getBoolean(RSSReader.PREF_SMART_TITLES, false)
        for (item in feed.allItems) cards.add(
            CardContent(
                item.getNicePubDate(context),
                item.getTitle(smartTitles)
            )
        )
        return cards
    }

    /** True if images must not be downloaded (only wifi allowed, but wifi is not active). */
    fun isTextMode(): Boolean {
        if (prefs.getString(RSSReader.PREF_DOWNLOAD_IMAGES, "any") != "wifi") return false
        //images can be downloaded only with wifi connectivity
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val ni = cm.activeNetworkInfo
        return (ni == null) || (ni.type != ConnectivityManager.TYPE_WIFI)
    }

    /** CSS style used by the item viewer, with the colors of the current theme. */
    fun buildItemStyle(hyperlink: Int, background: Int, textcolor: Int, genericcolor: Int): String {
        var style = String.format(
            STYLE_TPL,
            (hyperlink and 0xFFFFFF),
            (background and 0xFFFFFF),
            (textcolor and 0xFFFFFF),
            (genericcolor and 0xFFFFFF),
            (genericcolor and 0xFFFFFF)
        )
        val fontsize = prefs.getInt(RSSReader.PREF_FONTSIZE, 16) // 16 is the default font size
        // note: padding inserted due to bug in android 4.2 and later (in a list with more than 10 elements the first digit disappears, so the user sees 6 7 8 9 and then 0 instead of 10! Problem related to the webview inside the viewpager
        style += " body {font-size:" + (Math.round(fontsize / 1.6) / 10.0) + "em;padding:.6em}"
        return style
    }

    /**///////////////////////////////////////// */
    // OPML import: categories
    /**///////////////////////////////////////// */

    /** Localized names of native categories followed by user categories. */
    fun importCategoryNames(): List<String> {
        val categories = context.resources.getStringArray(R.array.categories_list) //localized names
        val catList = ArrayList<String>(
            Arrays.asList(*categories).subList(0, FeedsDB.categories.size)
        )
        val udb = UserDB.getInstance(context, prefs)
        for (k in udb.userCats.indices)  //user categories
            catList.add(udb.getUserCat(k)[0])
        return catList
    }

    /** Category id of the item selected in the list returned by importCategoryNames(). */
    fun importCategoryId(which: Int): String? {
        return if (which < FeedsDB.categories.size) FeedsDB.categories[which][2]
        else UserDB.getInstance(context, prefs).getUserCat(which - FeedsDB.categories.size)[2]
    }

    companion object {
        private const val TAG = "RSSHelper"

        const val RSS_ACCEPT_MIME: String =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.5"

        @JvmField
        val RSS_USER_AGENT: String =
            "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; unknown) Firefox/62.0" //dummu user agent to get expected response from most remote servers

        //colorpicker
        private const val STYLE_TPL =
            " A {color: #%06x;} body {background-color:#%06x;color:#%06x;margin-right:32px;word-wrap:break-word;} hr {background-color:#%06x;color:#%06x;height:1px;border:0px;} img {max-width:100%%;height:auto;} " // nota il %% per evitare eccezioni con String.format()

        @JvmStatic
        fun getLocale(lang_code: String): Locale {
            if (lang_code.startsWith("en")) return Locale.ENGLISH
            else if (lang_code.startsWith("fr")) return Locale.FRENCH
            else if (lang_code.startsWith("it")) return Locale.ITALIAN
            else if (lang_code.startsWith("de")) return Locale.GERMAN
            else { //other languages, e.g. spanish
                val split = lang_code.indexOf('-')
                if (split != -1) { // split language country
                    return Locale(lang_code.substring(0, split), lang_code.substring(split + 1))
                } else return Locale(lang_code)
            }
        }

        fun setTextColorPopupMenu(menu: Menu, targetColor: Int) {
            for (i in 0..<menu.size) {
                val item = menu[i]
                val spannableTitle = SpannableString(item.title)
                spannableTitle.setSpan(
                    ForegroundColorSpan(targetColor),
                    0,
                    spannableTitle.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.title = spannableTitle
            }
        }

        fun cleanupContent(content: String, tts: Boolean): String {
            val li_header = (if (tts) "\n" else "\n- ")
            // needed as Html class does not support tags <li>, <ul>, <ol>...
            return Html.fromHtml(
                content.replace("<script.+?</script>".toRegex(), ""),
                null
            ) { opening: Boolean, tag: String?, output: Editable?, xmlReader: XMLReader? ->
                if (opening) {
                    if (tag.equals("li", ignoreCase = true)) output!!.append(li_header)
                    else if (tag.equals("hr", ignoreCase = true) || tag.equals(
                            "br",
                            ignoreCase = true
                        ) || tag.equals("dt", ignoreCase = true)
                    )  // nota: dt deve causare una nuova linea
                        output?.append("\n")
                } else if ((tag.equals("ul", ignoreCase = true) || tag.equals(
                        "ol",
                        ignoreCase = true
                    ) ||
                            tag.equals("dl", ignoreCase = true) || tag.equals(
                        "dt",
                        ignoreCase = true
                    ))
                ) output?.append("\n")
            }.toString().replace("\uFFFC", "")
        }
    }
}
