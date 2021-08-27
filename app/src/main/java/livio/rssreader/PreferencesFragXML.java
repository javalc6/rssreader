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
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceScreen;
import tools.SeekBarDialog;
import tools.SeekBarPreference;

import android.view.MenuItem;

import static livio.rssreader.RSSReader.PREF_FONTSIZE;

public final class PreferencesFragXML extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar t = getSupportActionBar();
		if (t != null) {
			t.setDisplayHomeAsUpEnabled(true);
		}
        
        if (savedInstanceState == null) {
        // Display the fragment as the main content.
	        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                new PrefsFragment()).commit();
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
    
    public static class PrefsFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener{
    	SharedPreferences prefs;
    	
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
           
            prefs = getPreferenceScreen().getSharedPreferences();

            PreferenceScreen preferenceScreen = getPreferenceScreen();
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) && (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                Preference theme = preferenceScreen.findPreference(RSSReader.PREF_THEME);
                theme.setSummary(R.string.nightmode);
            }
            prefs = preferenceScreen.getSharedPreferences();
    		prefs.registerOnSharedPreferenceChangeListener(this);

            setFontSizeSummary();//twin
        }

        private void setFontSizeSummary() {
            SeekBarPreference fontsizePreference = findPreference(PREF_FONTSIZE);
            fontsizePreference.setSummaryProvider((Preference.SummaryProvider<SeekBarPreference>) preference -> {
                return preference.getValue() * 100 / 16 + " %";//fontsize percentage as summary
            });
        }

        private static final String DIALOG_FRAGMENT_TAG = "SeekBarDialogPreference";

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
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

        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        	if  (getActivity() == null)
        		return;
    		BackupManager bm = new BackupManager(getActivity());
    		bm.dataChanged(); // Notifies the Android backup system
/* note: RESULT_OK is used to notify that UI should be refreshed.
   Importante: Non usare esplitamente RESULT_CANCELED, in quanto potrebbe invalidare il refresh dell'UI qualora l'utente cambiasse diverse preferenze, alcune che richiedono il refresh ed altre no.
 */
            switch (key) {
                case RSSReader.PREF_REFRESH_TIMER:
//                    setRefreshTimerSummary();
                    getActivity().setResult(RESULT_OK);
                    break;
                case PREF_FONTSIZE:
                    setFontSizeSummary();//twin
                    getActivity().setResult(RESULT_OK);
                    break;
                case RSSReader.PREF_THEME:
                case RSSReader.PREF_LT_TEXTCOLOR:
                case RSSReader.PREF_LT_HYPERLINKCOLOR:
                case RSSReader.PREF_LT_GENERICCOLOR:
                case RSSReader.PREF_DT_TEXTCOLOR:
                case RSSReader.PREF_DT_HYPERLINKCOLOR:
                case RSSReader.PREF_DT_GENERICCOLOR:
                    getActivity().setResult(RESULT_OK);
                    break;
                case RSSReader.PREF_FEEDS_LANGUAGE:
//                    setLanguageSummary();
                    break;
                case RSSReader.PREF_FEED_ID:
                    getActivity().setResult(RESULT_OK);
                    getActivity().finish();
                    break;
            }
        }

    }

}

