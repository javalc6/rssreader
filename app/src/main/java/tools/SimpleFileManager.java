package tools;
/*
Version 1.0, 16-12-2024, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.core.util.Predicate;
import livio.rssreader.BuildConfig;
import livio.rssreader.R;

//IMPORTANT: users of SimpleFileManager MUST call processActivityResult()
public final class SimpleFileManager {
    private final static String tag = "SimpleFileManager";
    public static final boolean debug = BuildConfig.DEBUG; // shall be false in production

    public static final int REQUEST_CODE_READ_FILE = 42;
    public static final int REQUEST_CODE_WRITE_FILE = 43;

    private final Activity mActivity;
    private final Predicate<InputStream> mDecodeFile;
    private final Predicate<OutputStream> mEncodeFile;

    public SimpleFileManager(Activity activity, Predicate<InputStream> decodeFile, Predicate<OutputStream> encodeFile) {
        mActivity = activity;
        mDecodeFile = decodeFile;
        mEncodeFile = encodeFile;
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
                        if (updateDocument(uri))
                            resid = R.string.msg_export_ok;
                        else
                            resid = R.string.msg_cannot_write_file;
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
            default:
                return false;
        }
    }

    private void do_extended_readfile(Uri content) {
        String error = "file content is null";
        if (content != null)
            try {
//                Log.d(tag, "content: "+content);

                InputStream is = mActivity.getContentResolver().openInputStream(content);
                if (is != null) {
                    if (mDecodeFile.test(is)) {//twin
                        Toast.makeText(mActivity, mActivity.getString(R.string.msg_import_ok), Toast.LENGTH_SHORT).show();
                        error = null;
                    } else error = "invalid file content";
                } else {
                    error = "do_extended_readfile: <is> null pointer";
                    Log.d(tag, error);
                }
            } //            ReportBug.reportException(this, packageName + "-" + version, e);
            catch (FileNotFoundException e) {
                error = "FileNotFoundException";
                Log.d(tag, "do_extended_readfile", e);
            }
        if (error != null)//nota che readFile() ritorna null quando il file è letto correttamente
            Snackbar.make(mActivity.findViewById(android.R.id.content), mActivity.getString(R.string.msg_cannot_read_file)+" ("+error+")", Snackbar.LENGTH_LONG).show();
    }

    /******************************************************************************
     * Storage Access Framework
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

    private boolean updateDocument(Uri uri) {
        try {
            ParcelFileDescriptor pfd = mActivity.getContentResolver().openFileDescriptor(uri, "w");
            if (pfd == null) {
                Log.d(tag, "error: pfd is null in updateDocument()");
                return false;
            }
            OutputStream f_out = new BufferedOutputStream(new FileOutputStream(pfd.getFileDescriptor()));
            if (!mEncodeFile.test(f_out))
                throw new IOException("cannot write file");
            f_out.close();
            pfd.close();
            return true;
        } catch (IOException e) {
            Log.d(tag, "updateDocument()", e);
        }
        return false;
    }

    public void openFileSAF(String mimetype) {//scopedstorage
        if (debug)
            Log.d(tag, "openFileSAF");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimetype);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        mActivity.startActivityForResult(intent, REQUEST_CODE_READ_FILE);
    }

    public boolean createFileSAF(String filename, String mimetype) {//scopedstorage
        if (debug)
            Log.d(tag, "createFileSAF");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimetype);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            mActivity.startActivityForResult(intent, REQUEST_CODE_WRITE_FILE);
            return true;
        } catch (ActivityNotFoundException ex) {
            Log.d(tag, "cannot launch SAF", ex);
            return false;
        }
    }
}
