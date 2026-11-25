package livio.rssreader

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tools.ColorBase
import tools.ColorPickerDialog
import tools.ColorPickerDialog.OnColorChangedListener

class SelectColors : AppCompatDialogFragment(), OnItemClickListener {
    private var mAdapter: ColorArrayAdapter? = null
    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var ci0: ColorItem? = null
    private var ci1: ColorItem? = null
    private var ci2: ColorItem? = null
    private var mColorDialog: ColorPickerDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDark = isDarkTheme(prefs, resources)
        setStyle(STYLE_NORMAL, if (isDark) R.style.MyDialogTheme_Dark else R.style.MyDialogTheme_Light)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val isTablet = resources.getBoolean(R.bool.is_tablet)

            if (isTablet) {
                val maxWidthDp = 440
                val widthPercent = 0.60f
                val maxWidthPx = (maxWidthDp * displayMetrics.density).toInt()
                val targetWidth = minOf((displayMetrics.widthPixels * widthPercent).toInt(), maxWidthPx)
                window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
    }

    @SuppressLint("NewApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.select_colors, container, false)
        dialog?.setTitle(R.string.prefs_theme)

        val theme: Int = get_theme_idx(prefs, resources)
        val theme_names = resources.getStringArray(R.array.prefs_theme_names)
        val mThemes = view.findViewById<Button>(R.id.themes)
        mThemes.tag = theme

        val theme_auto = view.findViewById<SwitchCompat>(R.id.theme_auto) //zzautotheme
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { //zzautotheme
            theme_auto.visibility = View.GONE //theme auto is not available before Android O
        } else {
            theme_auto.setChecked(prefs.getBoolean(RSSReader.PREF_THEME_AUTO, true))
            theme_auto.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                prefs.edit {
                    putBoolean(RSSReader.PREF_THEME_AUTO, isChecked)
                }
                setNightMode(prefs)
                activity?.recreate()
            }
        }

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && prefs.getBoolean(
                RSSReader.PREF_THEME_AUTO,
                true
            )
        ) { //zzautotheme
            mThemes.setText(R.string.auto)
            mThemes.isEnabled = false
        } else {
            mThemes.text = theme_names[theme]
            mThemes.isEnabled = true
            mThemes.setOnClickListener { _: View? ->
                val ctheme = mThemes.tag as Int
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.prefs_theme)
                    .setNegativeButton(
                        android.R.string.cancel
                    ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .setSingleChoiceItems(
                        theme_names,
                        ctheme
                    ) { dialog: DialogInterface, item: Int ->
                        if (item != ctheme) {
                            prefs.edit {
                                putString(RSSReader.PREF_THEME, ColorBase.themes[item])
                            }
                            val colors: IntArray =
                                getThemeColors(prefs, resources) //zzdt
                            ci0?.setColor(colors[1])
                            ci1?.setColor(colors[2])
                            ci2?.setColor(colors[3])
                            mAdapter?.notifyDataSetChanged()

                            mThemes.text = theme_names[item]
                            mThemes.tag = item

                            if (setNightMode(prefs)) {
                                activity?.recreate()
                            }
                        }
                        dialog.dismiss() // dismiss the alertbox after chose option
                    }.show()
            }
        }

        val catList: ArrayList<ColorItem?> = ArrayList()
        val colors: IntArray = getThemeColors(prefs, resources) //zzdt
        catList.add(ColorItem(getString(R.string.textColor), colors[1]).also {
            ci0 = it
        }) // pos = 0
        catList.add(ColorItem(getString(R.string.hyperlinkColor), colors[2]).also {
            ci1 = it
        }) // pos = 1
        catList.add(ColorItem(getString(R.string.genericColor), colors[3]).also {
            ci2 = it
        }) // pos = 2
        // Create a customized ArrayAdapter
        mAdapter = ColorArrayAdapter(
            requireContext(), R.layout.color_listitem, catList
        )

        val lv = view.findViewById<ListView>(R.id.itemlist)

        // Set the ListView adapter
        lv.adapter = mAdapter
        lv.onItemClickListener = this

        //zzcolor 16-03-2016
        val reset_colors = view.findViewById<Button>(R.id.reset_colors)
        reset_colors.setOnClickListener { _: View? ->
            val ctheme = mThemes.tag as Int
            resetTheme(ctheme) //reset colors to default value
            val colors1: IntArray = getThemeColors(prefs, resources) //zzdt
            ci0?.setColor(colors1[1])
            ci1?.setColor(colors1[2])
            ci2?.setColor(colors1[3])
            mAdapter?.notifyDataSetChanged()
        }

        return view
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, rowId: Long) {
        if ((position >= 0) && (position < 3)) {
            val context = context ?: return
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                ?: return
            // get theme_idx
            val theme_idx: Int = get_theme_idx(prefs, resources)

            val color: Int
            val title: Int
            val preset_idx = position + 1
            when (preset_idx) {
                1 -> {
                    color = prefs.getInt(
                        if (theme_idx < 2) RSSReader.PREF_LT_TEXTCOLOR else RSSReader.PREF_DT_TEXTCOLOR,
                        ColorBase.preset_colors[theme_idx][preset_idx]
                    )
                    title = R.string.textColor
                }

                2 -> {
                    color = prefs.getInt(
                        if (theme_idx < 2) RSSReader.PREF_LT_HYPERLINKCOLOR else RSSReader.PREF_DT_HYPERLINKCOLOR,
                        ColorBase.preset_colors[theme_idx][preset_idx]
                    )
                    title = R.string.hyperlinkColor
                }

                3 -> {
                    color = prefs.getInt(
                        if (theme_idx < 2) RSSReader.PREF_LT_GENERICCOLOR else RSSReader.PREF_DT_GENERICCOLOR,
                        ColorBase.preset_colors[theme_idx][preset_idx]
                    )
                    title = R.string.genericColor
                }

                else -> {
                    Log.e(tag, "incorrect preset_idx:$preset_idx")
                    return  // error !
                }
            }
            val background = ColorBase.preset_colors[theme_idx][0]
            val colors =
                IntArray(ColorBase.color_wheel_mod.size) // tavolozza finale da mostrare all'utente
            if (preset_idx == 3) { //generic_color
                for (i in ColorBase.color_wheel_mod.indices) {
                    val color_i = ColorBase.color_wheel_mod[i]
                    colors[i] = -0x1000000 or color_i //0xff000000 is required to provide luminance
                }
            } else { //text_color or hyperlink_color
                for (i in ColorBase.color_wheel_mod.indices) {
                    var color_i = ColorBase.color_wheel_mod[i]
                    if (ColorBase.luminance(color_i) >= 128) color_i = ColorBase.half_color(color_i)
                    if (ColorBase.isDarkColor(background)) colors[i] =
                        ColorBase.invert_color(color_i)
                    else colors[i] =
                        -0x1000000 or color_i //0xff000000 is required to provide luminance
                }
            }

            dismissColorDialog()
            mColorDialog = ColorPickerDialog(
                requireContext(),
                color,
                resources.getString(title),
                colors,
                ColorPickerDialog.CIRCLE_CIRCLE,
                object : OnColorChangedListener {
                    override fun colorChanged(color: Int) {
                        prefs.edit {
                            when (preset_idx) {
                                1 -> putInt(
                                    if (theme_idx < 2) RSSReader.PREF_LT_TEXTCOLOR else RSSReader.PREF_DT_TEXTCOLOR,
                                    color
                                )

                                2 -> putInt(
                                    if (theme_idx < 2) RSSReader.PREF_LT_HYPERLINKCOLOR else RSSReader.PREF_DT_HYPERLINKCOLOR,
                                    color
                                )

                                3 -> putInt(
                                    if (theme_idx < 2) RSSReader.PREF_LT_GENERICCOLOR else RSSReader.PREF_DT_GENERICCOLOR,
                                    color
                                )
                            }
                        }
                        ViewCompat.setBackgroundTintList(view.findViewById(R.id.item_color), ColorStateList.valueOf(color))
                    }
                }
            )
            mColorDialog?.show()
        }
    }

    override fun onStop() {
        super.onStop()
        dismissColorDialog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissColorDialog()
    }

    private fun dismissColorDialog() {
        mColorDialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        mColorDialog = null
    }

    private fun resetTheme(theme_idx: Int) { //zzcolor 16-03-2016
        prefs.edit {
            putString(RSSReader.PREF_THEME, ColorBase.themes[theme_idx])
            putInt(
                if (theme_idx < 2) RSSReader.PREF_LT_TEXTCOLOR else RSSReader.PREF_DT_TEXTCOLOR,
                ColorBase.preset_colors[theme_idx][1]
            )
            putInt(
                if (theme_idx < 2) RSSReader.PREF_LT_HYPERLINKCOLOR else RSSReader.PREF_DT_HYPERLINKCOLOR,
                ColorBase.preset_colors[theme_idx][2]
            )
            putInt(
                if (theme_idx < 2) RSSReader.PREF_LT_GENERICCOLOR else RSSReader.PREF_DT_GENERICCOLOR,
                ColorBase.preset_colors[theme_idx][3]
            )
        }
    }

    private class ColorItem {
        var name: String? = null
        var resourceId: Int = 0

        constructor()

        constructor(name: String, color: Int) {
            this.name = name
            this.resourceId = color
        }

        fun setColor(color: Int) {
            this.resourceId = color
        }

        override fun toString(): String {
            return this.name!!
        }
    }

    private class ColorArrayAdapter(
        context: Context, private val resourceid: Int,
        private val colorItems: MutableList<ColorItem?>
    ) : ArrayAdapter<ColorItem?>(
        context,
        resourceid,
        colorItems
    ) {
        override fun getCount(): Int {
            return this.colorItems.size
        }

        override fun getItem(index: Int): ColorItem? {
            return this.colorItems[index]
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var row = convertView
            if (row == null) {
                // ROW INFLATION
                val inflater = this.context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                row = inflater.inflate(resourceid, parent, false)
            }

            // Get item
            val iconItem = getItem(position)

            (row.findViewById<View>(R.id.item_name) as TextView).text = iconItem!!.name
            ViewCompat.setBackgroundTintList(row.findViewById(R.id.item_color), ColorStateList.valueOf(iconItem.resourceId))
            return row
        }
    }

    companion object {
        private const val tag = "SelectColors"
        @JvmStatic
        fun getThemeColors(
            prefs: SharedPreferences,
            resources: Resources
        ): IntArray { //zznight: retrieve theme colors
            val colors = IntArray(4)
            val theme_idx: Int = get_theme_idx(prefs, resources)
            colors[0] = ColorBase.preset_colors[theme_idx][0] //background
            colors[1] = prefs.getInt(
                if (theme_idx < 2) RSSReader.PREF_LT_TEXTCOLOR else RSSReader.PREF_DT_TEXTCOLOR,
                ColorBase.preset_colors[theme_idx][1]
            ) //text_color
            colors[2] = prefs.getInt(
                if (theme_idx < 2) RSSReader.PREF_LT_HYPERLINKCOLOR else RSSReader.PREF_DT_HYPERLINKCOLOR,
                ColorBase.preset_colors[theme_idx][2]
            ) //hyperlink_color
            colors[3] = prefs.getInt(
                if (theme_idx < 2) RSSReader.PREF_LT_GENERICCOLOR else RSSReader.PREF_DT_GENERICCOLOR,
                ColorBase.preset_colors[theme_idx][3]
            ) //generic_color
            return colors
        }

        //zznight: dark theme handling compatible with Android P and later
        @JvmStatic
        fun get_theme_idx(
            prefs: SharedPreferences,
            resources: Resources
        ): Int { //zzdt: returns theme_idx
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && prefs.getBoolean(
                    RSSReader.PREF_THEME_AUTO,
                    true
                )
            ) { //zzautotheme
                val nightModeFlags =
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK //zznight
                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES)  //zznight
                    return 2 //dark gray
                else return 1 //light gray
            } else return ColorBase.get_theme_idx(prefs.getString(RSSReader.PREF_THEME, "light"))
        }

        @JvmStatic
        fun isDarkTheme(prefs: SharedPreferences, resources: Resources): Boolean { //zznight
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && prefs.getBoolean(
                    RSSReader.PREF_THEME_AUTO,
                    true
                )
            ) { //zzautotheme
                val nightModeFlags =
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK //zznight
                return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            } else {
                val theme_idx: Int = get_theme_idx(prefs, resources)
                val background = ColorBase.preset_colors[theme_idx][0]
                return ColorBase.isDarkColor(background)
            }
        }

        //setNightMode() returns true if setDefaultNightMode() has been called to recreate activities
        @JvmStatic
        fun setNightMode(prefs: SharedPreferences): Boolean { //must be called by any activities that changes values of PREF_THEME_AUTO and/or PREF_THEME
            val night_mode: Int
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && prefs.getBoolean(
                    RSSReader.PREF_THEME_AUTO,
                    true
                )
            ) night_mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else {
                val theme: String = prefs.getString(RSSReader.PREF_THEME, "light")!!
                if (ColorBase.is_dark_theme(theme)) night_mode = AppCompatDelegate.MODE_NIGHT_YES
                else if (Build.VERSION.SDK_INT_FULL <= Build.VERSION_CODES_FULL.BAKLAVA) night_mode =
                    AppCompatDelegate.MODE_NIGHT_NO
                else return false //zzexpand
            }
            if (AppCompatDelegate.getDefaultNightMode() != night_mode) {
                AppCompatDelegate.setDefaultNightMode(night_mode)
                return true
            } else return false
        }
    }
}