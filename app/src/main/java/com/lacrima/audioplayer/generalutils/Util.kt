package com.lacrima.audioplayer.generalutils

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import timber.log.Timber
import java.io.File

object Util {
    fun getRawUriForMetadataRetriever(context: Context, fileResource: Int): Uri {
        Timber.d("getRawUri returned ${Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + File.pathSeparator +
                "//" + context.packageName + "/raw/" + fileResource)}")
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + File.pathSeparator +
                "//" + context.packageName + "/raw/" + fileResource)
    }

    fun getRawUri(fileResource: Int): Uri {
        return RawResourceDataSource.buildRawResourceUri(fileResource)
    }

    /**
     * Convert dp to pixels
     */
    val Int.toPixels
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()

    /**
     * Apply window insets to the bottom of the view
     */
    fun setUiWindowInsetsBottom(view: View, bottomPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom + bottomPadding)
            insets
        }
    }

    /**
     * Apply window insets to the top of the view
     */
    fun setUiWindowInsetsTop(view: View, topPadding: Int = 0) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top + topPadding)
            insets
        }
    }

    /**
     * Checks connection to wi-fi only.
     * If the device is connected to mobile network, it returns false.
     * This is useful for cases when the network traffic might be metered.
     */
    @Suppress("DEPRECATION")
    fun Activity.checkIsConnectedToWiFi(): Boolean {
        var isConnectedToWiFi = false
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connMgr.activeNetwork
            if (network == null) {
                isConnectedToWiFi = false
            } else {
                val activeNetwork = connMgr.getNetworkCapabilities(network)
                if (activeNetwork == null) {
                    isConnectedToWiFi = false
                }
                if (activeNetwork != null) {
                    isConnectedToWiFi = when {
                        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        else -> false
                    }
                }
            }
        } else {
            connMgr.allNetworks.forEach { network ->
                val type = connMgr.getNetworkInfo(network)?.type
                isConnectedToWiFi = if (type == null) {
                    false
                } else {
                    type == ConnectivityManager.TYPE_WIFI
                }
            }
        }
        Timber.d("checkIsConnectedToWiFi() returned $isConnectedToWiFi")
        return isConnectedToWiFi
    }
}