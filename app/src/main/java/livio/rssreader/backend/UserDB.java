package livio.rssreader.backend;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import livio.rssreader.BuildConfig;
import livio.rssreader.R;
import livio.rssreader.RSSReader;

import static livio.rssreader.backend.FeedsDB.categories;

public final class UserDB {

    private static final String userDBfn = "userDB0.ser"; //new file, replaces feedListfn
/*
  file format:
    - number of enclosed objects
    - for each enclosed object: tag + payload
    - when the application read the file, it must stop reading as soon as an unknown tag is reached
 */
    private static final byte zUserFeeds = 1;//tag for userFeeds
    private static final byte zUserCats = 2;//tag for userCats

    private static final String tag = "UserDB";

    private static UserDB singleton;
    private static String pref_lang;

    public static final int FEED_SIZE = 5; // {title, url, feed_id, cat, timestamp} , non cambiare il numero di elementi, altrimenti salta la backward compatibility
    public static final int CAT_SIZE = 5; // {title, description, cat_id, dummy1, dummy2} , non cambiare il numero di elementi, altrimenti salta la backward compatibility

    private ArrayList<String[]> userFeeds;//user feeds, list of {title, url, feed_id, cat, timestamp}
    private ArrayList<String[]> userCats;//user categorys, list of {title, description, cat_id, dummy1, dummy2}, le dummy sono per usi futuri, todo: renderla privata aggiungendo metodi opportuni

    private static String[][][] nativeFeeds;
    public static String DEFAULT_FEED_ID;// the default feed_id can be used at start and in case of problems

