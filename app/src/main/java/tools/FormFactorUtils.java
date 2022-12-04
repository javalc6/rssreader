package tools;

import android.os.Build;

public final class FormFactorUtils {

    public static boolean isArc() {//check if we are running on Chromebook, reference: https://github.com/google/talkback/blob/e69d4731fce02bb9e69613d0e48c29033cad4a98/utils/src/main/java/FormFactorUtils.java#L42
        return Build.DEVICE != null && Build.DEVICE.matches(".+_cheets|cheets_.+");
    }
}
