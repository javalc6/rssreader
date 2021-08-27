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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateParser {
    private static final SimpleDateFormat[] dateFormats = new SimpleDateFormat[] {//test.java contains useful tests-->test_date_parsing()
            new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),//parse HTTP date headers in RFC 1036 format.
            new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),//parse HTTP date headers in RFC 1123 format
            new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US),//parse HTTP date headers in ANSI C
            new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US),//aggiunto per supportare focus.it
//            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            new SimpleDateFormat("E, dd MMM yyyy HH:mm:ssZ", Locale.US),
            new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd", Locale.US),//last, do not move before!
    };

    public static Date parseDate(String pubdate)	{
        for (SimpleDateFormat format : dateFormats) {
            try {
                return format.parse(pubdate);
            } catch (ParseException pe) {
                // ignore this exception, we will try the next format
            }
        }
        return new Date(); //no format worked, use current date/time
    }

}
