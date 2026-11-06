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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CatAvatar(
    cat: CatProfile,
    size: Dp = 92.dp,
    borderColor: Color = Color(0xFFE78FB3)
) {
    val decodedBitmap = remember(cat.photoBase64) {
        if (cat.photoBase64.isNotBlank()) {
            PlatformManager.decodeBase64ToBitmap(cat.photoBase64)
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFFDFBF7))
            .border(3.dp, borderColor, CircleShape),
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
            when (cat.breedType) {
                "RAGDOLL" -> RagdollIllustration(modifier = Modifier.fillMaxSize())
                "MAINE_COON" -> MaineCoonIllustration(modifier = Modifier.fillMaxSize())
                else -> GenericCatIllustration(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RagdollIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Jemné baby-pink pozadí medailonku
        drawRect(color = Color(0xFFFCEEF2))

        // Načechraný krémový kožíšek pod hlavičkou
        drawOval(
            color = Color(0xFFF7EFE6),
            topLeft = Offset(w * 0.16f, h * 0.62f),
            size = Size(w * 0.68f, h * 0.42f)
        )

        // Tmavě čokoládová ouška (typický seal/chocolate point u Ragdollky)
        val leftEar = Path().apply {
            moveTo(w * 0.22f, h * 0.44f)
            lineTo(w * 0.16f, h * 0.16f)
            lineTo(w * 0.42f, h * 0.28f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.78f, h * 0.44f)
            lineTo(w * 0.84f, h * 0.16f)
            lineTo(w * 0.58f, h * 0.28f)
            close()
        }
        drawPath(leftEar, color = Color(0xFF6B4F43))
        drawPath(rightEar, color = Color(0xFF6B4F43))

        // Růžová vnitřní ouška
        val leftInner = Path().apply {
            moveTo(w * 0.25f, h * 0.40f)
            lineTo(w * 0.21f, h * 0.22f)
            lineTo(w * 0.38f, h * 0.30f)
            close()
        }
        val rightInner = Path().apply {
            moveTo(w * 0.75f, h * 0.40f)
            lineTo(w * 0.79f, h * 0.22f)
            lineTo(w * 0.62f, h * 0.30f)
            close()
        }
        drawPath(leftInner, color = Color(0xFFF4B6C2))
        drawPath(rightInner, color = Color(0xFFF4B6C2))

        // Krémově béžová hlavička Ragdollky
        drawOval(
            color = Color(0xFFFFFBF5),
            topLeft = Offset(w * 0.18f, h * 0.25f),
            size = Size(w * 0.64f, h * 0.54f)
        )

        // Jemná tmavší maska kolem oček (Ragdoll bicolor/point)
        drawOval(
            color = Color(0xFF8D6E63).copy(alpha = 0.28f),
            topLeft = Offset(w * 0.25f, h * 0.36f),
            size = Size(w * 0.22f, h * 0.20f)
        )
        drawOval(
            color = Color(0xFF8D6E63).copy(alpha = 0.28f),
            topLeft = Offset(w * 0.53f, h * 0.36f),
            size = Size(w * 0.22f, h * 0.20f)
        )

        // Safírově modrá očička (Safírka)
        val leftEyeCenter = Offset(w * 0.36f, h * 0.47f)
        val rightEyeCenter = Offset(w * 0.64f, h * 0.47f)
        drawCircle(color = Color(0xFF2B6CB0), radius = w * 0.068f, center = leftEyeCenter)
        drawCircle(color = Color(0xFF2B6CB0), radius = w * 0.068f, center = rightEyeCenter)
        drawCircle(color = Color(0xFF63B3ED), radius = w * 0.042f, center = leftEyeCenter)
        drawCircle(color = Color(0xFF63B3ED), radius = w * 0.042f, center = rightEyeCenter)
        drawCircle(color = Color(0xFF1A365D), radius = w * 0.025f, center = leftEyeCenter)
        drawCircle(color = Color(0xFF1A365D), radius = w * 0.025f, center = rightEyeCenter)
        // Odlesk v očích
        drawCircle(
            color = Color.White,
            radius = w * 0.016f,
            center = Offset(leftEyeCenter.x - w * 0.018f, leftEyeCenter.y - h * 0.018f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.016f,
            center = Offset(rightEyeCenter.x - w * 0.018f, rightEyeCenter.y - h * 0.018f)
        )

        // Růžové tvářičky
        drawCircle(
            color = Color(0xFFF6A6BB).copy(alpha = 0.55f),
            radius = w * 0.055f,
            center = Offset(w * 0.28f, h * 0.57f)
        )
        drawCircle(
            color = Color(0xFFF6A6BB).copy(alpha = 0.55f),
            radius = w * 0.055f,
            center = Offset(w * 0.72f, h * 0.57f)
        )

        // Roztomilý růžový čumáček
        val nose = Path().apply {
            moveTo(w * 0.46f, h * 0.54f)
            lineTo(w * 0.54f, h * 0.54f)
            lineTo(w * 0.50f, h * 0.59f)
            close()
        }
        drawPath(nose, color = Color(0xFFE78FB3))

        // Úsměv
        drawLine(
            color = Color(0xFF6B4F43),
            start = Offset(w * 0.50f, h * 0.59f),
            end = Offset(w * 0.45f, h * 0.63f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF6B4F43),
            start = Offset(w * 0.50f, h * 0.59f),
            end = Offset(w * 0.55f, h * 0.63f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MaineCoonIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Teplé béžovo-růžové pozadí
        drawRect(color = Color(0xFFF9EFE8))

        // Bohatá lví hříva Mainské mývalí (Zlatín)
        val mane = Path().apply {
            moveTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.85f, h * 0.42f)
            lineTo(w * 0.90f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.88f)
            lineTo(w * 0.28f, h * 0.88f)
            lineTo(w * 0.10f, h * 0.68f)
            lineTo(w * 0.15f, h * 0.42f)
            close()
        }
        drawPath(mane, color = Color(0xFFD9A773))

        // Vysoké uši s typickými rysími štětičkami (lynx tips)
        val leftEar = Path().apply {
            moveTo(w * 0.20f, h * 0.42f)
            lineTo(w * 0.15f, h * 0.11f)
            lineTo(w * 0.40f, h * 0.27f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(w * 0.80f, h * 0.42f)
            lineTo(w * 0.85f, h * 0.11f)
            lineTo(w * 0.60f, h * 0.27f)
            close()
        }
        drawPath(leftEar, color = Color(0xFFA66E38))
        drawPath(rightEar, color = Color(0xFFA66E38))

        // Štětičky na špičkách uší
        drawLine(
            color = Color(0xFF5C3A21),
            start = Offset(w * 0.15f, h * 0.11f),
            end = Offset(w * 0.13f, h * 0.04f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF5C3A21),
            start = Offset(w * 0.85f, h * 0.11f),
            end = Offset(w * 0.87f, h * 0.04f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        // Vnitřek uší
        val leftInner = Path().apply {
            moveTo(w * 0.23f, h * 0.38f)
            lineTo(w * 0.19f, h * 0.18f)
            lineTo(w * 0.36f, h * 0.29f)
            close()
        }
        val rightInner = Path().apply {
            moveTo(w * 0.77f, h * 0.38f)
            lineTo(w * 0.81f, h * 0.18f)
            lineTo(w * 0.64f, h * 0.29f)
            close()
        }
        drawPath(leftInner, color = Color(0xFFF2B5C4))
        drawPath(rightInner, color = Color(0xFFF2B5C4))

        // Zlatavě béžová hlava
        drawOval(
            color = Color(0xFFE6BC8A),
            topLeft = Offset(w * 0.19f, h * 0.26f),
            size = Size(w * 0.62f, h * 0.52f)
        )

        // Typické "M" žíhání na čele Mainské mývalí
        drawLine(
            color = Color(0xFF7D4E24),
            start = Offset(w * 0.42f, h * 0.35f),
            end = Offset(w * 0.46f, h * 0.29f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF7D4E24),
            start = Offset(w * 0.50f, h * 0.35f),
            end = Offset(w * 0.46f, h * 0.29f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF7D4E24),
            start = Offset(w * 0.50f, h * 0.35f),
            end = Offset(w * 0.54f, h * 0.29f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF7D4E24),
            start = Offset(w * 0.58f, h * 0.35f),
            end = Offset(w * 0.54f, h * 0.29f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Zlatavě jantarovo-zelenkavé oči
        val leftEye = Offset(w * 0.36f, h * 0.47f)
        val rightEye = Offset(w * 0.64f, h * 0.47f)
        drawCircle(color = Color(0xFFD69E2E), radius = w * 0.066f, center = leftEye)
        drawCircle(color = Color(0xFFD69E2E), radius = w * 0.066f, center = rightEye)
        drawCircle(color = Color(0xFF68D391), radius = w * 0.038f, center = leftEye)
        drawCircle(color = Color(0xFF68D391), radius = w * 0.038f, center = rightEye)
        drawCircle(color = Color(0xFF2D3748), radius = w * 0.023f, center = leftEye)
        drawCircle(color = Color(0xFF2D3748), radius = w * 0.023f, center = rightEye)
        drawCircle(
            color = Color.White,
            radius = w * 0.015f,
            center = Offset(leftEye.x - w * 0.016f, leftEye.y - h * 0.016f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.015f,
            center = Offset(rightEye.x - w * 0.016f, rightEye.y - h * 0.016f)
        )

        // Výrazný světlý čenich (typický pro Maine Coon)
        drawCircle(
            color = Color(0xFFFFF8EE),
            radius = w * 0.075f,
            center = Offset(w * 0.45f, h * 0.60f)
        )
        drawCircle(
            color = Color(0xFFFFF8EE),
            radius = w * 0.075f,
            center = Offset(w * 0.55f, h * 0.60f)
        )

        // Cihlově růžový nosík
        val nose = Path().apply {
            moveTo(w * 0.45f, h * 0.54f)
            lineTo(w * 0.55f, h * 0.54f)
            lineTo(w * 0.50f, h * 0.60f)
            close()
        }
        drawPath(nose, color = Color(0xFFD97B93))

        // Dlouhé bílé fousky
        drawLine(
            color = Color(0xFFFFFBF5),
            start = Offset(w * 0.36f, h * 0.59f),
            end = Offset(w * 0.14f, h * 0.56f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFFFFFBF5),
            start = Offset(w * 0.36f, h * 0.62f),
            end = Offset(w * 0.14f, h * 0.63f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFFFFFBF5),
            start = Offset(w * 0.64f, h * 0.59f),
            end = Offset(w * 0.86f, h * 0.56f),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFFFFFBF5),
            start = Offset(w * 0.64f, h * 0.62f),
            end = Offset(w * 0.86f, h * 0.63f),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun GenericCatIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(color = Color(0xFFFADADD))
        drawCircle(
            color = Color(0xFFFDFBF7),
            radius = w * 0.30f,
            center = Offset(w * 0.50f, h * 0.54f)
        )
        drawCircle(
            color = Color(0xFF5C4033),
            radius = w * 0.045f,
            center = Offset(w * 0.39f, h * 0.49f)
        )
        drawCircle(
            color = Color(0xFF5C4033),
            radius = w * 0.045f,
            center = Offset(w * 0.61f, h * 0.49f)
        )
        drawCircle(
            color = Color(0xFFE78FB3),
            radius = w * 0.035f,
            center = Offset(w * 0.50f, h * 0.56f)
        )
        drawCircle(
            color = Color(0xFFE78FB3),
            radius = w * 0.42f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = 3f)
        )
    }
}
