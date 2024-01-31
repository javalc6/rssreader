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
import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;
import livio.rssreader.BuildConfig;
import livio.rssreader.R;

//IMPORTANT: users of FileManager MUST call processActivityResult() AND processRequestPermissionsResult()
public final class FileManager {
    private final static String tag = "FileManager";
    public static final boolean debug = BuildConfig.DEBUG; // shall be false in production

    private static final String BACKUP_FOLDER = "backup";
    public static final String EXTENDED_BACKUP_MIMETYPE_ONEDRIVE = "application/*";

    private final static int MY_PERMISSIONS_REQUEST_WRITE_FILE = 1; 
    private final static int MY_PERMISSIONS_REQUEST_READ_FILE = 2; 
    private final static int MY_PERMISSIONS_REQUEST_READ_EXTENDED_FILE = 3; 

    private static final int REQUEST_CODE_READ_FILE = 42;
    private static final int REQUEST_CODE_WRITE_FILE = 43;
    private static final int REQUEST_EXTENDED_READ_FILE = 44;

    private static final String PROVIDER_INTERFACE = "android.content.action.DOCUMENTS_PROVIDER";

    private final static String EOL = "\r\n"; //DO NOT CHANGE
    private static String debug_info = null; // used to show debug info in Credits hidden information

    private final Activity mActivity;
    private final FileHandler fileHandler;
    private final boolean backupMode; //true: backup/restore, false: export/import
    private final String mAppFolder;

    public static String getDebugInfo() {
        return debug_info;
    }

    public FileManager(Activity activity, FileHandler fh, boolean bm, String app_folder) {
        mActivity = activity;
        backupMode = bm;
        fileHandler = fh;
        mAppFolder = app_folder;
    }
    /******************************************************************************
     * onRequestPermissionsResult related section
     ******************************************************************************/

    private static Uri save_uri;
    private static String save_filename;

    public void processRequestPermissionsResult(int requestCode) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_FILE:
                execute_savefile(save_filename);
                break;
            case MY_PERMISSIONS_REQUEST_READ_FILE:
                execute_readfile(save_filename);
                break;
            case MY_PERMISSIONS_REQUEST_READ_EXTENDED_FILE:
                do_extended_readfile(save_uri);
        }
    }
    /******************************************************************************
     * onActivityResult related section
     ******************************************************************************/
