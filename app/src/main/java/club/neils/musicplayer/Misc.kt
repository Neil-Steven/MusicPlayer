package club.neils.musicplayer

import android.content.pm.PackageManager
import android.content.Context

fun getAppName(context: Context): String {
    var appName = ""
    try {
        val labelRes = context.packageManager.getPackageInfo(context.packageName, 0).applicationInfo.labelRes
        appName = context.resources.getString(labelRes)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return appName
}

fun getPackageName(context: Context): String {
    var packageName = ""
    try {
        packageName = context.packageManager.getPackageInfo(context.packageName, 0).packageName
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return packageName
}

fun getVersionCode(context: Context): Int {
    var versionCode = 0
    try {
        versionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return versionCode
}

fun getVersionName(context: Context): String {
    var versionName = ""
    try {
        versionName = context.packageManager.getPackageInfo(context.getPackageName(), 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return versionName
}
