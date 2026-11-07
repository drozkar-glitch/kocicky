package cz.mares.kocicky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CatAvatar(
    cat: CatProfile,
    size: Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val avatarBg = if (isDark) Color(0xFF2E2229) else Color(0xFFFCEEF2)

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
            .background(avatarBg)
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
                    drawCircle(
                        color = if (isDark) Color(0xFF382B26) else Color(0xFFF7E6DC),
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

                    drawCircle(
                        color = furMain,
                        radius = w * 0.31f,
                        center = Offset(w * 0.5f, h * 0.50f)
                    )

                    drawOval(
                        color = Color(0xFFFFF5EB),
                        topLeft = Offset(w * 0.32f, h * 0.48f),
                        size = Size(w * 0.36f, h * 0.26f)
                    )

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

                    drawRoundRect(
                        color = Color(0xFF2D1E17),
                        topLeft = Offset(w * 0.43f, h * 0.52f),
                        size = Size(w * 0.14f, h * 0.09f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    drawOval(
                        color = Color(0xFFF28B9C),
                        topLeft = Offset(w * 0.46f, h * 0.62f),
                        size = Size(w * 0.08f, h * 0.11f)
                    )

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
                    val isRagdoll = cat.breedType == "RAGDOLL"
                    val isMaineCoon = cat.breedType == "MAINE_COON"

                    drawCircle(
                        color = if (isDark) Color(0xFF362A31) else if (isRagdoll) Color(0xFFFDF6F0) else Color(0xFFF9E8DF),
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
                        drawCircle(color = earOuterColor, radius = w * 0.03f, center = Offset(w * 0.18f, h * 0.12f))
                        drawCircle(color = earOuterColor, radius = w * 0.03f, center = Offset(w * 0.82f, h * 0.12f))
                    }

                    drawCircle(
                        color = headColor,
                        radius = w * 0.31f,
                        center = Offset(w * 0.5f, h * 0.54f)
                    )

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

@Composable
fun PetWeightAndVitalityChart(
    cat: CatProfile,
    records: List<WeightRecord>,
    onAddWeightClick: () -> Unit,
    onDeleteWeightRecord: (WeightRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF221A1F) else Color(0xFFFDFBF7)
    val chartBoxBg = if (isDark) Color(0xFF2B2027) else Color(0xFFFCEEF2)
    val rowItemBg = if (isDark) Color(0xFF33272F) else Color(0xFFF5EBE6)
    val borderCol = if (isDark) Color(0xFF4A3742) else Color(0xFFF2D0DC)
    val textPrimary = if (isDark) Color(0xFFF8EFEA) else Color(0xFF5C4033)
    val textMuted = if (isDark) Color(0xFFC4B2AA) else Color(0xFF8C6D5E)
    val accentPink = Color(0xFFE78FB3)
    val deepPink = if (isDark) Color(0xFFF49AC0) else Color(0xFFD46A92)

    var showHistoryList by remember { mutableStateOf(false) }

    val displayRecords = remember(records, cat.weightKg, cat.lastCheckedDate) {
        if (records.isNotEmpty()) {
            records.takeLast(7)
        } else {
            val fallbackKg = WeightFormatHelper.parseWeightKg(cat.weightKg) ?: 4.0f
            listOf(
                WeightRecord(
                    id = "fallback_${cat.id}",
                    catId = cat.id,
                    dateText = cat.lastCheckedDate.ifBlank { PlatformManager.currentDateString() },
                    weightKg = fallbackKg,
                    note = "Aktuální váha"
                )
            )
        }
    }

    val latestWeight = displayRecords.lastOrNull()?.weightKg
        ?: WeightFormatHelper.parseWeightKg(cat.weightKg)
        ?: 4.0f
    val prevWeight = if (displayRecords.size >= 2) displayRecords[displayRecords.size - 2].weightKg else null
    val weightDiff = if (prevWeight != null) latestWeight - prevWeight else null

    Card(
        backgroundColor = cardBg,
        shape = RoundedCornerShape(18.dp),
        elevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderCol, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CatAvatar(cat = cat, size = 44.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "📊 ${cat.name} – Váha a kondice",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Aktuálně: ${WeightFormatHelper.formatWeight(latestWeight)} kg",
                                color = deepPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (weightDiff != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(chartBoxBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = WeightFormatHelper.formatDiff(weightDiff),
                                        color = textPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onAddWeightClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = accentPink,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚖️ Zvážit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(chartBoxBg)
                    .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayRecords.forEachIndexed { idx, rec ->
                        val isLast = idx == displayRecords.lastIndex
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${WeightFormatHelper.formatWeight(rec.weightKg)} kg",
                                color = if (isLast) deepPink else textPrimary,
                                fontSize = 10.sp,
                                fontWeight = if (isLast) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val count = displayRecords.size
                    val minW = (displayRecords.minOfOrNull { it.weightKg } ?: 1f) - 0.25f
                    val maxW = (displayRecords.maxOfOrNull { it.weightKg } ?: 5f) + 0.25f
                    val range = (maxW - minW).coerceAtLeast(0.4f)

                    val topPad = h * 0.14f
                    val botPad = h * 0.14f
                    val usableH = (h - topPad - botPad).coerceAtLeast(10f)

                    for (g in 0..2) {
                        val gy = topPad + usableH * (g / 2f)
                        drawLine(
                            color = borderCol,
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val points = displayRecords.mapIndexed { i, rec ->
                        val cx = w * ((i + 0.5f) / count.toFloat())
                        val normY = ((rec.weightKg - minW) / range).coerceIn(0f, 1f)
                        val cy = topPad + usableH * (1f - normY)
                        Offset(cx, cy)
                    }

                    if (points.size >= 2) {
                        val fillPath = Path().apply {
                            moveTo(points.first().x, h - botPad)
                            points.forEach { pt -> lineTo(pt.x, pt.y) }
                            lineTo(points.last().x, h - botPad)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accentPink.copy(alpha = 0.35f),
                                    accentPink.copy(alpha = 0.04f)
                                ),
                                startY = topPad,
                                endY = h - botPad
                            )
                        )

                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = deepPink,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    points.forEachIndexed { idx, pt ->
                        val isLast = idx == points.lastIndex
                        drawCircle(
                            color = if (isDark) Color(0xFF181316) else Color.White,
                            radius = if (isLast) 6.5.dp.toPx() else 5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = if (isLast) deepPink else accentPink,
                            radius = if (isLast) 4.5.dp.toPx() else 3.2.dp.toPx(),
                            center = pt
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    displayRecords.forEach { rec ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = WeightFormatHelper.shortDateLabel(rec.dateText),
                                color = textMuted,
                                fontSize = 9.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💖 Vitalita: ${cat.energyLevel} %  •  ${cat.mood}",
                    color = textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (showHistoryList) "Skrýt historii ▲" else "Historie vážení (${records.size}) ▼",
                    color = deepPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showHistoryList = !showHistoryList }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            if (showHistoryList && records.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                records.reversed().take(8).forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(rowItemBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 ${rec.dateText}  •  ${WeightFormatHelper.formatWeight(rec.weightKg)} kg" +
                                    if (rec.note.isNotBlank()) " (${rec.note})" else "",
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (records.size > 1) {
                            Text(
                                text = "🗑️",
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onDeleteWeightRecord(rec) }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
