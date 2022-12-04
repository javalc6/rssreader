package tools;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.slider.Slider;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;
import livio.rssreader.R;

import static com.google.android.material.slider.LabelFormatter.LABEL_GONE;

public final class SeekBarDialog extends PreferenceDialogFragmentCompat {
    private final static String tag = "SeekBarDialog";
//    private boolean mPreferenceChanged;
    private Slider mSlider;

    private int mMinProgress;
    private int mMaxProgress;
    private int mDefault;
    private int mInterval = 1;
    private boolean modePercentage = false;
    private String mSuffix;
    private int value;

    public static SeekBarDialog newInstance(String key) {
        SeekBarDialog fragment = new SeekBarDialog();
        Bundle b = new Bundle(1);
        b.putString("key", key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SeekBarPreference preference = (SeekBarPreference) getPreference();
        if (savedInstanceState != null) {
            value = savedInstanceState.getInt("value");
        } else {
            value = preference.getValue();
        }
        mMinProgress = preference.getMinProgress();
        mMaxProgress = preference.getMaxProgress();
        mDefault = preference.getDefault();
        mSuffix = preference.getSuffix();
        modePercentage = preference.getmodePercentage();
        mInterval = preference.getInterval();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("value", value);
    }

    private int getThemeColor(int id) {//da estendere alle varie activities
        TypedValue typedValue = new TypedValue();
        Context context = getActivity();
        Resources.Theme theme = context.getTheme();
        if (theme.resolveAttribute(id, typedValue, true)) {
            return context.getResources().getColor(typedValue.resourceId, theme);
        } else {
            return Color.TRANSPARENT;
        }
    }

    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        // set layout
        Context context = builder.getContext();
        View dialogView = LayoutInflater.from(context).inflate(R.layout.seekbar_dialog, null, false);
        mSlider = dialogView.findViewById(R.id.seek_bar);
        final TextView valueText = dialogView.findViewById(R.id.text_progress);
        valueText.setTextColor(getThemeColor(android.R.attr.textColorSecondary));


        mSlider.addOnChangeListener((seekBar, newValue, fromUser) -> {
//                mPreferenceChanged = true;
/*rimosso dopo avere convertito SeekBar in Slider
            if(mInterval != 1 && newValue % mInterval != 0)
                newValue = Math.round(newValue / mInterval) * mInterval;
*/
            String progressStr;
            if (modePercentage)
                progressStr = String.valueOf((newValue) * 100 / mDefault);
            else progressStr = String.valueOf(newValue);
            valueText.setText(mSuffix == null ? progressStr : progressStr.concat(mSuffix));
            Log.d(tag, "onProgressChanged.value = "+(newValue));
        });
        if (modePercentage) {//in case of percentage we have to change the text displayed in the bubble on the slider
            mSlider.setLabelFormatter(value -> String.valueOf((value) * 100 / mDefault));
        }
        mSlider.setValueFrom(mMinProgress);
        mSlider.setValueTo(mMaxProgress);
        mSlider.setValue(value);
        mSlider.setStepSize(mInterval);
        mSlider.setLabelBehavior(LABEL_GONE);
        Log.d(tag, "setValue = " + value);
        builder.setView(dialogView);

    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            SeekBarPreference preference = (SeekBarPreference) getPreference();
/*rimosso dopo avere convertito SeekBar in Slider
            if(mInterval != 1 && progress % mInterval != 0)
                progress = Math.round(((float)progress)/mInterval)*mInterval;
            int value = progress + mMinProgress;
 */
            int value = (int) mSlider.getValue();
            if (preference.callChangeListener(value)) {
                preference.setValue(value);
            }
        }
    }
}