package tools;

import android.content.Context;
import android.os.Build;

public final class FormFactorUtils {

    public static boolean isArc(Context ctx) {//check if we are running on Chromebook, reference: https://github.com/google/talkback/blob/e69d4731fce02bb9e69613d0e48c29033cad4a98/utils/src/main/java/FormFactorUtils.java#L42
        return (Build.DEVICE != null && Build.DEVICE.matches(".+_cheets|cheets_.+"))  || FeatureUtil.isArc(ctx);//05-04-2023: extended with  FeatureUtil.isArc(ctx)
    }

    public static boolean isRunningOnWindows() {//check if app is running under Windows 11
        return "Windows".equals(Build.BRAND) && "windows".equals(Build.BOARD);
    }

}