//processActivityResult returns true if ActivityResult was consumed
    public boolean processActivityResult(int requestCode, int resultCode, Intent resultData) {
        switch (requestCode) {
            case REQUEST_CODE_WRITE_FILE: 
                if (resultCode == Activity.RESULT_OK) {
                    if (resultData != null) {
                        Uri uri = resultData.getData();
                        int resid;
                        try {
                            if (updateDocument(uri))
                                resid = backupMode ? R.string.msg_backup_ok : R.string.msg_export_ok;
                            else
                                resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
                        } catch (JSONException e) {
                            Log.w(tag, "REQUEST_CODE_WRITE_FILE", e);
                            resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
                        }
                        Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(resid), Snackbar.LENGTH_SHORT).show();
                    }
                }
                return true;
            case REQUEST_CODE_READ_FILE: 
                if (resultCode == Activity.RESULT_OK) {
                    if (resultData != null) {
                        Uri uri = resultData.getData();
                        do_extended_readfile(uri);
                    }
                }
                return true;
            case REQUEST_EXTENDED_READ_FILE: //extended restore
                if (resultCode == Activity.RESULT_CANCELED) {
                    // action cancelled
                    Log.i(tag, "extended restore: action cancelled");
                } else if (resultCode == Activity.RESULT_OK) {
                    if (resultData != null) {
                        if (mActivity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                            save_uri = resultData.getData();
                            mActivity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                    FileManager.MY_PERMISSIONS_REQUEST_READ_EXTENDED_FILE);
                            return true;
                        }
                        do_extended_readfile(resultData.getData());
                    } else Log.i(tag, "extended restore: data is null");
                }

                return true;
            default:
                return false;
        }
    }

    /******************************************************************************
     * Backup section
     ******************************************************************************/


     /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);

    }


    /* Checks if external storage is available to at least read */
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);

    }

    private void do_local_save(String extended_filename, String simple_filename) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {//scopedstorage
            int resid;
            try {
                boolean result = create_file_downloads_Q(extended_filename, fileHandler.getMimeType(), fileHandler.encodeFile());
                if (result)
                    resid = backupMode ? R.string.msg_backup_ok : R.string.msg_export_ok;
                else resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
            } catch (JSONException e) {
                Log.w(tag, "JSONException on menu_backup");
                resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
            }
            Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(resid), Snackbar.LENGTH_SHORT).show();
            return;
        } else {
            if (mActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                save_filename = simple_filename;
                mActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_FILE);
                return;
            }
        }
        execute_savefile(simple_filename);
    }

    private void execute_savefile(String simple_filename) {
        int resid;
        if (isExternalStorageWritable()) {
//         File file = new File(getExternalFilesDir(null), filename);
            String folder = mAppFolder;
            if (backupMode)
                folder += File.separator+BACKUP_FOLDER;
            File dir = new File(Environment.getExternalStorageDirectory(), folder);
            dir.mkdirs(); // create needed directories if not present
            File file = new File(dir, simple_filename);
            try (GZIPOutputStream f_out = new GZIPOutputStream(
                    new BufferedOutputStream(new FileOutputStream(file)))) {
/* using object stream
        ObjectOutputStream obj_out = new ObjectOutputStream(f_out);
        obj_out.writeObject(content);
*/
                writeFile(f_out, !backupMode, fileHandler.encodeFile(), fileHandler.getMimeType());//backupmode needs old format
                f_out.close();
                resid = backupMode ? R.string.msg_backup_ok : R.string.msg_export_ok;
            } catch (IOException e) {
                Log.w(tag, "execute_savefile: error writing " + simple_filename, e);
                resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
            } catch (JSONException e) {
                Log.w(tag, "JSONException on menu_backup");
                resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
            } catch (NoSuchAlgorithmException e) {
//ignore it, should never occur
                resid = R.string.msg_external_storage_notwritable;//default value - not really used
            }
        } else resid = R.string.msg_external_storage_notwritable;
//        	Toast.makeText(this, mActivity.getString(resid), Toast.LENGTH_SHORT).show();
        Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(resid), Snackbar.LENGTH_SHORT).show();
    }

