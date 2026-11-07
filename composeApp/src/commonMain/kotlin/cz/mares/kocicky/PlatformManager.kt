package cz.mares.kocicky

import androidx.compose.ui.graphics.ImageBitmap

expect object PlatformManager {
    fun currentTimeMillis(): Long
    fun currentDateString(): String

    fun loadLocalData(): String?
    fun saveLocalData(json: String)

    fun loadFamilyCode(): String
    fun saveFamilyCode(code: String)

    fun loadFamilyPin(): String
    fun saveFamilyPin(pin: String)

    fun syncToFirebase(
        familyCode: String,
        familyPin: String,
        jsonData: String,
        onResult: (Boolean, String) -> Unit
    )

    fun fetchFromFirebase(
        familyCode: String,
        familyPin: String,
        onResult: (Boolean, String?, String) -> Unit
    )

    fun scheduleMedicationNotification(
        id: String,
        catName: String,
        medName: String,
        dosage: String,
        timeHhMm: String
    )

    fun cancelMedicationNotification(id: String)

    fun sendImmediateTestNotification(title: String, message: String)

    fun pickPhotoFromGallery(onResult: (String?) -> Unit)

    fun decodeBase64ToBitmap(base64: String): ImageBitmap?
}
