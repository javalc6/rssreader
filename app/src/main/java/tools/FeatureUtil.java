package tools;

import android.content.Context;
import android.content.pm.PackageManager;

public class FeatureUtil {

    public static final String ARC_FEATURE = "org.chromium.arc";
    public static final String ARC_DEVICE_MANAGEMENT_FEATURE = "org.chromium.arc.device_management";

    /** Returns true if the device has any feature in a given collection of system features */
    public static boolean hasAnySystemFeature(Context ctx, String... features) {
        PackageManager pm = ctx.getPackageManager();
        for (String feature : features) {
            if (pm.hasSystemFeature(feature)) {
                return true;
            }
        }
        return false;
    }

    /** Returns {@code true} if device is an ARC++ device. */
    public static boolean isArc(Context ctx) {
        return hasAnySystemFeature(ctx, ARC_FEATURE, ARC_DEVICE_MANAGEMENT_FEATURE);
    }

}
