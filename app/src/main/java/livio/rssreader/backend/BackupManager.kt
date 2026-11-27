package livio.rssreader.backend

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import livio.rssreader.BuildConfig
import livio.rssreader.R
import livio.rssreader.RSSReader
import livio.rssreader.backend.UserDB
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tools.FileHandler
import tools.FileManager
import java.io.File
import java.util.Calendar
import java.util.Random

/*
BackupManager: handles backup, restore, auto-recovery file and the "backup hint" dialog.

Extracted from RSSReader. The class implements FileHandler, so it is the object
that FileManager calls back to encode (backup) and decode (restore) the data file.

Usage from the activity:
  private val backupManager by lazy { BackupManager(this, prefs) { restart() } }
  onCreate()           -> backupManager.checkAutoRecovery()
  onDestroy()          -> backupManager.createAutoRecoveryIfNeeded()
  onResume()           -> backupManager.suggestBackupIfNeeded()
  menu backup          -> backupManager.doBackup()
  menu restore         -> backupManager.startRestore(anchorView)
  onActivityResult()   -> backupManager.processActivityResult(...)
  onRequestPermissionsResult() -> backupManager.processRequestPermissionsResult(...)
*/
class BackupManager(
    private val activity: RSSReader,
    private val prefs: SharedPreferences,
    private val onRestoreCompleted: () -> Unit
) : FileHandler {

    // created lazily: FileManager must not be built before the activity is attached to a context
    private val fileManager: FileManager by lazy {
        FileManager(activity, this, true, APP_FOLDER)
    }

    private var createRecoveryFile = false
    private val randomBackupHint = Random()

    /**///////////////////////////////////////// */
    // auto-recovery
    /**///////////////////////////////////////// */

    /** Evaluates whether the auto-recovery file is missing or too old. Call from onCreate(). */
    fun checkAutoRecovery() {
        createRecoveryFile = false
        val owndir = activity.getExternalFilesDir(null)
        if (owndir != null) {
            val backupFile = File(owndir, BACKUP_FILENAME)
            if (backupFile.exists()) {
                val age = System.currentTimeMillis() - backupFile.lastModified()
                if (age > AUTO_BACKUP_MAX_AGE)  // file is too old
                    createRecoveryFile = true
            } else createRecoveryFile = true
        }
    }

    /** Writes the auto-recovery file if checkAutoRecovery() asked for it. Call from onDestroy(). */
    fun createAutoRecoveryIfNeeded() {
        if (createRecoveryFile)
            fileManager.createAutoRecovery(BACKUP_FILENAME)
    }

    /**///////////////////////////////////////// */
    // manual backup / restore
    /**///////////////////////////////////////// */

    fun doBackup() {
        if ((Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) || !fileManager.createFileSAF(
                EXTENDED_BACKUP_FILENAME
            )
        ) { //scopedstorage
            val authorityFp = BuildConfig.APPLICATION_ID + ".FileProvider"
            fileManager.saveFile(
                EXTENDED_BACKUP_FILENAME,
                BACKUP_FILENAME,
                authorityFp,
                R.mipmap.ic_launcher
            )
        }
    }

    /**
     * Starts the restore procedure.
     * @param anchor view used to anchor the popup menu on Android 10 or earlier
     */
    fun startRestore(anchor: View) {
        //scopedstorage (Unfortunately in Android 11 it is not possible to safely use filename2uri_downloads_Q(), so we use SAF for the restore anyway, as in the case of external storage
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            fileManager.openFileSAF(
                FileManager.EXTENDED_BACKUP_MIMETYPE_ONEDRIVE,
                false
            ) //scopedstorage, use SAF on Android 11 or later
        } else {
//18-09-2022: new PopupMenu handling to avoid issues in ChromeOS based on Android 11 (or higher)
//PopupMenu fixes mouse hover over 'restore' menu item opening file manager on ChromeOS in an unwanted way
            val popup = showRestorePopup(anchor, Gravity.END)

            popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item: MenuItem? ->
                val itemId = item!!.itemId
                if (itemId == R.id.menu_ext_storage) { //restore from external storage
                    fileManager.readExternalFile()
                    return@OnMenuItemClickListener true
                } else if (itemId == R.id.menu_local_storage) { //restore from local storage (direct)
                    fileManager.readLocalFile(
                        EXTENDED_BACKUP_FILENAME,
                        BACKUP_FILENAME
                    ) //scopedstorage
                    return@OnMenuItemClickListener true
                }
                false //never happen
            })
        }
    }

    private fun showRestorePopup(view: View, gravity: Int): PopupMenu {
        val popup = PopupMenu(activity, view, gravity)
        val popmenu = popup.menu
        popmenu.add(
            Menu.NONE,
            R.id.menu_local_storage,
            Menu.NONE,
            R.string.local_storage
        ) //menu_local_storage is hidden in buildNavDrawerItems()
        popmenu.add(
            Menu.NONE,
            R.id.menu_ext_storage,
            Menu.NONE,
            R.string.ext_storage
        ) //menu_ext_storage is hidden in buildNavDrawerItems()

        popup.show()
        return popup
    }

    /** Forwards the result of an activity to the FileManager; returns true if it was consumed. */
    fun processActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        fileManager.processActivityResult(requestCode, resultCode, data)

    fun processRequestPermissionsResult(requestCode: Int) =
        fileManager.processRequestPermissionsResult(requestCode)

    /**///////////////////////////////////////// */
    // backup hint
    /**///////////////////////////////////////// */

    /** Randomly suggests the user to do a backup, if never done before. Call from onResume(). */
    fun suggestBackupIfNeeded() {
        //This testy avoids ANR in case duplicate dialogs if one is already showing (e.g. after rotation)
        if (activity.supportFragmentManager.findFragmentByTag("backuphint") != null) return

        if (randomBackupHint.nextInt(20) != 0) return
        if (prefs.getLong(PREF_BACKUP_TIME, -1) != -1L) return //backup already performed

        val ft = UserDB.getInstance(activity, prefs)
        if (ft.userFeeds.isEmpty()) return //nothing to save

        if (prefs.getLong(PREF_BACKUP_DIALOG, -1) != -1L) return //hint already shown once
        prefs.edit {
            putLong(PREF_BACKUP_DIALOG, System.currentTimeMillis())
        }
        BackupHint_DF().show(activity.supportFragmentManager, "backuphint")
    }

    class BackupHint_DF : AppCompatDialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val builder = MaterialAlertDialogBuilder(requireActivity())
            builder.setMessage(getString(R.string.backup_hint))
                .setPositiveButton(getString(R.string.backup)) { _, _ ->
                    (requireActivity() as? RSSReader)?.doBackup()
                }
                .setNeutralButton(getString(android.R.string.cancel)) { _, _ -> }
            return builder.create()
        }
    }

    /**///////////////////////////////////////// */
    // FileHandler implementation
    /**///////////////////////////////////////// */

    override fun getMimeType(): String {
        return activity.getString(R.string.backup_mimetype)
    }

    @Throws(JSONException::class)
    override fun encodeFile(): String {
// write origin of backup in preferences		
        val editor = prefs.edit()
        val c = Calendar.getInstance()
        val timestamp = c.timeInMillis
        editor.putLong(PREF_BACKUP_TIME, timestamp)
        editor.putString(PREF_BACKUP_ORIGIN, "encodeBackup")
        editor.apply()
        // common part					
        val obj = JSONObject()
        obj.put("version", 1) // version 1 of backup file format
        obj.put("name", BuildConfig.APPLICATION_ID) // package name
        obj.put("timestamp", timestamp) // backup timestamp in milliseconds
        // userfeeds part
        val ft = UserDB.getInstance(activity, prefs)
        obj.put("userfeeds", JSONArray(ft.userFeeds))
        // usercats part
        obj.put("usercats", JSONArray(ft.userCats))
        // deletednativefeeds part
        obj.put("deletednativefeeds", JSONArray(ft.deletedNativeFeeds))
        // prefs part
        val pref = prefs.all
        val jprefs = JSONObject()
        if (pref != null) {
            for ((key, value) in pref) if ((key != "config_date") && (key != RSSReader.PREF_CLIENT_VERSION) &&
                (value is String || value is Boolean
                        || value is Int || value is Long)
            ) jprefs.put(key, value)
            obj.put("prefs", jprefs)
        }
        return obj.toString()
    }


    @Throws(JSONException::class)
    override fun decodeFile(content: String): Boolean {
        val obj = JSONObject(content)
        val version = obj.optLong("version")
        val name = obj.optString("name")
        val timestamp = obj.optLong("timestamp")
        //		Log.w(tag, "decodeBackup:  " + version);
        if (name != BuildConfig.APPLICATION_ID) return false
        // userfeeds part
        val juserfeeds = obj.optJSONArray("userfeeds")
        //        Log.w(tag, "decodeBackup:juserfeeds  " + juserfeeds);
        val listUserFeeds = ArrayList<Array<String?>?>()
        if (juserfeeds != null) {
            for (i in 0..<juserfeeds.length()) {
                val feed = juserfeeds.getJSONArray(i)
                if (feed.length() == UserDB.FEED_SIZE) {
                    val aFeed = arrayOfNulls<String>(UserDB.FEED_SIZE)
                    for (j in 0..<UserDB.FEED_SIZE) {
                        aFeed[j] = feed.getString(j)
                    }
                    listUserFeeds.add(aFeed)
                } else Log.e(TAG, "decodeBackup: incorrect number of elements in $feed")
            }
        }
        // usercats part
        val jusercats = obj.optJSONArray("usercats")
        //        Log.w(tag, "decodeBackup:jusercats  " + jusercats);
        val listUserCats = ArrayList<Array<String?>?>()
        if (jusercats != null) {
            for (i in 0..<jusercats.length()) {
                val cat = jusercats.getJSONArray(i)
                if (cat.length() == UserDB.CAT_SIZE) {
                    val aCat = arrayOfNulls<String>(UserDB.CAT_SIZE)
                    for (j in 0..<UserDB.CAT_SIZE) {
                        aCat[j] = cat.getString(j)
                    }
                    listUserCats.add(aCat)
                } else Log.e(TAG, "decodeBackup: incorrect number of elements in $cat")
            }
        }
        // deletednativefeeds part
        val jdeletednativefeeds = obj.optJSONArray("deletednativefeeds")
        Log.w(TAG, "decodeBackup:jdeletednativefeeds  $jdeletednativefeeds")
        val deletedNativeFeeds = HashSet<String?>()
        if (jdeletednativefeeds != null) {
            for (i in 0..<jdeletednativefeeds.length()) deletedNativeFeeds.add(
                jdeletednativefeeds.getString(
                    i
                )
            )
        }
        //sync to file if needed
        if (listUserFeeds.size + listUserCats.size + deletedNativeFeeds.size > 0) {
            val ft = UserDB.getInstance(
                activity,
                prefs,
                listUserFeeds,
                listUserCats,
                deletedNativeFeeds
            ) //create feedstree with restored user feeds
            ft.synctoFile(activity) //write restored user feeds to file
        }

        // prefs part				
        val jprefs = obj.optJSONObject("prefs")
        //		Log.w(tag, "decodeBackup:jprefs  " + jprefs);
        if (jprefs != null) {
            prefs.edit {
                val pref = jprefs.keys()
                while (pref.hasNext()) {
                    val mPref = pref.next()
                    val tPref = jprefs.get(mPref)
                    // String, Boolean, Long
                    if (tPref is Boolean) {
                        putBoolean(mPref, tPref)
                    } else if (tPref is String) {
                        putString(mPref, tPref)
                    } else if (tPref is Int) {
                        putInt(mPref, tPref)
                    } else if (tPref is Long) {
                        putLong(mPref, tPref)
                    } else Log.w(TAG, "decodeBackup, unexpected pref: $mPref")
                }
            }
            onRestoreCompleted() // the activity recreates itself to apply restored preferences
        }
        return true
    }

    companion object {
        private const val TAG = "BackupManager"

        val APP_FOLDER: String? =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) "livio" + File.separator + "rssreader" else Environment.DIRECTORY_DOWNLOADS

        private const val EXTENDED_BACKUP_FILENAME = "rssreader.backup"
        private const val BACKUP_FILENAME = "backup.z"

        private const val PREF_BACKUP_TIME = "backup_time" // backup date
        private const val PREF_BACKUP_ORIGIN = "backup_origin" // backup origin
        private const val PREF_BACKUP_DIALOG = "backup_dialog_ts"

        private const val AUTO_BACKUP_MAX_AGE = 7L * 24L * 3600L * 1000L // 7 days
    }
}