    public synchronized static UserDB getInstance(Context context, SharedPreferences prefs) {
        FeedsDB feedsDB = FeedsDB.getInstance();
        if (singleton == null) {
            singleton = new UserDB(context, prefs, feedsDB);
        } else {
            String feed_lang = prefs.getString(RSSReader.PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
            if (!pref_lang.equals(feed_lang)) {//update according to effective feed language
                pref_lang = feed_lang;
                int lang_idx = feedsDB.getLanguageIndex(pref_lang);
                nativeFeeds = FeedsDB.nativeFeeds[lang_idx];
                DEFAULT_FEED_ID = feedsDB.getDefaultFeedId(pref_lang);
            }
        }
        return singleton;
    }

    public synchronized static UserDB getInstance(Context context, SharedPreferences prefs, @Nullable FeedsDB feedsDB, ArrayList<String[]> listUserFeeds, ArrayList<String[]> listUserCats) {
        if (singleton == null) {
            singleton = new UserDB(context, prefs, listUserFeeds, listUserCats, feedsDB);
        } else {
            String feed_lang = prefs.getString(RSSReader.PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
            if (!pref_lang.equals(feed_lang)) {//update according to effective feed language
                pref_lang = feed_lang;
                int lang_idx = feedsDB.getLanguageIndex(pref_lang);
                nativeFeeds = FeedsDB.nativeFeeds[lang_idx];
                DEFAULT_FEED_ID = feedsDB.getDefaultFeedId(pref_lang);
            }
            singleton.userFeeds = listUserFeeds;
            singleton.userCats = listUserCats;
        }
        return singleton;
    }

    private UserDB(Context context, SharedPreferences prefs, FeedsDB feedsDB) {
        if (BuildConfig.DEBUG)
            Log.d(tag, "new UserDB from file in feedListfn");
        userFeeds = new ArrayList<>();
        userCats = new ArrayList<>();

        pref_lang = prefs.getString(RSSReader.PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));

        int lang_idx = feedsDB.getLanguageIndex(pref_lang);
        nativeFeeds = FeedsDB.nativeFeeds[lang_idx];
        DEFAULT_FEED_ID = feedsDB.getDefaultFeedId(pref_lang);
        try (FileInputStream fis = context.openFileInput(userDBfn)) {
            ObjectInputStream is = new ObjectInputStream(fis);
            int nobjects = is.readInt();
            while (nobjects > 0) {
                int tag = is.readByte();
                if (tag == zUserFeeds) {
                    userFeeds = (ArrayList<String[]>) is.readObject();//legge la lista di feed utente linearizzata
                    nobjects--;
                } else if (tag == zUserCats) {
                    userCats = (ArrayList<String[]>) is.readObject();//legge la lista di categorie utente linearizzata
                    nobjects--;
                } else break;//design rule: unknown type encountered? break!
            }
            is.close();
        } catch (FileNotFoundException ex) { // file not found, try migration from oldfile (if it exists)
            Log.i(tag, "FileNotFoundException in UserDB constructor --> doMigrate");
        } catch (IOException e) {
            Log.i(tag, "IOException in UserDB constructor");
        } catch (ClassNotFoundException e) {
            Log.i(tag, "ClassNotFoundException in UserDB constructor");
        }
    }

    private UserDB(Context context, SharedPreferences prefs, ArrayList<String[]> listUserFeeds, ArrayList<String[]> listUserCats, FeedsDB feedsDB) {
        if (BuildConfig.DEBUG)
            Log.d(tag, "new UserDB from restore file");
        pref_lang = prefs.getString(RSSReader.PREF_FEEDS_LANGUAGE, context.getString(R.string.default_feed_language_code));
        userCats = listUserCats;

        int lang_idx = feedsDB.getLanguageIndex(pref_lang);
        nativeFeeds = FeedsDB.nativeFeeds[lang_idx];
        DEFAULT_FEED_ID = feedsDB.getDefaultFeedId(pref_lang);
        userFeeds = listUserFeeds;
    }

    public ArrayList<String[]> getUserFeeds(String cat) {//get user feeds for the specific cat
        ArrayList<String[]> current = new ArrayList<>();
        for (String[] feed: userFeeds) {
            if (cat.equals(feed[3])) {
                current.add(feed);
            }
        }
        return current;
    }

    private void deleteUserFeeds(String cat) {//delete user feeds for the specific cat
        int j = 0;
        while (j < userFeeds.size()) {
            if (cat.equals(userFeeds.get(j)[3])) {
                if (BuildConfig.DEBUG)
                    Log.d(tag, "delete feed: "+userFeeds.get(j)[2]);
                userFeeds.remove(j);
            } else j++;
        }
    }

    public static String[][][] getNativeFeeds() {
        return nativeFeeds;
    }

    public ArrayList<String[]> getUserFeeds() {
        return userFeeds;
    }

    public ArrayList<String[]> getUserCats() {
        return userCats;
    }

    public String[] getUserCat(int i) {
        return userCats.get(i);
    }

    public boolean deleteFeed(String feed_id) {
        if ((feed_id != null) && (!feed_id.isEmpty())) {
            if ((feed_id.charAt(0) >= '0') && (feed_id.charAt(0) <= '9')) {//isUserFeed
                int ni = userFeeds.size();
                for (int j = 0; j < ni; j++)
                    if (feed_id.equals(userFeeds.get(j)[2])) {
                        if (BuildConfig.DEBUG)
                            Log.d(tag, "delete feed: "+feed_id);
                        userFeeds.remove(j);
                        return true;
                    }
            }
        }
        return false;
    }

    public void addFeed(@NonNull String[] feed) {
        if (BuildConfig.DEBUG)
            Log.d(tag, "add feed: "+feed[2]);
        int max = 0;
        int ni = userFeeds.size();
        for (int j = 0; j < ni; j++) {
            String feed_id = userFeeds.get(j)[2];
            int seq = Integer.parseInt(feed_id);// gli user feed sono identificati da un numero intero, senza lettera iniziale a differenza dei feed nativi
            if (seq > max)
                max = seq;
        }
        max++; // next
        feed[2] = Integer.toString(max); //feed_id
        userFeeds.add(feed);
    }

    public boolean updateFeed(@NonNull String[] feed) {
        String feed_id = feed[2];
        int ni = userFeeds.size();
        for (int j = 0; j < ni; j++)
            if (feed_id.equals(userFeeds.get(j)[2])) {
                if (BuildConfig.DEBUG)
                    Log.d(tag, "update feed: "+feed[2]);
                userFeeds.set(j, feed);
                return true;
            }
        return false;
    }

    public String[] getFeedData(String feed_id) {// return feed data {title, url, feed_id, cat, timestamp(only in case of userfeed)}
		if ((feed_id != null) && (!feed_id.isEmpty())) {
//TODO: search should be improved
            if ((feed_id.charAt(0) >= '0') && (feed_id.charAt(0) <= '9')) {//isUserFeed
                int ni = userFeeds.size();
                for (int j = 0; j < ni; j++)
                    if (feed_id.equals(userFeeds.get(j)[2]))
                        return userFeeds.get(j);
            } else {// embedded feed
                for (String[][] nativeFeed : nativeFeeds) {
                    for (String[] aNativeFeed : nativeFeed)
                        if (feed_id.equals(aNativeFeed[2]))
                            return aNativeFeed;
                }
            }
		}
		return nativeFeeds[0][0]; // default feed
	}

    public boolean setFeedUrl(Context context, String feed_id, String feedurl) {// set feed url
        if ((feed_id != null) && (!feed_id.isEmpty())) {
//TODO: search should be improved
            if ((feed_id.charAt(0) >= '0') && (feed_id.charAt(0) <= '9')) {//isUserFeed
                int ni = userFeeds.size();
                for (int j = 0; j < ni; j++)
                    if (feed_id.equals(userFeeds.get(j)[2])) {
                        userFeeds.get(j)[1] = feedurl;
                        synctoFile(context);
                        return true;
                    }
            } else {// embedded feed
                for (int i = 0; i < nativeFeeds.length; i++) {
                    int ni = nativeFeeds[i].length;
                    for (int j = 0; j < ni; j++)
                        if (feed_id.equals(nativeFeeds[i][j][2])) {
                            nativeFeeds[i][j][1] = feedurl;
                            return true;
                        }
                }
            }
        }
        return false;
    }


    public static boolean isUserFeed(String feed_id) {
        return (feed_id.charAt(0) >= '0') && (feed_id.charAt(0) <= '9'); // user feed
    }

    public int cat2int(String categ) {
        if ((categ.charAt(0) >= '0') && (categ.charAt(0) <= '9')) { // user category
            for (int cat = 0; cat < userCats.size(); cat++) {
                if (userCats.get(cat)[2].equals(categ))
                    return cat + categories.length;
            }
        } else
            for (int cat = 0; cat < categories.length; cat++) {
                if (categories[cat][2].equals(categ))
                    return cat;
            }
        return -1; // not found
    }

    public void deleteCat(int j) {//delete user category
        if (j >= categories.length) {
            if (BuildConfig.DEBUG)
                Log.d(tag, "deleteCat id:"+(j - categories.length));
            deleteUserFeeds(userCats.get(j - categories.length)[2]);
            userCats.remove(j - categories.length);
        } else Log.e(tag, "incorrect cat id");

    }

    public int updateCategory(@NonNull String[] category, Context context) {//add or replace category
        int position = -1; // invalid position
        if (category[2] == null) {// add new feed
            int max = 0;
//TODO: search should be improved
            for (String[] cat : userCats) {
                String cat_id = cat[2];
                int seq = Integer.parseInt(cat_id.substring(0, cat_id.length() - 1));// le categorie di utente hanno come id un numero progressivo seguito da '$'
                if (seq > max)
                    max = seq;
            }
            max++; // next
            category[2] = max + "$"; //category id
            userCats.add(category);
            if (BuildConfig.DEBUG)
                Log.d(tag, "updateCategory id:"+category[2]);
        } else { // replace existing category
            for (int cat = 0; cat < userCats.size(); cat++) {
                if (category[2].equals(userCats.get(cat)[2])) {
                    userCats.set(cat, category);
                    position = cat + categories.length;//user category
                }
            }
        }
        synctoFile(context);
        return position;//it is valid only in replace case (-1 in case of add)
    }


    public void synctoFile(Context context) {
        new Thread(() -> {
//do task in background (avoid writefile in UI thread
            try (FileOutputStream fos = context.openFileOutput(userDBfn, Context.MODE_PRIVATE)) {
                ObjectOutputStream os = new ObjectOutputStream(fos);
                int nobjects = 0;
                if (!userFeeds.isEmpty())
                    nobjects++;
                if (!userCats.isEmpty())
                    nobjects++;
                os.writeInt(nobjects);//write number of objects
                if (!userFeeds.isEmpty()) {
                    os.writeByte(zUserFeeds);//tag userfeeds
                    os.writeObject(userFeeds);//scrive su file la lista di feed utente linearizzata
                }
                if (!userCats.isEmpty()) {
                    os.writeByte(zUserCats);//tag usercats
                    os.writeObject(userCats);//scrive su file la lista di categorie utente linearizzata
                }
                os.close();
            } catch (IOException ioex) {
                Log.i(tag, "IOException in FeedsTree.addFeed()");
            }
        }).start();
    }
		
}
