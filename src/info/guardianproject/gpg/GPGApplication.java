package info.guardianproject.gpg;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public class GPGApplication extends Application {
	public static final String TAG = "gpgcli";
    public static final String PACKAGE_NAME = "info.guardianproject.gpg";
    public static String VERSION = null;

    private static Context context;

    public void onCreate(){
        super.onCreate();
        GPGApplication.context = getApplicationContext();
    }

    public static Context getGlobalApplicationContext() {
        return GPGApplication.context;
    }

    public static String getVersionString(Context context) {
        if (VERSION != null) {
            return VERSION;
        }
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            VERSION = "gpgcli v" + pi.versionName;
            return VERSION;
        } catch (NameNotFoundException e) {
            // unpossible!
            return "v0.0.0";
        }
    }

}