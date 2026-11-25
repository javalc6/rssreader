package livio.rssreader

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tools.FormFactorUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.abs
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import livio.rssreader.RSSReader.Companion.PREF_THEME
import proto.BaseActivity
import tools.ColorBase

/*
The activity ShowHelp processes the parameter 'help' when called as intent, this parameter contains the filename of the help file and optionally the section marked by /

For the internal navigation use scheme help:
 */
class ShowHelp : BaseActivity() {
    private var smartPager: SmartPager? = null
    private var backbutton: ImageButton? = null
    private var fwdbutton: ImageButton? = null

    private var help_filename: String? = null
    private val help_content = ArrayList<String>()

    val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    /** Called to save instance state: put critical variables here!  */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val position = smartPager?.currentItem
        position?.let { outState.putInt(SAVE_currentItem_ID, position) }
        outState.putString(SAVE_helpfilename_ID, help_filename)
    }

    override fun getAppBarId(): Int {
        return R.id.my_appbar
    }

    override fun isLightTheme(): Boolean {
        return !ColorBase.is_dark_theme(prefs.getString(PREF_THEME, "light") ?: "light")
    }

    @SuppressLint("NewApi")
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT_FULL > Build.VERSION_CODES_FULL.BAKLAVA && !prefs.getBoolean(
                RSSReader.PREF_THEME_AUTO,
                true
            ) && !ColorBase.is_dark_theme(prefs.getString(PREF_THEME, "light"))
        ) { //zzexpanded: workaround for "expanded" dark mode
            setTheme(R.style.Theme_Light_ExpandedOption) //light theme for API levels beyond BAKLAVA
        }

        super.onCreate(savedInstanceState)
        Log.i(tag, "onCreate")

        setContentView(R.layout.showhelp)

        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val t = supportActionBar
        t?.setDisplayHomeAsUpEnabled(!FormFactorUtils.isArc(this))

        backbutton = findViewById(R.id.backbutton)
        fwdbutton = findViewById(R.id.fwdbutton)

        var position: Int
        if (savedInstanceState == null) {
            position = 0 //default
            help_filename = intent.getStringExtra("help")
            if (help_filename == null) help_filename =
                getString(R.string.helpfile) //default help file

            var idx = help_filename!!.indexOf('/')
            var selector: String? = null
            var anchor: String? = null
            if (idx != -1) {
                selector = help_filename!!.substring(idx + 1)
                help_filename = help_filename!!.substring(0, idx)
            } else {
                idx = help_filename!!.indexOf('#')
                if (idx != -1) {
                    anchor = help_filename!!.substring(idx + 1)
                    help_filename = help_filename!!.substring(0, idx)
                }
            }
            readHelpFile(help_content)
            if (selector != null) position = getPos(selector)
            else if (anchor != null) position = getPosAnchor(anchor)
            if (position < 0)  // getPos may return -1 if not found, reset to 0
                position = 0
        } else {
            position = savedInstanceState.getInt(SAVE_currentItem_ID)
            help_filename = savedInstanceState.getString(SAVE_helpfilename_ID)
            readHelpFile(help_content)
        }

        //        Log.d(tag, "position:" + position);
        smartPager = SmartPager(
            findViewById(R.id.smartpager), this, position,
            findViewById(R.id.indicators)
        )

    }

    /*
    public void onNewIntent (Intent intent) {
        Log.i(tag, "onNewIntent, intent: " + intent);
    }
*/
    override fun onTrimMemory(level: Int) {
//TODO
//liberare la memoria quando (level == TRIM_MEMORY_UI_HIDDEN)
        Log.i(tag, "onTrimMemory, level: $level")
        super.onTrimMemory(level)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.helpmenu, menu)
        menu.findItem(R.id.menu_about).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        val itemId = item.itemId
        if (itemId == android.R.id.home) {  //actionbar
            finish()
            return true
        } else if (itemId == R.id.menu_about) { //stopExtendedSpeech();
            About_DF().show(supportFragmentManager, "about")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class About_DF : AppCompatDialogFragment() {
        @SuppressLint("InflateParams")
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val inflater = requireActivity().layoutInflater
            // Inflate the about message contents
            val messageView = inflater.inflate(R.layout.about, null, false)
            return MaterialAlertDialogBuilder(requireActivity())
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                .setView(messageView)
                .create()
        }
    }

    public override fun onDestroy() {
        smartPager?.clearWebViews(true)
        super.onDestroy()
    }

    private fun readHelpFile(help_content: ArrayList<String>) {
        if (help_filename == null) {
            Log.d(tag, "missing help file")
            //            help_content.add("missing help file");
            return
        }
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader( //try to open help file with locale
                InputStreamReader(
                    assets.open(
                        "$BASE_DIRECTORY_HELP$help_filename." + Locale.getDefault()
                            .language + ".hlp"
                    )
                )
            )
        } catch (_: IOException) {
            try {
                reader = BufferedReader( //try to open help file in default language
                    InputStreamReader(assets.open("$BASE_DIRECTORY_HELP$help_filename.hlp"))
                )
            } catch (ex: IOException) {
                Log.d(tag, "IOException: $ex")
                //                help_content.add("IOException");
            }
        } finally {
            if (reader != null) {
                try {
                    help_content.clear() //remove old content
                    var mLine: String?
                    while ((reader.readLine().also { mLine = it }) != null) {
                        if (!mLine!!.startsWith("//"))  //check it is not a comment
                            help_content.add(mLine)
                    }
                    reader.close()
                } catch (e: IOException) {
                    Log.d(tag, "IOException: $e")
                    //                    help_content.add("IOException");
                }
            }
        }
    }

    private fun getPos(word: String): Int {
        if (word.isNotEmpty()) {
            val match = "<h3>$word</h3>"
            for ((pos, item) in help_content.withIndex()) {
                if (item.startsWith(match)) return pos
            }
        }
        return -1
    }

    private fun getPosAnchor(word: String): Int { //positioning based on anchor (new method)
        if (word.isNotEmpty()) {
            val match = "<a name=\"$word\"></a>"
            for ((pos, item) in help_content.withIndex()) {
                if (item.contains(match)) return pos
            }
        }
        return -1
    }

    private fun backpage() {
        movepage(false)
    }

    fun backpage(view: View?) {
        backpage()
    }

    private fun fwdpage() {
        movepage(true)
    }

    fun fwdpage(view: View?) {
        fwdpage()
    }

    private fun movepage(up: Boolean) {
        val pos = smartPager!!.currentItem
        if (up) {
            if (pos < smartPager!!.itemCount - 1) {
                smartPager?.setCurrentItem(pos + 1, true) // scroll
            }
        } else {
            if (pos > 0) {
                smartPager?.setCurrentItem(pos - 1, true) // scroll
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean { //15-02-2025, added DPAD support
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    backpage()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    fwdpage()
                    return true
                }

                else -> {}
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // http://developer.android.com/reference/android/support/v4/view/PagerAdapter.html
    @SuppressLint("ClickableViewAccessibility")
    internal inner class SmartPager(
        val viewPager: ViewPager2,
        ctx: Context,
        position: Int,
        indicators: LinearLayout
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
        val context: Context
        val n_items: Int = help_content.size
        val dark: Boolean //zzdark

        init {
            viewPager.setAdapter(this)
            val res = resources
            show_nav_buttons(position)
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) { //zzdark
                val nightModeFlags =
                    res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK //zzdark
                //        Log.d(tag, "nightModeFlags="+nightModeFlags);
                dark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            } else dark = false
            show_indicators(indicators, ctx, position)
            context = ctx
            viewPager.setCurrentItem(position, false)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder { //17-10-2021: ViewPager2
            val wv = WebView(context)
            if (dark) wv.setBackgroundColor(getColor(R.color.gray75)) //zzdark

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                    return overrideUrlLoading(url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest
                ): Boolean {
                    //                     if (!request.hasGesture()) return false;<--attenzione ad abilitarla, può dare problemi
                    return overrideUrlLoading(request.url.toString())
                }
            }

            wv.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return ViewHolder(wv)
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int
        ) { //17-10-2021: ViewPager2
//            Log.d("onBindViewHolder", "pos:"+position);
            val wv = (holder as ViewHolder).webView
            val help = help_content[position]
            /*codice per gestire l'affiancamento del testo con l'immagine nel caso di tablet e schermi larghi, richiesto SDK >= Android M
  importante: modificare i files .hlp per poter usare il questa nuova funzione con nuova struttura mettendo anche id="section2" nel tag img:

<div id="main"><div id="section1">blocco testo</div><img id="section2" src="images/blabla.png" alt="blabla" width="240px" height="400px"></div>

*/
            var darkstyle = "" //zzdark
            if (dark) { //zzdark
                val background = getColor(R.color.gray75) //zzdark
                val textcolor = Color.WHITE //zzdark
                val linkcolor = Color.YELLOW //zzdark
                darkstyle = String.format(
                    dark_style,
                    background and 0xFFFFFF,
                    textcolor and 0xFFFFFF,
                    linkcolor and 0xFFFFFF
                ) //zzdark
            }
            val html =
                "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>$darkstyle$help_style</style></head><body>$help</body></html>" //zzdark
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
// workaround for issue https://issuetracker.google.com/issues/123006042
// workaround is a simplified version of https://github.com/android/views-widgets-samples/blob/master/ViewPager2/app/src/main/java/androidx/viewpager2/integration/testapp/NestedScrollableHost.kt
            recyclerView.addOnItemTouchListener(object : OnItemTouchListener {
                var lastX: Int = 0
                var lastY: Int = 0
                var touchSlop: Int = 0
                var waitingFirst: Boolean = false
                var disallowInterceptTouch: Boolean = false

                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
//                    Log.d(tag, "onInterceptTouchEvent+"+e.getAction());
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = e.x.toInt()
                            lastY = e.y.toInt()
                            touchSlop = ViewConfiguration.get(context).scaledEdgeSlop
                            waitingFirst = true
                            disallowInterceptTouch = false
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = abs(e.x.toInt() - lastX)
                            val dy = abs(e.y.toInt() - lastY)
                            val scaledDx =
                                dx / 2 // assuming ViewPager2 touch-slop is 2x touch-slop of child
                            if (waitingFirst && (scaledDx > touchSlop || dy > touchSlop)) {
                                waitingFirst = false
                                if (dy > scaledDx) {
                                    // Gesture is perpendicular, disallow all parents to intercept
                                    disallowInterceptTouch = true
                                }
                            }
                            if (disallowInterceptTouch) rv.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                }

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                }
            })
        }

        val currentItem: Int
            get() = viewPager.currentItem

        fun setCurrentItem(item: Int, smoothScroll: Boolean) {
//            Log.d("setCurrentItem", "pos:"+item);
            viewPager.setCurrentItem(item, smoothScroll)
        }

        fun clearWebViews(includeDiskFiles: Boolean) {
            try {
                (viewPager.getChildAt(0) as? RecyclerView)?.let { rv ->
                    (rv.findViewHolderForAdapterPosition(0) as? ViewHolder)?.let { vh ->
                        vh.webView.clearCache(includeDiskFiles)
                        Log.d(tag, "successful clearWebViews")
                    }
                }
            } catch (re: RuntimeException) {
                Log.d(tag, "RuntimeException in clearWebViews", re)
                //ignore it, we were just trying to clear the webview cache
            }
        }

        private fun show_indicators(indicators: LinearLayout, ctx: Context, position: Int) {
            if (n_items > 1) {
                indicators.visibility = View.VISIBLE
                indicators.gravity = Gravity.CENTER
                if (dark) {
                    indicators.setBackgroundColor(getColor(R.color.gray75))
                } else {
                    indicators.setBackgroundColor(Color.WHITE)
                }

                val density = resources.displayMetrics.density
                val activeWidthPx = (20 * density).toInt()
                val inactiveWidthPx = (8 * density).toInt()
                val heightPx = (8 * density).toInt()
                val marginPx = (4 * density).toInt()

                indicators.removeAllViews()
                for (i in 0..<n_items) {
                    val view = View(ctx)
                    val isSelected = i == position
                    val w = if (isSelected) activeWidthPx else inactiveWidthPx
                    val lp = LinearLayout.LayoutParams(w, heightPx)
                    lp.setMargins(marginPx, 0, marginPx, 0)
                    view.layoutParams = lp
                    view.setBackgroundResource(
                        if (isSelected) R.drawable.indicator_dot_active else R.drawable.indicator_dot_inactive
                    )
                    indicators.addView(view)
                }

                indicators.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val touchX = event.x
                        var closestIndex = 0
                        var minDistance = Float.MAX_VALUE
                        for (i in 0..<indicators.childCount) {
                            val child = indicators.getChildAt(i)
                            val childCenterX = child.left + child.width / 2f
                            val dist = abs(touchX - childCenterX)
                            if (dist < minDistance) {
                                minDistance = dist
                                closestIndex = i
                            }
                        }
                        viewPager.setCurrentItem(closestIndex, true)
                        v.performClick()
                    }
                    true
                }

                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(pos: Int) {
                        super.onPageSelected(pos)
                        for (i in 0..<indicators.childCount) {
                            val view = indicators.getChildAt(i)
                            val isSelected = i == pos
                            val lp = view.layoutParams as LinearLayout.LayoutParams
                            lp.width = if (isSelected) activeWidthPx else inactiveWidthPx
                            view.layoutParams = lp
                            view.setBackgroundResource(
                                if (isSelected) R.drawable.indicator_dot_active else R.drawable.indicator_dot_inactive
                            )
                        }
                        show_nav_buttons(pos)
                    }
                })
            } else {
                indicators.visibility = View.GONE
            }
        }

        private fun show_nav_buttons(position: Int) {
            if (position > 0) backbutton?.visibility = View.VISIBLE
            else backbutton?.visibility = View.INVISIBLE
            if (position < n_items - 1) fwdbutton?.visibility = View.VISIBLE
            else fwdbutton?.visibility = View.INVISIBLE
        }

        fun overrideUrlLoading(url: String): Boolean {
            var url = url
            Log.d(tag, "shouldOverrideUrlLoading, url: $url")
            url = url.trim { it <= ' ' }
            if (url.startsWith("http:") || url.startsWith("https:")) {
                val myIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                try {
                    startActivity(myIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.d(tag, "Exception: " + e.message)
                    return false //proceed online using the original link
                } catch (e: SecurityException) {
                    Log.d(tag, "Exception: " + e.message)
                    return false
                }
                return true
            } else if (url.startsWith(HELP_SCHEME)) { //zzhelp, help screen
                try {
                    url = url.substring(HELP_SCHEME.length) //delete scheme
                    val word = URLDecoder.decode(url, "UTF-8")
                    var pos = getPos(word)
                    if (pos == -1) pos = getPosAnchor(word)
                    if (pos != -1) smartPager?.setCurrentItem(pos, false)
                } catch (e: IllegalArgumentException) { // report exception silently
                    Log.d(tag, "IllegalArgumentException on: $url")
                } catch (e: UnsupportedEncodingException) { // never happen
                    Log.d(tag, "UnsupportedEncodingException")
                }
                return true
            } else return false
        }

        override fun getItemCount(): Int {
            return n_items
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val webView: WebView = v as WebView
        }

    }

    companion object {
        private const val tag = "ShowHelp"
        private const val BASE_DIRECTORY_HELP = "help/"

        const val HELP_SCHEME: String =
            "help:" //zzhelp, support for help screens (proof of concept)

        private const val SAVE_currentItem_ID = "currentitem"
        private const val SAVE_helpfilename_ID = "filename"

        private const val dark_style =
            "body{background-color:#%06x;color:#%06x;}A{color:#%06x;}" //zzdark
        private const val help_style =
            "#main{display:flex;}#section1{order:1;margin:10px}#section2{order:2;}@media screen and (max-width: 560px) {#main{flex-wrap:wrap;}} img{max-width:100%;height:auto; display:block;margin-left:auto;margin-right:auto} A{text-decoration:none}"
    }
}
