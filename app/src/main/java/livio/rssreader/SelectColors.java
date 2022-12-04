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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import tools.ColorPickerDialog;

import static livio.rssreader.RSSReader.PREF_THEME;
import static tools.ColorBase.color_wheel_mod;
import static tools.ColorBase.half_color;
import static tools.ColorBase.invert_color;
import static tools.ColorBase.isDarkColor;
import static tools.ColorBase.luminance;
import static tools.ColorBase.preset_colors;
import static tools.ColorBase.themes;
import static tools.ColorPickerDialog.CIRCLE_CIRCLE;

public final class SelectColors extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String tag = "SelectColors";
    private Button mThemes;
    private ColorArrayAdapter mAdapter;
    private SharedPreferences prefs;
    private ColorItem ci0;
    private ColorItem ci1;
    private ColorItem ci2;

    @SuppressLint("NewApi")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.select_colors);

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK; //night

        int theme = get_theme_idx(prefs, getResources());
        String[] theme_names = getResources().getStringArray(R.array.prefs_theme_names);
        mThemes = findViewById(R.id.themes);
        mThemes.setTag(theme);
        if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) && (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O))//night
            mThemes.setText(R.string.nightmode);
        else {
            mThemes.setText(theme_names[theme]);
            mThemes.setOnClickListener(v -> {
                int ctheme = (Integer) mThemes.getTag();
                new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setSingleChoiceItems(theme_names, ctheme, (dialog, item) -> {
//                Log.d(tag, "clicked theme: "+item+", previous theme: "+ctheme);
                    if (item != ctheme) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(PREF_THEME, themes[item]);
                        editor.apply();
                        int[] colors = getThemeColors(prefs, getResources());//dt
                        ci0.setColor(colors[1]);
                        ci1.setColor(colors[2]);
                        ci2.setColor(colors[3]);
                        mAdapter.notifyDataSetChanged();

                        mThemes.setText(theme_names[item]);
                        mThemes.setTag(item);
                    }
                    dialog.dismiss();// dismiss the alertbox after chose option
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
            });
        }

        ArrayList<ColorItem> catList = new ArrayList<>();
        int[] colors = getThemeColors(prefs, getResources());//dt
        catList.add(ci0 = new ColorItem(getString(R.string.textColor), colors[1]));// pos = 0
        catList.add(ci1 = new ColorItem(getString(R.string.hyperlinkColor), colors[2]));// pos = 1
        catList.add(ci2 = new ColorItem(getString(R.string.genericColor), colors[3]));// pos = 2

        // Create a customized ArrayAdapter
        mAdapter = new ColorArrayAdapter(
                this, R.layout.color_listitem, catList);

        final ListView lv = findViewById(R.id.itemlist);
        lv.setAdapter(mAdapter);
        lv.setOnItemClickListener(this);

        Button reset_colors = findViewById(R.id.reset_colors);
        reset_colors.setOnClickListener(v -> {
// Perform action on clicks
//                Log.d(tag, "id:" + mThemes.getSelectedItemId());
            int ctheme = (Integer) mThemes.getTag();
            resetTheme(ctheme); //reset colors to default value
            int[] colors1 = getThemeColors(prefs, getResources());//dt
            ci0.setColor(colors1[1]);
            ci1.setColor(colors1[2]);
            ci2.setColor(colors1[3]);
            mAdapter.notifyDataSetChanged();
        });
    }

    public static int[] getThemeColors(SharedPreferences prefs, Resources resources) { //night: retrieve theme colors
        int[] colors = new int[4];
        int theme_idx = get_theme_idx(prefs, resources);
        colors[0] = preset_colors[theme_idx][0];//background
        colors[1] = prefs.getInt(theme_idx < 2 ? RSSReader.PREF_LT_TEXTCOLOR : RSSReader.PREF_DT_TEXTCOLOR, preset_colors[theme_idx][1]);//text_color
        colors[2] = prefs.getInt(theme_idx < 2 ? RSSReader.PREF_LT_HYPERLINKCOLOR : RSSReader.PREF_DT_HYPERLINKCOLOR, preset_colors[theme_idx][2]);//hyperlink_color
        colors[3] = prefs.getInt(theme_idx < 2 ? RSSReader.PREF_LT_GENERICCOLOR : RSSReader.PREF_DT_GENERICCOLOR, preset_colors[theme_idx][3]);//generic_color
        return colors;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) {
        if ((position >= 0) && (position < 3)) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parent.getContext());
            if (prefs == null) // main activity died
                return;
            int theme_idx = get_theme_idx(prefs, getResources());

