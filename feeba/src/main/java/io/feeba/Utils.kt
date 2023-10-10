/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.feeba

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.view.View
import androidx.annotation.Keep
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationManagerCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import io.feeba.lifecycle.LogLevel
import io.feeba.lifecycle.Logger
import io.feeba.ui.ViewUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object Utils {
    enum class SchemaType(private val text: String) {
        DATA("data"), HTTPS("https"), HTTP("http");

        companion object {
            fun fromString(text: String?): SchemaType? {
                for (type in values()) {
                    if (type.text.equals(text, ignoreCase = true)) {
                        return type
                    }
                }
                return null
            }
        }
    }

    private fun supportsADM(): Boolean {
        return try {
            // Class only available on the FireOS and only when the following is in the AndroidManifest.xml.
            // <amazon:enable-feature android:name="com.amazon.device.messaging" android:required="false"/>
            Class.forName("com.amazon.device.messaging.ADM")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }


    val netType: Int?
        get() {
            val cm = Feeba.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo = cm.activeNetworkInfo
            if (netInfo != null) {
                val networkType = netInfo.type
                return if (networkType == ConnectivityManager.TYPE_WIFI || networkType == ConnectivityManager.TYPE_ETHERNET) 0 else 1
            }
            return null
        }
    val carrierName: String?
        get() = try {
            val manager = Feeba.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            // May throw even though it's not in noted in the Android docs.
            // Issue #427
            val carrierName = manager.networkOperatorName
            if ("" == carrierName) null else carrierName
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }

    val NO_RETRY_NETWROK_REQUEST_STATUS_CODES = intArrayOf(401, 402, 403, 404, 410)
    fun shouldRetryNetworkRequest(statusCode: Int): Boolean {
        for (code in NO_RETRY_NETWROK_REQUEST_STATUS_CODES) if (statusCode == code) return false
        return true
    }

    // Interim method that works around Proguard's overly aggressive assumenosideeffects which
    // ignores keep rules.
    // This is specifically designed to address Proguard removing catches for NoClassDefFoundError
    // when the config has "-assumenosideeffects" with
    // java.lang.Class.getName() & java.lang.Object.getClass().
    // This @Keep annotation is key so this method does not get removed / inlined.
    // Addresses issue https://github.com/OneSignal/OneSignal-Android-SDK/issues/1423
    @Keep
    private fun opaqueHasClass(_class: Class<*>): Boolean {
        return true
    }
    private fun hasWakefulBroadcastReceiver(): Boolean {
        return try {
            // noinspection ConstantConditions
            WakefulBroadcastReceiver::class.java != null
        } catch (e: Throwable) {
            false
        }
    }

    private fun hasNotificationManagerCompat(): Boolean {
        return try {
            // noinspection ConstantConditions
            NotificationManagerCompat::class.java != null
        } catch (e: Throwable) {
            false
        }
    }

    private fun hasJobIntentService(): Boolean {
        return try {
            // noinspection ConstantConditions
            JobIntentService::class.java != null
        } catch (e: Throwable) {
            false
        }
    }

    private fun packageInstalledAndEnabled(packageName: String): Boolean {
        return try {
            val pm: PackageManager = Feeba.appContext.getPackageManager()
            val info = pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            info.applicationInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getManifestMetaBundle(context: Context): Bundle? {
        val ai: ApplicationInfo
        try {
            ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            return ai.metaData
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.log(LogLevel.ERROR, "Manifest application info not found", e)
        }
        return null
    }

    fun getManifestMetaBoolean(context: Context, metaName: String?): Boolean {
        val bundle = getManifestMetaBundle(context)
        return bundle?.getBoolean(metaName) ?: false
    }

    fun getManifestMeta(context: Context, metaName: String?): String? {
        val bundle = getManifestMetaBundle(context)
        return bundle?.getString(metaName)
    }

    fun getResourceString(context: Context, key: String?, defaultStr: String): String {
        val resources = context.resources
        val bodyResId = resources.getIdentifier(key, "string", context.packageName)
        return if (bodyResId != 0) resources.getString(bodyResId) else defaultStr
    }

    fun isValidEmail(email: String?): Boolean {
        if (email == null) return false
        val emRegex = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$"
        val pattern = Pattern.compile(emRegex)
        return pattern.matcher(email).matches()
    }

    fun isStringNotEmpty(body: String?): Boolean {
        return !TextUtils.isEmpty(body)
    }

    // Get the app's permission which will be false if the user disabled notifications for the app
    //   from Settings > Apps or by long pressing the notifications and selecting block.
    //   - Detection works on Android 4.4+, requires Android Support v4 Library 24.0.0+
    fun areNotificationsEnabled(context: Context?): Boolean {
        try {
            return NotificationManagerCompat.from(Feeba.appContext).areNotificationsEnabled()
        } catch (t: Throwable) {
        }
        return true
    }

    val isRunningOnMainThread: Boolean
        get() = Thread.currentThread() == Looper.getMainLooper().thread

    fun runOnMainUIThread(runnable: Runnable) {
        if (Looper.getMainLooper().thread === Thread.currentThread()) runnable.run() else {
            val handler = Handler(Looper.getMainLooper())
            handler.post(runnable)
        }
    }

    fun runOnMainThreadDelayed(runnable: Runnable?, delay: Int) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(runnable!!, delay.toLong())
    }

    fun getTargetSdkVersion(context: Context): Int {
        val packageName = context.packageName
        val packageManager = context.packageManager
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            return applicationInfo.targetSdkVersion
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
    }

    fun isValidResourceName(name: String?): Boolean {
        return name != null && !name.matches("^[0-9]".toRegex())
    }

    fun getSoundUri(context: Context, sound: String?): Uri? {
        val resources = context.resources
        val packageName = context.packageName
        var soundId: Int
        if (isValidResourceName(sound)) {
            soundId = resources.getIdentifier(sound, "raw", packageName)
            if (soundId != 0) return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId)
        }
        soundId = resources.getIdentifier("onesignal_default_sound", "raw", packageName)
        return if (soundId != 0) Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + soundId) else null
    }

    fun parseVibrationPattern(fcmBundle: JSONObject): LongArray? {
        try {
            val patternObj = fcmBundle.opt("vib_pt")
            val jsonVibArray: JSONArray
            jsonVibArray = if (patternObj is String) JSONArray(patternObj) else patternObj as JSONArray
            val longArray = LongArray(jsonVibArray.length())
            for (i in 0 until jsonVibArray.length()) longArray[i] = jsonVibArray.optLong(i)
            return longArray
        } catch (e: JSONException) {
        }
        return null
    }

    fun sleep(ms: Int) {
        try {
            Thread.sleep(ms.toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun openURLInBrowser(url: String) {
        openURLInBrowser(Uri.parse(url.trim { it <= ' ' }))
    }

    private fun openURLInBrowser(uri: Uri) {
        val intent = openURLInBrowserIntent(uri)
        Feeba.appContext.startActivity(intent)
    }

    fun openURLInBrowserIntent(uri: Uri): Intent {
        var uri = uri
        var type = if (uri.scheme != null) SchemaType.fromString(uri.scheme) else null
        if (type == null) {
            type = SchemaType.HTTP
            if (!uri.toString().contains("://")) {
                uri = Uri.parse("http://$uri")
            }
        }
        val intent: Intent
        when (type) {
            SchemaType.DATA -> {
                intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
                intent.data = uri
            }

            SchemaType.HTTPS, SchemaType.HTTP -> intent = Intent(Intent.ACTION_VIEW, uri)
            else -> intent = Intent(Intent.ACTION_VIEW, uri)
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
        return intent
    }

    // Creates a new Set<T> that supports reads and writes from more than one thread at a time
    fun <T> newConcurrentSet(): Set<T> {
        return Collections.newSetFromMap(ConcurrentHashMap())
    }

    // Creates a new Set<String> from a Set String by converting and iterating a JSONArray
    @Throws(JSONException::class)
    fun newStringSetFromJSONArray(jsonArray: JSONArray): Set<String> {
        val stringSet: MutableSet<String> = HashSet()
        for (i in 0 until jsonArray.length()) {
            stringSet.add(jsonArray.getString(i))
        }
        return stringSet
    }

    fun hasConfigChangeFlag(activity: Activity, configChangeFlag: Int): Boolean {
        var hasFlag = false
        try {
            val configChanges = activity.packageManager.getActivityInfo(activity.componentName, 0).configChanges
            val flagInt = configChanges and configChangeFlag
            hasFlag = flagInt != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return hasFlag
    }

    fun extractStringsFromCollection(collection: Collection<Any?>?): Collection<String> {
        val result: MutableCollection<String> = ArrayList()
        if (collection == null) return result
        for (value in collection) {
            if (value is String) result.add(value)
        }
        return result
    }

    fun jsonStringToBundle(data: String): Bundle? {
        return try {
            val jsonObject = JSONObject(data)
            val bundle = Bundle()
            val iterator: Iterator<*> = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next() as String
                val value = jsonObject.getString(key)
                bundle.putString(key, value)
            }
            bundle
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    fun shouldLogMissingAppIdError(appId: String?): Boolean {
        if (appId != null) return false

        // Wrapper SDKs can't normally call on Application.onCreate so just count this as informational.
        Logger.log(
            LogLevel.DEBUG, "OneSignal was not initialized, " +
                    "ensure to always initialize OneSignal from the onCreate of your Application class."
        )
        return true
    }

    fun getRandomDelay(minDelay: Int, maxDelay: Int): Int {
        return Random().nextInt(maxDelay + 1 - minDelay) + minDelay
    }

    fun getRootCauseThrowable(subjectThrowable: Throwable): Throwable {
        var throwable = subjectThrowable
        while (throwable.cause != null && throwable.cause !== throwable) {
            throwable = throwable.cause!!
        }
        return throwable
    }

    fun getRootCauseMessage(throwable: Throwable): String? {
        return getRootCauseThrowable(throwable).message
    }
}