//do not use saveFile() for SDK >= 30, you must use createFileSAF() if SDK >= 30
    @Deprecated
    public void saveFile(int textcolor, final String extended_filename, String simple_filename, final String AUTHORITY_FP, int icon_resource_id) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            Log.e(tag, "saveFile() shall not be used on Android 11 or later");
        int resid;
        try {
            PackageManager pm = mActivity.getPackageManager();
// get list of receiver for ACTION_GET_CONTENT
//            resid = R.string.msg_missing_file_manager; //default message to print out
            HashSet<String> allowed_set;

            allowed_set = new HashSet<>();
            final Intent gc_intent = new Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(fileHandler.getMimeType());
            final List<ResolveInfo> lri_gc = pm.queryIntentActivities(gc_intent, PackageManager.MATCH_ALL);
            if (lri_gc != null)
                for (ResolveInfo ri_gc : lri_gc) {
                    if (ri_gc.activityInfo != null)
                        allowed_set.add(ri_gc.activityInfo.packageName);
                }
// get list of receiver for PROVIDER_INTERFACE to capture Google Drive
            final Intent pi_intent = new Intent(PROVIDER_INTERFACE);
            final List<ResolveInfo> lri_pi = pm.queryIntentContentProviders(pi_intent, 0);
            if (lri_pi != null)
                for (ResolveInfo ri_pi : lri_pi) {
                    if (ri_pi.providerInfo != null) {
                        allowed_set.add(ri_pi.providerInfo.packageName);
//                            Log.d(tag, "packageName: " + ri_pi.providerInfo.packageName);
                    }
                }
            allowed_set.remove("jackpal.androidterm"); // blacklisted application

            final ArrayList<IconItem> intentList = new ArrayList<>();
            intentList.add(new IconItem(null, mActivity.getString(R.string.local_storage), icon_resource_id, null));// insert local backup as first element!

// get list of receiver for ACTION_SEND
            final List<ResolveInfo> lri_send = getSendReceivers(pm, fileHandler.getMimeType());

            if ((allowed_set.size() > 0) && (lri_send != null) && (lri_send.size() > 0)) {
// generate file to backup with new format (extended backup)

                File backup_dir = new File(mActivity.getCacheDir(), BACKUP_FOLDER);//BACKUP_FOLDER è un in questo caso solo un folder convenzionale per separare i files di FileManager da altri files presenti in cache
                if (!backup_dir.exists())
                    backup_dir.mkdir();
                File mybackup = new File(backup_dir, extended_filename);
                GZIPOutputStream ostream = new GZIPOutputStream(
                        new BufferedOutputStream(new FileOutputStream(mybackup)));
                Uri ouri = FileProvider.getUriForFile(mActivity, AUTHORITY_FP, mybackup);
                writeFile(ostream, true, fileHandler.encodeFile(), fileHandler.getMimeType());//use always new format, both backup mode and file mode
                ostream.close();

// now collect the list of intents to put in chooser
                for (ResolveInfo ri_send: lri_send) {
                    if ((ri_send.activityInfo != null) && (allowed_set.contains(ri_send.activityInfo.packageName))) {
                        Intent intent = new Intent(Intent.ACTION_SEND)
                                .putExtra(Intent.EXTRA_STREAM, ouri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                                .setType(fileHandler.getMimeType())//experimental
                                .setClassName(ri_send.activityInfo.packageName, ri_send.activityInfo.name);
                        intentList.add(new IconItem(intent, ri_send.loadLabel(pm).toString(),
                                ri_send.activityInfo.getIconResource(), ri_send.activityInfo.packageName));
//                        Log.d(tag, "packageName: " + ri_send.activityInfo.packageName + " - " + ri_send.activityInfo.name);
                    }
                }
            }

            if (intentList.size() > 1) {//must be greater than 1, to be sure there are real intents (not only the local backup)
                ArrayAdapter<IconItem> adapter = new IconArrayAdapter(mActivity, R.layout.icon_listitem, intentList, textcolor);

                new MaterialAlertDialogBuilder(mActivity)//'this' has replaced getSupportActionBar().getThemedContext() to avoid theme issues
                        .setTitle(backupMode ? R.string.backup : R.string.export_label)
                        .setAdapter(adapter, (dialog, item) -> {
//                            Log.d(tag, "pos:"+item);
                            Intent intent = intentList.get(item).intent;
                            if (intent == null) // local backup
                                do_local_save(extended_filename, simple_filename);
                            else {
                                try {// workaround to avoid exception: android.os.NetworkOnMainThreadException (issue detected with Onedrive cloud)
                                    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitNetwork().build()); // DO NOT REMOVE TO AVOID ANR IN ICS AND LATER DEVICES
                                } catch(NoClassDefFoundError e) {
                                    //ignore - cannot set thread policy
                                }
                                mActivity.startActivity(intent);//remote backup
                            }
                        })
                        .show();
            } else {//local backup
                do_local_save(extended_filename, simple_filename);
            }
            return;
        } catch (JSONException e) {
            Log.w(tag, "JSONException on menu_backup");
            resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(tag, "doExtendedBackup: ActivityNotFoundException", ex);
            resid = R.string.msg_missing_file_manager;
        } catch (IOException e) {
            Log.w(tag, "doExtendedBackup: error writing " + extended_filename, e);
            resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
        } catch (NoSuchAlgorithmException e) {
            Log.w(tag, "doExtendedBackup: NoSuchAlgorithmException", e);
            resid = backupMode ? R.string.msg_cannot_write_backup : R.string.msg_cannot_write_file;
        }
        Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(resid), Snackbar.LENGTH_SHORT).show();
    }

    private List<ResolveInfo> getSendReceivers(PackageManager pm, String mimetype) {
// get list of receiver for ACTION_SEND
        final Intent send_intent = new Intent(Intent.ACTION_SEND)
                .setType(fileHandler.getMimeType());//experimental
        return pm.queryIntentActivities(send_intent, PackageManager.MATCH_ALL);
    }

    /******************************************************************************
     * Restore section
     ******************************************************************************/

