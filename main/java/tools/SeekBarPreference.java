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
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import livio.rssreader.R;

/* example of usage:

     <PreferenceCategory android:title="Fonts">
        <tools.SeekBarPreference
        	android:key="font_size"
        	android:title="Font Size"
            android:summary="optional summary"
        	android:dialogTitle="Font Size"
			android:dialogMessage="Choose font size:"
			android:defaultValue="16" 	<-- required default value
			android:max="26"			<-- required max value
			app:min="12"				<-- required min value
			app:displayMode="percentage"<-- optional: used to print the value as percentage compared to android:defaultValue
			app:displaySuffix="%"		<-- optional
            app:interval="2" 			<-- optional
            />
    </PreferenceCategory>

 */

public final class SeekBarPreference extends DialogPreference {
    private final static String tag = "SeekBarDialog";
    private static final int DEFAULT_MIN_PROGRESS = 0;
    private static final int DEFAULT_MAX_PROGRESS = 100;
    private static final int DEFAULT_PROGRESS = 50;

    private int mMinProgress;
    private int mMaxProgress;
    private int mProgress;
    private int mDefault;
    private int mInterval = 1;
    private boolean modePercentage = false;
    private String mSuffix;

    private int value;

    public SeekBarPreference(Context context) {
        this(context, null);
    }

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        // get attributes specified in XML
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SeekBarDialogPreference, 0, 0);
        try {
            mMinProgress = a.getInteger(R.styleable.SeekBarDialogPreference_min, DEFAULT_MIN_PROGRESS);
            mMaxProgress = a.getInteger(R.styleable.SeekBarDialogPreference_android_max, DEFAULT_MAX_PROGRESS);
            mDefault = a.getInteger(R.styleable.SeekBarDialogPreference_android_defaultValue, DEFAULT_PROGRESS);
            mSuffix = a.getString(R.styleable.SeekBarDialogPreference_displaySuffix);
            if ("percentage".equals(a.getString(R.styleable.SeekBarDialogPreference_displayMode)))
                modePercentage = true;
            String newInterval = a.getString(R.styleable.SeekBarDialogPreference_interval);
            if(newInterval != null)
                mInterval = Integer.parseInt(newInterval);
//            Log.d(tag, "init:"+mMinProgress+":"+mMaxProgress+":"+mDefault+":"+mSuffix+":"+modePercentage+":"+mInterval);
        } finally {
            a.recycle();
        }
    }

    public void setValue(int val) {
        value = val;
        persistInt(val);
//        Log.d(tag, "setValue = "+value);
    }

    public int getValue() {
        return value;
    }

    public int getMinProgress() {
        return mMinProgress;
    }
    public int getMaxProgress() {
        return mMaxProgress;
    }
    public int getDefault() {
        return mDefault;
    }
    public String getSuffix() {
        return mSuffix;
    }
    public boolean getmodePercentage() {
        return modePercentage;
    }
    public int getInterval() {
        return mInterval;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
//        setValue(getPersistedInt((Integer) defaultValue));-->Null Pointer exception
        setValue(getPersistedInt(mDefault));
    }


    @Override
    protected Parcelable onSaveInstanceState() {
        // save the instance state so that it will survive screen orientation changes and other events that may temporarily destroy it
        final Parcelable superState = super.onSaveInstanceState();

        // set the state's value with the class member that holds current setting value
        final SavedState myState = new SavedState(superState);
        myState.minProgress = mMinProgress;
        myState.maxProgress = mMaxProgress;
        myState.progress = mProgress;

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // check whether we saved the state in onSaveInstanceState()
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // didn't save the state, so call superclass
            super.onRestoreInstanceState(state);
            return;
        }

        // restore the state
        SavedState myState = (SavedState) state;
        mMinProgress = myState.minProgress;
        mMaxProgress = myState.maxProgress;
        mProgress = myState.progress;

        super.onRestoreInstanceState(myState.getSuperState());
    }

    private static class SavedState extends BaseSavedState {
        int minProgress;
        int maxProgress;
        int progress;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);

            minProgress = source.readInt();
            maxProgress = source.readInt();
            progress = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeInt(minProgress);
            dest.writeInt(maxProgress);
            dest.writeInt(progress);
        }

        @SuppressWarnings("unused")
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}
