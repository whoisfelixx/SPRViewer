package tk.zwander.sprviewer.ui

import android.app.Application
import android.os.Build
import android.util.Log
import com.bugsnag.android.Bugsnag
import com.squareup.picasso.Picasso
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.sprviewer.util.BatchExportListener

class App : Application() {
    val batchExportListeners = ArrayList<BatchExportListener>()

    override fun onCreate() {
        super.onCreate()

        Bugsnag.start(this)

        Picasso.setSingletonInstance(Picasso.Builder(this).build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.setHiddenApiExemptions("L")
            } catch (e: Throwable) {
                Log.e("SPRViewer", "Hidden API bypass failed", e)
            }
        }
    }
}