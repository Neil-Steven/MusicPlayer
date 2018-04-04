package club.neils.musicplayer

import android.app.Activity
import android.content.pm.PackageManager

fun Activity.getAppName(): String {
    var appName = ""
    try {
        val labelRes = this.packageManager.getPackageInfo(this.packageName, 0).applicationInfo.labelRes
        appName = this.resources.getString(labelRes)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return appName
}

fun Activity.getVersionName(): String {
    var versionName = ""
    try {
        versionName = this.packageManager.getPackageInfo(this.packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return versionName
}