//do not use readExternalFile() for SDK >= 30, you must use openFileSAF() if SDK >= 30
    @Deprecated
    public void readExternalFile() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            Log.e(tag, "readExternalFile() shall not be used on Android 11 or later");
        if (debug)
            Log.d(tag, "readExternalFile");
        try {// workaround to avoid exception: android.os.NetworkOnMainThreadException (issue detected with Onedrive cloud)
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitNetwork().build()); // DO NOT REMOVE TO AVOID ANR IN ICS AND LATER DEVICES
        } catch(NoClassDefFoundError e) {
            //ignore - cannot set thread policy
        }

        try {
// get list of receiver for ACTION_SEND
            final List<ResolveInfo> lri_send = getSendReceivers(mActivity.getPackageManager(), fileHandler.getMimeType());
            if ((lri_send != null) && (lri_send.size() > 0)) {
// a causa di samsung (issue 70697) è necessario utilizzare un mimetype generico "*/*" perchè altrimenti non è possibile usare Google Drive per il restore del backup
                String type = Build.MANUFACTURER.equalsIgnoreCase("samsung") ? "*/*" // // "*/*" is neeeded by samsung devices, due to issue 70697
                        : EXTENDED_BACKUP_MIMETYPE_ONEDRIVE;// "application/*" is needed by onedrive (microsoft)
                mActivity.startActivityForResult(
                        new Intent(Intent.ACTION_GET_CONTENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .setType(type),// samsung issue 70697
                        REQUEST_EXTENDED_READ_FILE);
                return;
            }
        } catch (android.content.ActivityNotFoundException ex) {
            debug_info = "do_readfile: activity not found";
// fallback to local restore
//                Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(R.string.msg_missing_file_manager), Snackbar.LENGTH_SHORT).show();
        }
//Il messaggio informativo per l'utente dovrebbe essere tipo: "Please install cloud application, e.g. Google Drive or similar"
        Snackbar.make(mActivity.findViewById(android.R.id.content),
                mActivity.getString(backupMode ? R.string.msg_missing_backup : R.string.msg_missing_file), Snackbar.LENGTH_SHORT).show();
    }

