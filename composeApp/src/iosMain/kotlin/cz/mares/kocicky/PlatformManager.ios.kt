package cz.mares.kocicky

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.UserNotifications.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object PlatformManager {
    private const val KEY_STATE_JSON = "terezciny_kocicky_state_v1"
    private const val KEY_FAMILY_CODE = "terezciny_kocicky_family_v2"
    private const val KEY_FAMILY_PIN = "terezciny_kocicky_pin_v2"
    private const val FIREBASE_BASE = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app"

    actual fun loadLocalData(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(KEY_STATE_JSON)
    }

    actual fun saveLocalData(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, forKey = KEY_STATE_JSON)
    }

    actual fun loadFamilyCode(): String {
        return NSUserDefaults.standardUserDefaults.stringForKey(KEY_FAMILY_CODE) ?: ""
    }

    actual fun saveFamilyCode(code: String) {
        NSUserDefaults.standardUserDefaults.setObject(code.trim(), forKey = KEY_FAMILY_CODE)
    }

    actual fun loadFamilyPin(): String {
        return NSUserDefaults.standardUserDefaults.stringForKey(KEY_FAMILY_PIN) ?: ""
    }

    actual fun saveFamilyPin(pin: String) {
        NSUserDefaults.standardUserDefaults.setObject(pin.trim(), forKey = KEY_FAMILY_PIN)
    }

    private fun sanitizeKey(raw: String): String {
        return raw.trim().lowercase()
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
            .replace(" ", "_")
    }

    actual fun syncToFirebase(
        familyCode: String,
        familyPin: String,
        jsonPayload: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanCode = sanitizeKey(familyCode)
        val cleanPin = sanitizeKey(familyPin)
        if (cleanCode.isBlank() || cleanPin.isBlank()) {
            onResult(false, "Nepřihlášeno")
            return
        }
        val urlStr = "$FIREBASE_BASE/kocicky/$cleanCode/$cleanPin.json"
        val nsUrl = NSURL.URLWithString(urlStr)
        if (nsUrl == null) {
            onResult(false, "URL")
            return
        }
        val req = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod("PUT")
            setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
            val bodyData = (jsonPayload as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            setHTTPBody(bodyData)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { _, response, error ->
            val httpResp = response as? NSHTTPURLResponse
            val code = httpResp?.statusCode ?: 0L
            val success = error == null && code in 200..299
            dispatch_async(dispatch_get_main_queue()) {
                onResult(success, if (success) "OK" else "HTTP $code")
            }
        }
        task.resume()
    }

    actual fun fetchFromFirebase(
        familyCode: String,
        familyPin: String,
        onResult: (Boolean, String?, String) -> Unit
    ) {
        val cleanCode = sanitizeKey(familyCode)
        val cleanPin = sanitizeKey(familyPin)
        if (cleanCode.isBlank() || cleanPin.isBlank()) {
            onResult(false, null, "Nepřihlášeno")
            return
        }
        val urlStr = "$FIREBASE_BASE/kocicky/$cleanCode/$cleanPin.json"
        val nsUrl = NSURL.URLWithString(urlStr)
        if (nsUrl == null) {
            onResult(false, null, "URL")
            return
        }
        val req = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod("GET")
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
            val httpResp = response as? NSHTTPURLResponse
            val code = httpResp?.statusCode ?: 0L
            val text = if (data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            } else null
            val success = error == null && code in 200..299
            dispatch_async(dispatch_get_main_queue()) {
                onResult(success, text, if (success) "OK" else "HTTP $code")
            }
        }
        task.resume()
    }

    actual fun pickPhotoFromGallery(onPhotoSelected: (String?) -> Unit) {
        onPhotoSelected(null)
    }

    actual fun decodeBase64ToBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val nsData = NSData.create(base64EncodedString = base64, options = 0u) ?: return null
            val length = nsData.length.toInt()
            if (length <= 0) return null
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
            }
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractSpecificDate(text: String): Triple<Long, Long, Long>? {
        val regex = Regex("""(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})""")
        val m = regex.find(text) ?: return null
        val d = m.groupValues[1].toLongOrNull() ?: return null
        val mo = m.groupValues[2].toLongOrNull() ?: return null
        val y = m.groupValues[3].toLongOrNull() ?: return null
        return Triple(d.coerceIn(1L, 31L), mo.coerceIn(1L, 12L), y)
    }

    actual fun scheduleMedicationNotification(
        id: String,
        catName: String,
        medName: String,
        dosage: String,
        timeHhMm: String
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { granted, _ ->
            if (granted) {
                val parts = timeHhMm.split(":")
                val hour = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 8L
                val minute = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                val specificDate = extractSpecificDate(medName)

                val content = UNMutableNotificationContent().apply {
                    setTitle("🐾 $catName – $medName")
                    setBody("Čas ($timeHhMm): $dosage")
                }

                val dateComps = NSDateComponents().apply {
                    if (specificDate != null) {
                        setDay(specificDate.first)
                        setMonth(specificDate.second)
                        setYear(specificDate.third)
                    }
                    setHour(hour)
                    setMinute(minute)
                }

                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComps,
                    repeats = (specificDate == null)
                )
                val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
                center.addNotificationRequest(request, withCompletionHandler = null)
            }
        }
    }

    actual fun cancelMedicationNotification(id: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }

    actual fun sendImmediateTestNotification(title: String, message: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound
        ) { granted, _ ->
            if (granted) {
                val content = UNMutableNotificationContent().apply {
                    setTitle(title)
                    setBody(message)
                }
                val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
                val req = UNNotificationRequest.requestWithIdentifier(
                    "test_kocicky_${currentTimeMillis()}",
                    content,
                    trigger
                )
                center.addNotificationRequest(req, withCompletionHandler = null)
            }
        }
    }

    actual fun currentDateString(): String {
        val formatter = NSDateFormatter().apply {
            dateFormat = "d. M. yyyy"
        }
        return formatter.stringFromDate(NSDate())
    }

    actual fun currentTimeMillis(): Long {
        return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    }
}
