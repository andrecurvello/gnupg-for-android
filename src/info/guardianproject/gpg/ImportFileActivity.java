
package info.guardianproject.gpg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.freiheit.gnupg.GnuPGException;

public class ImportFileActivity extends ActionBarActivity {
    private static final String TAG = ImportFileActivity.class.getSimpleName();

    private FragmentManager mFragmentManager;
    private FileDialogFragment mFileDialog;
    private Handler mReturnHandler;
    private Messenger mMessenger;
    private File mDeleteThisFileAfterImport;

    private MimeTypeMap mMimeTypeMap;

    // used to find any existing instance of the fragment, in case of rotation,
    static final String GPG2_TASK_FRAGMENT_TAG = TAG;

    public static final String[] supportedFileTypes = {
            ".asc", ".gpg", ".key", ".pkr", ".skr"
    };
    public static final String[] supportedMimeTypes = {
            "application/pgp-keys"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mFragmentManager = getSupportFragmentManager();

        mMimeTypeMap = MimeTypeMap.getSingleton();

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        // Message is received after file is selected
        mReturnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == FileDialogFragment.MESSAGE_CANCELED) {
                    cancel();
                } else if (message.what == FileDialogFragment.MESSAGE_OK) {
                    Bundle data = message.getData();
                    File f = new File(data.getString(FileDialogFragment.MESSAGE_DATA_FILENAME));
                    boolean deleteAfterImport = data
                            .getBoolean(FileDialogFragment.MESSAGE_DATA_CHECKED);
                    Log.d(TAG, "importFilename: " + f);
                    Log.d(TAG, "deleteAfterImport: " + deleteAfterImport);
                    runImport(f, deleteAfterImport);
                } else if (message.what == Gpg2TaskFragment.GPG2_TASK_FINISHED) {
                    if (mDeleteThisFileAfterImport != null)
                        mDeleteThisFileAfterImport.delete();
                    notifyImportComplete();
                }
            }
        };
        // Create a new Messenger for the communication back
        mMessenger = new Messenger(mReturnHandler);

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent); // Handle text being sent
            } else {
                handleSendBinary(intent); // Handle single image being sent
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            handleSendMultipleBinaries(intent);
        } else {
            Uri uri = intent.getData();
            if (uri == null)
                showImportFromFileDialog("");
            else
                showImportFromFileDialog(uri.getPath());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "Activity Result: " + requestCode + " " + resultCode);
        if (resultCode == RESULT_CANCELED || data == null)
            return;

        switch (requestCode) {
            case GpgApplication.FILENAME: { // file picker result returned
                if (resultCode == RESULT_OK) {
                    try {
                        String path = data.getData().getPath();
                        Log.d(TAG, "path=" + path);

                        // set filename used in export/import dialogs
                        mFileDialog.setFilename(path);
                    } catch (NullPointerException e) {
                        Log.e(TAG, "Nullpointer while retrieving path!", e);
                    }
                }
                return;
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private boolean isSupportedFileType(Uri uri) {
        String path = uri.getLastPathSegment();
        String extension = path.substring(path.lastIndexOf('.'), path.length()).toLowerCase();
        for (String filetype : supportedFileTypes)
            if (extension.equals(filetype))
                return true;
        String mimeType = mMimeTypeMap.getMimeTypeFromExtension(extension);
        for (String supported : supportedFileTypes)
            if (mimeType.equals(supported))
                return true;
        return false;
    }

    void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (!TextUtils.isEmpty(sharedText)) {
            try {
                File importFile = File.createTempFile("import", ".asc");
                Log.d(TAG, "handle send text importFile: " + importFile);
                FileUtils.writeStringToFile(importFile, sharedText);
                runImport(importFile, true);
            } catch (IOException e) {
                e.printStackTrace();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    void handleSendBinary(Intent intent) {
        Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (!isSupportedFileType(uri))
            return;
        try {
            File importFile = File.createTempFile("import", ".gpg");
            Log.d(TAG, "handle send binary importFile: " + importFile);
            InputStream in = getContentResolver().openInputStream(uri);
            FileUtils.copyInputStreamToFile(in, importFile);
            runImport(importFile, true);
        } catch (IOException e) {
            e.printStackTrace();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    void handleSendMultipleBinaries(Intent intent) {
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        for (Uri uri : uris) {
            Log.v(TAG, "handle multiple binaries: " + uri);
            handleSendBinary(new Intent().putExtra(Intent.EXTRA_STREAM, uri));
        }
    }

    /**
     * Show to dialog from where to import keys
     */
    public void showImportFromFileDialog(final String defaultFilename) {

        new Runnable() {
            @Override
            public void run() {
                mFileDialog = FileDialogFragment.newInstance(mMessenger,
                        getString(R.string.title_import_keys),
                        getString(R.string.dialog_specify_import_file_msg), defaultFilename,
                        null, GpgApplication.FILENAME);

                mFileDialog.show(mFragmentManager, "fileDialog");
            }
        }.run();
    }

    private void cancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void runImport(File importFile, boolean deleteAfterImport) {
        if (!importFile.exists()) {
            String errorMsg = String.format(
                    getString(R.string.error_file_does_not_exist_format),
                    importFile);
            Toast.makeText(getBaseContext(), errorMsg, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String keyFilename = importFile.getCanonicalPath();
            if (deleteAfterImport)
                mDeleteThisFileAfterImport = importFile;
            else
                mDeleteThisFileAfterImport = null;
            String args = " --import '" + keyFilename + "'";
            Gpg2TaskFragment gpg2Task = new Gpg2TaskFragment();
            gpg2Task.configTask(mMessenger, new Gpg2TaskFragment.Gpg2Task(), args);
            gpg2Task.show(mFragmentManager, GPG2_TASK_FRAGMENT_TAG);
            Log.d(TAG, "import launch complete");
        } catch (GnuPGException e) {
            Log.e(TAG, "File import failed: ");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "File import failed: ");
            e.printStackTrace();
        }
        setResult(RESULT_OK);
        finish();

    }

    private void notifyImportComplete() {
        Log.d(TAG, "import complete, sending broadcast");
        GpgApplication.triggerContactsSync();
    }

}
