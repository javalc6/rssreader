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
import android.graphics.Color;

public final class ColorBase {
    public static final String [] themes = {"white", "ltgray", "dkgray", "black"};//DO NOT CHANGE THIS STATEMENT
/* preset_colors[theme_index][component_index]
theme_index:
    0: white
    1: light gray
    2: dark gray
    3: black
component_index:
    0: background color
    1: text color
    2: hyperlink color
    3: generic color
 */
    public static final int [][] preset_colors = {{Color.WHITE, Color.BLACK, Color.BLUE, Color.rgb(0, 0x40, 0xFF)},
            {Color.LTGRAY, Color.BLACK, Color.BLUE, Color.rgb(0, 0x40, 0xFF)},
            {Color.DKGRAY, Color.WHITE, Color.YELLOW, Color.rgb(0xFF, 0xBF, 0x00)},
            {Color.BLACK, Color.WHITE, Color.YELLOW, Color.rgb(0xFF, 0xBF, 0x00)}
    };

    public static final int preset_bookmarked_color = Color.YELLOW;

    //color wheel modified with black (note that 'black' is not part of standard color wheel)
    public static final int[] color_wheel_mod = {0xff0000, 0xff0040, 0xff0080, 0xff00bf, 0xff00ff, 0xbf00ff,
            0x8000ff, 0x4000ff, 0x000000, 0x0000ff, 0x0040ff, 0x0080ff, 0x00bfff,
            0x00ffff, 0x00ffbf, 0x00ff80, 0x00ff40, 0x00ff00, 0x40ff00,
            0x80ff00, 0xbfff00, 0xffff00, 0xffbf00, 0xff8000, 0xff4000};

    public static boolean isDarkColor(int color) {
        return Color.green(color) + Color.red(color) < 256;
    }

    public static int value(int color) { // 0..255
        return (Color.green(color) + Color.red(color) + Color.blue(color)) / 3;
    }

    public static int luminance(int color) { // 0..255
        return (299 * Color.red(color) + 587 * Color.green(color) + 114 * Color.blue(color)) / 1000;
    }

    public static int invert_color(int color) {
        return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color));
    }

    public static int half_color(int color) {
        return Color.rgb(Color.red(color) / 2, Color.green(color) / 2, Color.blue(color) / 2);
    }

    public static int to_gray(int base, int value) { // return a color with Gray coding
        int[] baseN = new int[3];
        int[] gray = new int[3];
        int i;

        for (i = 0; i < 3; i++) {
            baseN[i] = value % base;
            value    = value / base;
        }

        int shift = 0;
        while (i-- > 0) {
            int t = (baseN[i] + shift) % base;
            gray[i] = t * 255 / (base - 1);
            shift = shift + base - t;
        }
        return Color.rgb(gray[0], gray[1], gray[2]);
    }

    //colorpicker
    public static int get_theme_idx(String theme) { // returns theme_idx
        int theme_idx = themes.length;
        while (--theme_idx > 0)
            if (themes[theme_idx].equals(theme))
                break;
        return theme_idx;
    }

    public static boolean is_dark_theme(String theme) {
        return theme.equals(themes[2]) || theme.equals(themes[3]);
    }

    public static int force_dark_theme(int theme) {
        if (theme == 0)//white --> black
            return 3;
        if (theme == 1)//ltgray-->dkgray
            return 2;
        return theme;
    }

}
