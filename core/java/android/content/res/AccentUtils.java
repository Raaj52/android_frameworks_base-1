package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static ArrayList<String> accentResources = new ArrayList<>(
            Arrays.asList("colorAccent",
                    "accent_device_default",
                    "accent_device_default_light",
                    "accent_device_default_dark",
                    "material_pixel_blue_dark",
                    "material_pixel_blue_bright",
                    "holo_blue_light",
                    "holo_blue_dark",
                    "omni_color5",
                    "omni_color4",
                    "dialer_theme_color",
                    "dialer_theme_color_dark",
                    "dialer_theme_color_20pct",
                    "qs_header_accent_color",
                    "lockscreen_clock_accent_color",
                    "recents_dismiss_all_background_color",
                    "gradient_start"));

    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";

    static boolean isResourceAccent(String resName) {
        for (String ar : accentResources)
            if (resName.contains(ar))
                return true;
        return false;
    }

    public static int getNewAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_COLOR_PROP);
    }

    private static int getAccentColor(int defaultColor, String property) {
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
