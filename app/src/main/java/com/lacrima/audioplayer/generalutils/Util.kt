package com.lacrima.audioplayer.generalutils

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import timber.log.Timber

object Util {
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
     * Convert pixels to dp
     */
    val Int.toDp
        get() = (this / Resources.getSystem().displayMetrics.density).toInt()

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