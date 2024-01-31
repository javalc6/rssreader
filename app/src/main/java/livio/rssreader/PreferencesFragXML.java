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
import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import tools.FormFactorUtils;
import tools.SeekBarDialog;
import tools.SeekBarPreference;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import static livio.rssreader.RSSReader.PREF_DT_GENERICCOLOR;
import static livio.rssreader.RSSReader.PREF_DT_HYPERLINKCOLOR;
import static livio.rssreader.RSSReader.PREF_DT_TEXTCOLOR;
import static livio.rssreader.RSSReader.PREF_FEEDS_LANGUAGE;
import static livio.rssreader.RSSReader.PREF_FEED_ID;
import static livio.rssreader.RSSReader.PREF_FONTSIZE;
import static livio.rssreader.RSSReader.PREF_LT_GENERICCOLOR;
import static livio.rssreader.RSSReader.PREF_LT_HYPERLINKCOLOR;
import static livio.rssreader.RSSReader.PREF_LT_TEXTCOLOR;
import static livio.rssreader.RSSReader.PREF_REFRESH_TIMER;
import static livio.rssreader.RSSReader.PREF_THEME;
import static livio.rssreader.RSSReader.PREF_THEME_AUTO;
import static livio.rssreader.SelectColors.setNightMode;

public final class PreferencesFragXML extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.showpreferences);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new PrefsFragment())
                    .commit();
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!FormFactorUtils.isArc(this));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

/* zztabletlandscape: questo codice è alternativo al codice onCreateView
// nota: viene ridotta solo la larghezza sia dell'action bar che del contenuto della finestra sui tablet in modalità landscape
    @Override
    public void onStart() {//14-11-2022: narrow panel in case of table in landscape mode
        super.onStart();
//        Log.d("onStart", "onStart");
        if (!tools.FormFactorUtils.isRunningOnWindows()) {//28-01-2023: check that we are not running on windows to avoid weird behavior
            Resources resources = getResources();
            if (resources.getBoolean(R.bool.is_tablet_landscape)) {//isTablet && isLandscape
//		int dialogHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
                int dialogWidth = (int) (resources.getDisplayMetrics().widthPixels * 0.6);
                getWindow().setLayout(dialogWidth, WindowManager.LayoutParams.MATCH_PARENT);
            }
        }
    }
*/
    public static class PrefsFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener{

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
    		prefs.registerOnSharedPreferenceChangeListener(this);

            setFontSizeSummary();//twin
        }

// zztabletlandscape: questo codice è alternativo al codice onStart
// nota: viene ridotta solo la larghezza del contenuto della finestra sui tablet in modalità landscape
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View view = super.onCreateView(inflater, container, savedInstanceState);
            if (!tools.FormFactorUtils.isRunningOnWindows()) {//28-01-2023: check that we are not running on windows to avoid weird behavior
                Resources resources = getResources();
                if (resources.getBoolean(R.bool.is_tablet_landscape)) {//isTablet && isLandscape
                    int padding = (int) (resources.getDisplayMetrics().widthPixels * 0.2);
                    view.setPadding(padding, 0, padding, 0);
                }
            }
            return view;
        }

        private void setFontSizeSummary() {
            SeekBarPreference fontsizePreference = findPreference(PREF_FONTSIZE);
            fontsizePreference.setSummaryProvider((Preference.SummaryProvider<SeekBarPreference>) preference -> {
                return preference.getValue() * 100 / 16 + " %";//fontsize percentage as summary
            });
        }

        private static final String DIALOG_FRAGMENT_TAG = "SeekBarDialogPreference";

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
// check if dialog is already showing
            if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                return;
            }
            if (preference instanceof SeekBarPreference) {
                final DialogFragment f = SeekBarDialog.newInstance(preference.getKey());
                f.setTargetFragment(this, 0);
                f.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        	if  (getActivity() == null)
        		return;
    		BackupManager bm = new BackupManager(getActivity());
    		bm.dataChanged(); // Notifies the Android backup system
/* note: RESULT_OK is used to notify that UI should be refreshed.
   Importante: Non usare esplitamente RESULT_CANCELED, in quanto potrebbe invalidare il refresh dell'UI qualora l'utente cambiasse diverse preferenze, alcune che richiedono il refresh ed altre no.
 */
            switch (key) {
                case PREF_REFRESH_TIMER:
//                    setRefreshTimerSummary();
                    getActivity().setResult(RESULT_OK);
                    break;
                case PREF_FONTSIZE:
                    setFontSizeSummary();//twin
                    getActivity().setResult(RESULT_OK);
                    break;
                case PREF_THEME:
                    if (!setNightMode(prefs))
                        getActivity().setResult(RESULT_OK);//refresh UI for dark<->black and light<->white changes
                    break;
                case PREF_THEME_AUTO://zzautotheme
                    setNightMode(prefs);
//                    getActivity().setResult(RESULT_OK); questo non serve, il refresh UI è forzato già da setNightMode()
                    break;
                case PREF_LT_TEXTCOLOR:
                case PREF_LT_HYPERLINKCOLOR:
                case PREF_LT_GENERICCOLOR:
                case PREF_DT_TEXTCOLOR:
                case PREF_DT_HYPERLINKCOLOR:
                case PREF_DT_GENERICCOLOR:
                    getActivity().setResult(RESULT_OK);
                    break;
                case PREF_FEEDS_LANGUAGE:
//                    setLanguageSummary();
                    break;
                case PREF_FEED_ID:
                    getActivity().setResult(RESULT_OK);
                    getActivity().finish();
                    break;
            }
        }

    }

}

