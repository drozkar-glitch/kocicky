package cz.mares.kocicky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CatAvatar(
    cat: CatProfile,
    size: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val decodedBitmap = remember(cat.photoBase64) {
        if (cat.photoBase64.isNotBlank()) {
            PlatformManager.decodeBase64ToBitmap(cat.photoBase64)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFFCEEF2))
            .border(2.5.dp, Color(0xFFE78FB3), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = cat.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height

                if (cat.species == "DOG" || cat.breedType.startsWith("DOG")) {
                    // Kreslená ilustrace pejska podle rasy/velikosti
                    drawCircle(
                        color = Color(0xFFF7E6DC),
                        radius = w * 0.48f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    val furMain = when (cat.breedSize) {
                        "SMALL" -> Color(0xFFD99B66)
                        "LARGE" -> Color(0xFFE6B873)
                        "GIANT" -> Color(0xFF6E5041)
                        else -> Color(0xFFC68B59)
                    }
                    val earColor = when (cat.breedSize) {
                        "GIANT" -> Color(0xFF3E2B22)
                        else -> Color(0xFF8C583A)
                    }

                    // Klapací psí ouška po stranách
                    drawOval(
                        color = earColor,
                        topLeft = Offset(w * 0.10f, h * 0.25f),
                        size = Size(w * 0.22f, h * 0.42f)
                    )
                    drawOval(
                        color = earColor,
                        topLeft = Offset(w * 0.68f, h * 0.25f),
                        size = Size(w * 0.22f, h * 0.42f)
                    )

                    // Hlavička pejska
                    drawCircle(
                        color = furMain,
                        radius = w * 0.31f,
                        center = Offset(w * 0.5f, h * 0.50f)
                    )

                    // Světlý čumáček / náprsenka
                    drawOval(
                        color = Color(0xFFFFF5EB),
                        topLeft = Offset(w * 0.32f, h * 0.48f),
                        size = Size(w * 0.36f, h * 0.26f)
                    )

                    // Očička pejska
                    drawCircle(
                        color = Color(0xFF3B2314),
                        radius = w * 0.048f,
                        center = Offset(w * 0.38f, h * 0.44f)
                    )
                    drawCircle(
                        color = Color(0xFF3B2314),
                        radius = w * 0.048f,
                        center = Offset(w * 0.62f, h * 0.44f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.016f,
                        center = Offset(w * 0.365f, h * 0.425f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.016f,
                        center = Offset(w * 0.605f, h * 0.425f)
                    )

                    // Černý psí čumák
                    drawRoundRect(
                        color = Color(0xFF2D1E17),
                        topLeft = Offset(w * 0.43f, h * 0.52f),
                        size = Size(w * 0.14f, h * 0.09f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Veselý růžový jazýček
                    drawOval(
                        color = Color(0xFFF28B9C),
                        topLeft = Offset(w * 0.46f, h * 0.62f),
                        size = Size(w * 0.08f, h * 0.11f)
                    )

                    // Obojek se známkou
                    drawRoundRect(
                        color = Color(0xFFE78FB3),
                        topLeft = Offset(w * 0.30f, h * 0.76f),
                        size = Size(w * 0.40f, h * 0.07f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawCircle(
                        color = Color(0xFFFFD54F),
                        radius = w * 0.04f,
                        center = Offset(w * 0.50f, h * 0.84f)
                    )
                } else {
                    // Kreslená ilustrace kočičky podle rasy
                    val isRagdoll = cat.breedType == "RAGDOLL"
                    val isMaineCoon = cat.breedType == "MAINE_COON"

                    drawCircle(
                        color = if (isRagdoll) Color(0xFFFDF6F0) else Color(0xFFF9E8DF),
                        radius = w * 0.48f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    val headColor = when {
                        isRagdoll -> Color(0xFFF7EFE6)
                        isMaineCoon -> Color(0xFFD99B66)
                        else -> Color(0xFFE8C5B0)
                    }
                    val earOuterColor = when {
                        isRagdoll -> Color(0xFF7B6054)
                        isMaineCoon -> Color(0xFF9C623E)
                        else -> Color(0xFFC89D84)
                    }

                    // Špičatá kočičí ouška
                    val leftEar = Path().apply {
                        moveTo(w * 0.22f, h * 0.42f)
                        lineTo(w * 0.18f, h * 0.14f)
                        lineTo(w * 0.42f, h * 0.28f)
                        close()
                    }
                    val rightEar = Path().apply {
                        moveTo(w * 0.78f, h * 0.42f)
                        lineTo(w * 0.82f, h * 0.14f)
                        lineTo(w * 0.58f, h * 0.28f)
                        close()
                    }
                    drawPath(leftEar, color = earOuterColor)
                    drawPath(rightEar, color = earOuterColor)

                    if (isMaineCoon) {
                        // Štětičky na uších pro Mainskou mývalí
                        drawCircle(color = earOuterColor, radius = w * 0.03f, center = Offset(w * 0.18f, h * 0.12f))
                        drawCircle(color = earOuterColor, radius = w * 0.03f, center = Offset(w * 0.82f, h * 0.12f))
                    }

                    // Hlavička kočičky
                    drawCircle(
                        color = headColor,
                        radius = w * 0.31f,
                        center = Offset(w * 0.5f, h * 0.54f)
                    )

                    // Očička (modré pro Ragdollku, jantarově zelené pro ostatní)
                    val eyeColor = if (isRagdoll) Color(0xFF4A90E2) else Color(0xFF689F38)
                    drawCircle(
                        color = eyeColor,
                        radius = w * 0.052f,
                        center = Offset(w * 0.38f, h * 0.50f)
                    )
                    drawCircle(
                        color = eyeColor,
                        radius = w * 0.052f,
                        center = Offset(w * 0.62f, h * 0.50f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.018f,
                        center = Offset(w * 0.365f, h * 0.485f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.018f,
                        center = Offset(w * 0.605f, h * 0.485f)
                    )

                    // Růžový čumáček
                    drawCircle(
                        color = Color(0xFFE78FB3),
                        radius = w * 0.032f,
                        center = Offset(w * 0.5f, h * 0.58f)
                    )
                }
            }
        }
    }
}