//do not use readLocalFile() for SDK >= 30, you must use openFileSAF() if SDK >= 30
    @Deprecated
    public void readLocalFile(String extended_filename, String simple_filename) {//scopedstorage
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {//scopedstorage
//this code does not work when application is uninstalled and then re-installed, see: https://issuetracker.google.com/issues/164317563
            String error = "backup content is null";
            Uri contentUri = filename2uri_downloads_Q(extended_filename);
            if (debug)
                Log.d(tag, "readLocalFile:"+contentUri);
            if (contentUri != null) {
                ContentResolver resolver = mActivity.getContentResolver();
                try (InputStream is = resolver.openInputStream(contentUri)) {
                    if (is != null) {
                        error = readFile(is, !backupMode);
                    } else {
                        error = "readLocalFile: <is> null pointer";
                        Log.d(tag, error);
                    }
                } catch (IOException | JSONException | NoSuchAlgorithmException e) {
                    error = e.getMessage();
                    Log.w(tag, "readLocalFile: "+e.getMessage()+" reading " + extended_filename);
                }
            }
            if (error != null) {//nota che readFile() ritorna null quando il file è letto correttamente
                if (backupMode) {//in backup mode possiamo tentare il fallback su auto-recovery file se quest'ultimo esiste
                    File owndir = mActivity.getExternalFilesDir(null);//autobackup
                    if (owndir != null) {
                        File backup_file = new File(owndir, simple_filename);
                        if (backup_file.exists()) {//fallback su auto-recovery file
                            try (FileInputStream fis = new FileInputStream(backup_file)) {
                                if (debug)
                                    Log.d(tag, "readLocalFile:autorecovery");
                                if (readFile(fis, true) == null)//return when restore of autobackup file is successful
                                    return;
                            } catch (IOException | JSONException | NoSuchAlgorithmException e) {
                                Log.w(tag, "readLocalFile: " + e.getMessage() + " reading " + simple_filename);
                                //variable error is not modified, to show original error to user
                            }
                        }
                    }
                }
                Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(backupMode ? R.string.msg_cannot_read_backup : R.string.msg_cannot_read_file)+" ("+error+")", Snackbar.LENGTH_LONG).show();
            }
            return;
        } else {
            if (mActivity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                save_filename = simple_filename;
                mActivity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_FILE);
                return;
            }
        }
        execute_readfile(simple_filename);
    }

    private void do_extended_readfile(Uri content) {
        String error = "backup content is null";
        if (content != null)
            try {
                debug_info = content.toString();//debugging!!
//                Log.d(tag, "content: "+content);

                InputStream is = mActivity.getContentResolver().openInputStream(content);
                if (is != null) {
                    error = readFile(is, !backupMode);
                } else {
                    error = "do_extended_readfile: <is> null pointer";
                    Log.d(tag, error);
                }
            } catch (JSONException e) {
                error = "invalid JSON format";
                Log.d(tag, "do_extended_readfile", e);
            } catch (NoSuchAlgorithmException e) {
                error = "NoSuchAlgorithmException";
                Log.d(tag, "do_extended_readfile", e);
            } catch (FileNotFoundException e) {
                error = "FileNotFoundException";
                Log.d(tag, "do_extended_readfile", e);
            } catch (IOException e) {
                error = "IOException";
                Log.d(tag, "do_extended_readfile", e);
            }
        if (error != null)//nota che readFile() ritorna null quando il file è letto correttamente
            Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(backupMode ? R.string.msg_cannot_read_backup : R.string.msg_cannot_read_file)+" ("+error+")", Snackbar.LENGTH_LONG).show();
    }

    private void execute_readfile(String filename) {
        String error;
        if (isExternalStorageReadable()) {
            String folder = mAppFolder;
            if (backupMode)
                folder += File.separator+BACKUP_FOLDER;
            File file = new File(Environment.getExternalStorageDirectory(),
                    folder+File.separator+filename);
            try (FileInputStream fis = new FileInputStream(file)) {
                error = readFile(fis, !backupMode);
            } catch (IOException | JSONException | NoSuchAlgorithmException e) {
                error = mActivity.getString(backupMode ? R.string.msg_cannot_read_backup : R.string.msg_cannot_read_file);
                Log.w(tag, "execute_readfile: "+e.getMessage()+" reading " + file);
            }
        } else {//SHOW_ADVANCED not supported before lollipop
            openFileSAF(EXTENDED_BACKUP_MIMETYPE_ONEDRIVE, true);//use saf if external storage is not writable;  "application/*" is safer compared to backup_mimetype when performing file search
            return;
        }
        if (error != null) {//nota che readFile() ritorna null quando il file è letto correttamente
            if (backupMode) {//in backup mode possiamo tentare il fallback su auto-recovery file se quest'ultimo esiste
                File owndir = mActivity.getExternalFilesDir(null);//autobackup
                if (owndir != null) {
                    File backup_file = new File(owndir, filename);
                    if (backup_file.exists()) {//fallback su auto-recovery file
                        try (FileInputStream fis = new FileInputStream(backup_file)) {
                            if (readFile(fis, true) == null)//return when restore of autobackup file is successful
                                return;
                        } catch (IOException | JSONException | NoSuchAlgorithmException e) {
                            Log.w(tag, "execute_readfile: " + e.getMessage() + " reading " + filename);
                            //variable error is not modified, to show original error to user
                        }
                    }
                }
            }
            Snackbar.make(mActivity.findViewById(android.R.id.content), error, Snackbar.LENGTH_LONG).show();
        }
    }

    private String readFile(InputStream is, boolean onlyNewMode) throws IOException, NoSuchAlgorithmException, JSONException {
        String error;
        BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new BufferedInputStream(is))));
        String restore = br.readLine();
        if (debug)
            Log.d(tag, "readFile:"+restore);
        if (fileHandler.getMimeType().equals(restore)) {
            String checksum = br.readLine();
            restore = br.readLine();
            br.close();

            MessageDigest digester = MessageDigest.getInstance("MD5");
            digester.update(restore.getBytes(StandardCharsets.UTF_8));
            String digest = Base64.encodeToString(digester.digest(), Base64.NO_WRAP + Base64.NO_PADDING);

            if (checksum.equals(digest)) {
                if (fileHandler.decodeFile(restore)) {//twin
                    Toast.makeText(mActivity, mActivity.getString(backupMode ? R.string.msg_restore_ok : R.string.msg_import_ok), Toast.LENGTH_SHORT).show();
                    return null;
                } else error = "invalid mimetype";
            } else {
                error = "invalid checksum";
            }
        } else if (!onlyNewMode) {//in case of backup, use old mode as fallback
            br.close();
            if (fileHandler.decodeFile(restore)) {//twin
                Toast.makeText(mActivity, mActivity.getString(backupMode ? R.string.msg_restore_ok : R.string.msg_import_ok), Toast.LENGTH_SHORT).show();
                return null;
            } else error = "invalid mimetype";
        } else {
            br.close();
            error = "invalid mimetype";
        }
        return error;
    }

    private static void writeFile(GZIPOutputStream ostream, boolean newMode, String content, String mimetype) throws NoSuchAlgorithmException, IOException {
        if (newMode) {
            MessageDigest digester = MessageDigest.getInstance("MD5");
            digester.update(content.getBytes(StandardCharsets.UTF_8));
            String digest = Base64.encodeToString(digester.digest(), Base64.NO_WRAP + Base64.NO_PADDING);

            ostream.write(mimetype.getBytes(StandardCharsets.UTF_8));
            ostream.write(EOL.getBytes(StandardCharsets.UTF_8));
            ostream.write(digest.getBytes(StandardCharsets.UTF_8));
            ostream.write(EOL.getBytes(StandardCharsets.UTF_8));
        }
        ostream.write(content.getBytes(StandardCharsets.UTF_8));
    }

    public void createAutoRecovery(String filename) {
        File owndir = mActivity.getExternalFilesDir(null);//autobackup
        if (owndir != null) {//autobackup
            File backup_file = new File(owndir, filename);
            if (backup_file.exists()) {
//rimuovi eventuale file .old
                File old_backup_file = new File(owndir, filename + ".old");
                old_backup_file.delete();
//rinomina file esistente appendendo .old
                if (!backup_file.renameTo(old_backup_file))
                    Log.d(tag, "failed to rename old backup file in createAutoRecovery");
            }
//crea file backup
            try (GZIPOutputStream f_out = new GZIPOutputStream(
                    new BufferedOutputStream(new FileOutputStream(backup_file)))) {
/* using object stream
        ObjectOutputStream obj_out = new ObjectOutputStream(f_out);
        obj_out.writeObject(content);
*/
                FileManager.writeFile(f_out, true, fileHandler.encodeFile(), fileHandler.getMimeType());
            } catch (IOException | JSONException | NoSuchAlgorithmException e) {
                Log.w(tag, "createAutoRecovery: "+e.getMessage()+" writing " + filename);
            }
        }
    }


    /******************************************************************************
     * Storage Access Framework (Android 4.4 and later)
     ******************************************************************************/
    private void createFile(String mimeType, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        mActivity.startActivityForResult(intent, REQUEST_CODE_WRITE_FILE);
    }

    private boolean updateDocument(Uri uri) throws JSONException {
        try {
            ParcelFileDescriptor pfd = mActivity.getContentResolver().openFileDescriptor(uri, "w");
            if (pfd == null) {
                Log.d(tag, "error: pfd is null in updateDocument()");
                return false;
            }
            GZIPOutputStream f_out = new GZIPOutputStream(
                    new BufferedOutputStream(new FileOutputStream(pfd.getFileDescriptor())));
            writeFile(f_out, !backupMode, fileHandler.encodeFile(), fileHandler.getMimeType());//backupmode needs old format
            f_out.close();
            pfd.close();
            return true;
        } catch (IOException e) {
            Log.d(tag, "updateDocument()", e);
        } catch (NoSuchAlgorithmException e) {
//ignore it, should never occur
        }
        return false;
    }

    public void openFileSAF(String mimetype, boolean only_local) {//scopedstorage
        if (debug)
            Log.d(tag, "openFileSAF");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);//oppure ACTION_OPEN_DOCUMENT_TREE ?
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimetype);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);//necessario ?
        if (only_local)
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        mActivity.startActivityForResult(intent, REQUEST_CODE_READ_FILE);
    }

    public boolean createFileSAF(String filename) {//scopedstorage
        if (debug)
            Log.d(tag, "createFileSAF");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(fileHandler.getMimeType());
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            mActivity.startActivityForResult(intent, REQUEST_CODE_WRITE_FILE);
            return true;
        } catch (ActivityNotFoundException ex) {
            Log.d(tag, "cannot launch SAF", ex);
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public boolean create_file_downloads_Q(String filename, String mimetype, String content) {//scopedstorage: save file in download folder, according to new method required by Android R
        ContentResolver resolver = mActivity.getContentResolver();
        Uri downloadedFileUri = filename2uri_downloads_Q(filename);
        if (downloadedFileUri != null) {//file exists -> delete it (serve veramente?)
            int numFilesRemoved = resolver.delete(downloadedFileUri, null, null);
            if (debug)
                Log.d(tag, "numFilesRemoved = "+numFilesRemoved);
        }
        ContentValues fileContent = new ContentValues();
        fileContent.put(MediaStore.Downloads.DISPLAY_NAME, filename);
        fileContent.put(MediaStore.Downloads.MIME_TYPE, mimetype);
//following line is needed only in case of sub-folder, but it is better to avoid sub-folder due to side-effects!
//            fileContent.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/livio/test/a/b");
        fileContent.put(MediaStore.Downloads.IS_PENDING, 1);
        downloadedFileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, fileContent);
        if (debug)
            Log.d(tag, "downloadedFileUri = "+downloadedFileUri);
        if (downloadedFileUri != null) {
            try {
                GZIPOutputStream ostream = new GZIPOutputStream(new BufferedOutputStream(resolver.openOutputStream(downloadedFileUri, "rw")));
                ostream.write(content.getBytes(StandardCharsets.UTF_8));
                ostream.flush();
                ostream.close();

                fileContent.clear();
                fileContent.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(downloadedFileUri, fileContent, null, null);
                if (debug)
                    Log.d(tag, "file saved to downloadedFileUri");
                return true;
            } catch (IOException e) {
                Log.e(tag, "error writing to downloadedFileUri", e);
            }
        } else Log.e(tag, "downloadedFileUri is null");
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Uri filename2uri_downloads_Q(String filename) {//scopedstorage: retrieves the uri from filename, if exists in downloads (otherwise NULL)
/*
problema: in Android 11 non si riesce ad usare in modo sicuro filename2uri_downloads_Q(), quindi usiamo per il restore comunque il SAF, come nel caso di storage esterno
il problema si verifica in questa situazione:
   a) esegui backup
   b) disinstalla applicazione
   c) reinstalla applicazione
   d) esegui restore-->non funziona se fatto usando readLocalFile() e filename2uri_downloads_Q(), non è chiaro il motivo, aperta https://issuetracker.google.com/issues/164317563
   work around: usare il SAF per il restore (problema utente: deve trovare da solo dove si trova il file)
 */
        if (debug)
            Log.d(tag, "filename2uri_downloads_Q("+filename+")");
        ContentResolver resolver = mActivity.getContentResolver();
        try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                null, null, null, null)) {
            if (debug)
                Log.d(tag, DatabaseUtils.dumpCursorToString(cursor));
            int nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                if (debug)
                    Log.d(tag, "filename2uri_downloads_Q:"+name);
                if (filename.equals(name)) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                    return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                }
            }
        }
        return null;//filename not found
    }

}