//    	Log.d(tag, "theme:"+theme_idx);//dt theme_idx < 2 ==> light theme
            int color, title;
            final int preset_idx = position + 1;
            switch (preset_idx) {
                case 1: //text_color
                    color = prefs.getInt(theme_idx < 2 ? RSSReader.PREF_LT_TEXTCOLOR : RSSReader.PREF_DT_TEXTCOLOR, preset_colors[theme_idx][preset_idx]);
                    title = R.string.textColor;
                    break;
                case 2: //hyperlink_color
                    color = prefs.getInt(theme_idx < 2 ? RSSReader.PREF_LT_HYPERLINKCOLOR : RSSReader.PREF_DT_HYPERLINKCOLOR, preset_colors[theme_idx][preset_idx]);
                    title = R.string.hyperlinkColor;
                    break;
                case 3: //generic_color
                    color = prefs.getInt(theme_idx < 2 ? RSSReader.PREF_LT_GENERICCOLOR : RSSReader.PREF_DT_GENERICCOLOR, preset_colors[theme_idx][preset_idx]);
                    title = R.string.genericColor;
                    break;
                default:
                    Log.e(tag, "incorrect preset_idx:" + preset_idx);
                    return; // error !

            }
            int background = preset_colors[theme_idx][0];
            int[] colors = new int[color_wheel_mod.length]; // tavolozza finale da mostrare all'utente
            if (preset_idx == 3) {//generic_color
                for (int i = 0; i < color_wheel_mod.length; i++) {
                    int color_i = color_wheel_mod[i];
                    colors[i] = 0xff000000 | color_i;//0xff000000 is required to provide luminance
                }
            } else {//text_color or hyperlink_color
                for (int i = 0; i < color_wheel_mod.length; i++) {
                    int color_i = color_wheel_mod[i];
                    if (luminance(color_i) >= 128)
                        color_i = half_color(color_i);
                    if (isDarkColor(background))
                        colors[i] = invert_color(color_i);
                    else colors[i] = 0xff000000 | color_i;//0xff000000 is required to provide luminance
                }
            }

            ColorPickerDialog colordlg = new ColorPickerDialog(this, color, getResources().getString(title), colors, CIRCLE_CIRCLE, color1 -> {
                SharedPreferences.Editor editor = prefs.edit();
                switch (preset_idx) {
                    case 1: //text_color
                        editor.putInt(theme_idx < 2 ? RSSReader.PREF_LT_TEXTCOLOR : RSSReader.PREF_DT_TEXTCOLOR, color1);
                        break;
                    case 2: //hyperlink_color
                        editor.putInt(theme_idx < 2 ? RSSReader.PREF_LT_HYPERLINKCOLOR : RSSReader.PREF_DT_HYPERLINKCOLOR, color1);
                        break;
                    case 3: //generic_color
                        editor.putInt(theme_idx < 2 ? RSSReader.PREF_LT_GENERICCOLOR : RSSReader.PREF_DT_GENERICCOLOR, color1);
                        break;
                }
                editor.apply();
                view.findViewById(R.id.item_color).setBackgroundColor(color1);
            });
            colordlg.show();
        }

    }


    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void resetTheme(int theme_idx) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_THEME, themes[theme_idx]);
        editor.putInt(theme_idx < 2 ? RSSReader.PREF_LT_TEXTCOLOR : RSSReader.PREF_DT_TEXTCOLOR, preset_colors[theme_idx][1]);
        editor.putInt(theme_idx < 2 ? RSSReader.PREF_LT_HYPERLINKCOLOR : RSSReader.PREF_DT_HYPERLINKCOLOR, preset_colors[theme_idx][2]);
        editor.putInt(theme_idx < 2 ? RSSReader.PREF_LT_GENERICCOLOR : RSSReader.PREF_DT_GENERICCOLOR, preset_colors[theme_idx][3]);
        editor.apply();
    }

    //night: dark theme handling compatible with Android P and later
    public static int get_theme_idx(SharedPreferences prefs, Resources resources) { //dt: returns theme_idx
        int theme = tools.ColorBase.get_theme_idx(prefs.getString(PREF_THEME, "white"));
        int nightModeFlags = resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK; //night
//        Log.d(tag, "nightModeFlags="+nightModeFlags);
        if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) && (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O))//night
            return tools.ColorBase.force_dark_theme(theme);//night, night mode-->force dark theme
        return theme;
    }

    public static boolean isDarkTheme(SharedPreferences prefs, Resources resources) {//night
        int nightModeFlags = resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK; //night
//        Log.d(tag, "nightModeFlags="+nightModeFlags);
        if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) && (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O))//night
            return true;//night, modo notte
        else {
            int theme_idx = get_theme_idx(prefs, resources);
            int background = preset_colors[theme_idx][0];
            return isDarkColor(background);
        }
    }

    private static class ColorItem {
        String name;
        int resourceId;

        @SuppressWarnings("unused")
        public ColorItem() {
            //Auto-generated constructor stub
        }

        ColorItem(@NonNull String name, int color) {
            this.name = name;
            this.resourceId = color;
        }

        void setColor(int color) {
            this.resourceId = color;
        }

        @NonNull
        @Override
        public String toString() {
            return this.name;
        }
    }

    private class ColorArrayAdapter extends ArrayAdapter<ColorItem> {

        private final int resourceid;

        private final List<ColorItem> colorItems;

        ColorArrayAdapter(Context context, int textViewResourceId,
                          List<ColorItem> objects) {
            super(context, textViewResourceId, objects);
            this.colorItems = objects;
            this.resourceid = textViewResourceId;
        }

        public int getCount() {
            return this.colorItems.size();
        }

        public ColorItem getItem(int index) {
            return this.colorItems.get(index);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                // ROW INFLATION
                LayoutInflater inflater = (LayoutInflater) this.getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(resourceid, parent, false);
            }

            // Get item
            ColorItem iconItem = getItem(position);

            ((TextView) row.findViewById(R.id.item_name)).setText(iconItem.name);
            row.findViewById(R.id.item_color).setBackgroundColor(iconItem.resourceId);
            return row;
        }

    }


}
