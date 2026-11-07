package cz.mares.kocicky

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initializePlatformManagerSafely()
        requestNotificationPermissionIfNeeded()

        setContent {
            App()
        }
    }

    private fun initializePlatformManagerSafely() {
        try {
            val pmClass = PlatformManager::class.java
            for (method in pmClass.declaredMethods) {
                if (method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(this::class.java)
                ) {
                    method.isAccessible = true
                    method.invoke(PlatformManager, this)
                }
            }
            for (field in pmClass.declaredFields) {
                if (field.type.isAssignableFrom(this::class.java)) {
                    field.isAccessible = true
                    field.set(PlatformManager, this)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
