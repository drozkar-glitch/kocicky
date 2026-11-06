package cz.mares.kocicky

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    private var photoPickerLauncher: ActivityResultLauncher<String>? = null
    private var notifPermissionLauncher: ActivityResultLauncher<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformManager.initAndroid(applicationContext)

        photoPickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { selectedUri: Uri? ->
            if (selectedUri != null) {
                val encodedPhoto = encodeUriToCompactBase64(selectedUri)
                PlatformManager.deliverPickedPhoto(encodedPhoto)
            } else {
                PlatformManager.deliverPickedPhoto(null)
            }
        }

        notifPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _ -> }

        PlatformManager.photoLauncherAction = {
            photoPickerLauncher?.launch("image/*")
        }

        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        if (PlatformManager.photoLauncherAction != null) {
            PlatformManager.photoLauncherAction = null
        }
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun encodeUriToCompactBase64(uri: Uri): String? {
        return try {
            val stream = contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            if (original == null) return null

            val maxSide = 360
            val ratio = original.width.toFloat() / original.height.toFloat().coerceAtLeast(1f)
            val targetW: Int
            val targetH: Int
            if (original.width >= original.height) {
                targetW = maxSide
                targetH = (maxSide / ratio).toInt().coerceAtLeast(1)
            } else {
                targetH = maxSide
                targetW = (maxSide * ratio).toInt().coerceAtLeast(1)
            }

            val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
