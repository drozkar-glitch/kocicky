package cz.mares.kocicky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// Baby Pink & Beige barevná paleta pro Terezku
private val BgBeigeLight = Color(0xFFFDFBF7)
private val BgBeigeWarm = Color(0xFFF5EBE6)
private val BabyPinkSoft = Color(0xFFFADADD)
private val BabyPinkCard = Color(0xFFFCEEF2)
private val BabyPinkAccent = Color(0xFFE78FB3)
private val BabyPinkDeep = Color(0xFFD46A92)
private val CocoaBrown = Color(0xFF5C4033)
private val CocoaMuted = Color(0xFF8C6D5E)
private val MintSuccess = Color(0xFF529E72)
private val SoftBorder = Color(0xFFF2D0DC)

private enum class MainTab(val label: String, val icon: String) {
    CATS("Kočičky", "🐾"),
    STATUS("Stavy", "💖"),
    MEDS("Léky", "💊"),
    ALERTS("Notifikace", "🔔")
}

@Composable
fun App() {
    var appState by remember {
        val savedJson = PlatformManager.loadLocalData()
        val parsed = if (savedJson != null) CatDataSerializer.deserialize(savedJson) else null
        mutableStateOf(parsed ?: DefaultCatData.createInitialState())
    }

    var familyCode by remember { mutableStateOf(PlatformManager.loadFamilyCode()) }
    var syncStatusText by remember { mutableStateOf("☁️ Připojování k serveru...") }
    var isSyncing by remember { mutableStateOf(false) }

    var activeTab by remember { mutableStateOf(MainTab.CATS) }
    var selectedCatIdFilter by remember { mutableStateOf<String?>(null) }

    // Dialogy
    var editingCat by remember { mutableStateOf<CatProfile?>(null) }
    var showAddCatDialog by remember { mutableStateOf(false) }
    var showAddMedDialog by remember { mutableStateOf(false) }
    var showAddHealthLogDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    fun persistAndSync(newState: CatAppState, toastMsg: String? = null) {
        val stamped = newState.copy(updatedAt = PlatformManager.currentTimeMillis())
        appState = stamped
        val json = CatDataSerializer.serialize(stamped)
        PlatformManager.saveLocalData(json)
        if (toastMsg != null) {
            bannerMessage = toastMsg
        }
        isSyncing = true
        syncStatusText = "☁️ Ukládám na server..."
        PlatformManager.syncToFirebase(familyCode, json) { ok, info ->
            isSyncing = false
            syncStatusText = if (ok) {
                "☁️ Uloženo na serveru ($familyCode)"
            } else {
                "📱 Uloženo v telefonu ($info)"
            }
        }
    }

    fun pullFromServer(manual: Boolean = false) {
        isSyncing = true
        syncStatusText = "☁️ Načítám ze serveru..."
        PlatformManager.fetchFromFirebase(familyCode) { ok, remoteJson, info ->
            isSyncing = false
            if (ok && !remoteJson.isNullOrBlank() && remoteJson != "null") {
                val remoteState = CatDataSerializer.deserialize(remoteJson)
                if (remoteState != null) {
                    if (manual || remoteState.updatedAt >= appState.updatedAt) {
                        appState = remoteState
                        PlatformManager.saveLocalData(CatDataSerializer.serialize(remoteState))
                        syncStatusText = "☁️ Synchronizováno ($familyCode)"
                        if (manual) {
                            bannerMessage = "Data ze serveru ($familyCode) byla úspěšně načtena!"
                        }
                    } else {
                        val localJson = CatDataSerializer.serialize(appState)
                        PlatformManager.syncToFirebase(familyCode, localJson) { upOk, _ ->
                            syncStatusText = if (upOk) "☁️ Synchronizováno ($familyCode)" else "📱 V telefonu"
                        }
                    }
                } else {
                    syncStatusText = "☁️ Server připraven ($familyCode)"
                }
            } else if (ok && (remoteJson == null || remoteJson == "null")) {
                val localJson = CatDataSerializer.serialize(appState)
                PlatformManager.syncToFirebase(familyCode, localJson) { upOk, _ ->
                    syncStatusText = if (upOk) "☁️ Uloženo na serveru ($familyCode)" else "📱 V telefonu"
                }
            } else {
                syncStatusText = "📱 Režim offline ($info)"
                if (manual) {
                    bannerMessage = "Nepodařilo se spojit se serverem: $info"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        pullFromServer(manual = false)
        appState.medications.forEach { med ->
            if (med.notificationsEnabled) {
                PlatformManager.scheduleMedicationNotification(
                    id = med.id,
                    catName = med.catName,
                    medName = med.name,
                    dosage = med.dosage,
                    timeHhMm = med.timeOfDay
                )
            }
        }
    }

    val unreadNotifCount = appState.notifications.count { !it.isRead }
    val todayStr = PlatformManager.currentDateString()
    val pendingMedsToday = appState.medications.count { it.lastGivenDate != todayStr }

    MaterialTheme(
        colors = lightColors(
            primary = BabyPinkAccent,
            primaryVariant = BabyPinkDeep,
            secondary = BabyPinkSoft,
            background = BgBeigeLight,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = CocoaBrown,
            onSurface = CocoaBrown
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BabyPinkSoft, BgBeigeLight, BgBeigeWarm)
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopHeaderBar(
                syncStatusText = syncStatusText,
                pendingMedsToday = pendingMedsToday,
                onCloudClick = { showCloudDialog = true },
                onRefreshClick = { pullFromServer(manual = true) }
            )

            if (bannerMessage != null) {
                Card(
                    backgroundColor = Color(0xFFFFF0F5),
                    shape = RoundedCornerShape(14.dp),
                    elevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .border(1.dp, BabyPinkAccent, RoundedCornerShape(14.dp))
                        .clickable { bannerMessage = null }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ ${bannerMessage ?: ""}",
                            color = CocoaBrown,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            color = BabyPinkDeep,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            CatQuickSelectorRow(
                cats = appState.cats,
                selectedCatId = selectedCatIdFilter,
                onSelectCat = { selectedCatIdFilter = it },
                onAddCatClick = { showAddCatDialog = true }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    MainTab.CATS -> CatsTabScreen(
                        cats = appState.cats.filter { selectedCatIdFilter == null || it.id == selectedCatIdFilter },
                        medications = appState.medications,
                        todayStr = todayStr,
                        onPickRealPhoto = { cat ->
                            PlatformManager.pickPhotoFromGallery { base64Photo ->
                                if (!base64Photo.isNullOrBlank()) {
                                    val updatedCats = appState.cats.map {
                                        if (it.id == cat.id) it.copy(photoBase64 = base64Photo) else it
                                    }
                                    val newLog = HealthLogItem(
                                        id = "log_${PlatformManager.currentTimeMillis()}",
                                        catId = cat.id,
                                        catName = cat.name,
                                        dateText = todayStr,
                                        title = "Nová reálná fotka kočičky 📸",
                                        detail = "Uložena nová fotografie pro ${cat.name}.",
                                        category = "Péče"
                                    )
                                    persistAndSync(
                                        appState.copy(
                                            cats = updatedCats,
                                            healthLogs = listOf(newLog) + appState.healthLogs
                                        ),
                                        "Fotka pro kočičku ${cat.name} byla uložena!"
                                    )
                                }
                            }
                        },
                        onResetToBreedIllustration = { cat ->
                            val updatedCats = appState.cats.map {
                                if (it.id == cat.id) it.copy(photoBase64 = "") else it
                            }
                            persistAndSync(
                                appState.copy(cats = updatedCats),
                                "Nastaven ilustrovaný portrét plemene (${cat.breed})."
                            )
                        },
                        onEditCat = { editingCat = it },
                        onAddCat = { showAddCatDialog = true }
                    )

                    MainTab.STATUS -> StatusAndLogsTabScreen(
                        cats = appState.cats.filter { selectedCatIdFilter == null || it.id == selectedCatIdFilter },
                        logs = appState.healthLogs.filter { selectedCatIdFilter == null || it.catId == selectedCatIdFilter },
                        onQuickUpdateCat = { updatedCat, logText ->
                            val newCats = appState.cats.map { if (it.id == updatedCat.id) updatedCat else it }
                            val newLog = HealthLogItem(
                                id = "log_${PlatformManager.currentTimeMillis()}",
                                catId = updatedCat.id,
                                catName = updatedCat.name,
                                dateText = todayStr,
                                title = "Aktualizace denního stavu 💖",
                                detail = logText,
                                category = "Zdraví"
                            )
                            persistAndSync(
                                appState.copy(
                                    cats = newCats,
                                    healthLogs = listOf(newLog) + appState.healthLogs
                                ),
                                "Stav kočičky ${updatedCat.name} byl uložen!"
                            )
                        },
                        onAddHealthLog = { showAddHealthLogDialog = true },
                        onDeleteLog = { logId ->
                            persistAndSync(
                                appState.copy(healthLogs = appState.healthLogs.filterNot { it.id == logId })
                            )
                        }
                    )

                    MainTab.MEDS -> MedicationsTabScreen(
                        medications = appState.medications.filter { selectedCatIdFilter == null || it.catId == selectedCatIdFilter },
                        todayStr = todayStr,
                        onMarkGivenToday = { med ->
                            val newRemaining = (med.remainingDoses - 1).coerceAtLeast(0)
                            val updatedMeds = appState.medications.map {
                                if (it.id == med.id) {
                                    it.copy(lastGivenDate = todayStr, remainingDoses = newRemaining)
                                } else it
                            }
                            val newLog = HealthLogItem(
                                id = "log_${PlatformManager.currentTimeMillis()}",
                                catId = med.catId,
                                catName = med.catName,
                                dateText = todayStr,
                                title = "Podán lék: ${med.name} 💊",
                                detail = "Dávka: ${med.dosage} (zbývá $newRemaining dávek).",
                                category = "Lék podán"
                            )
                            val newNotif = AppNotificationItem(
                                id = "notif_${PlatformManager.currentTimeMillis()}",
                                title = "✅ Podáno: ${med.name} (${med.catName})",
                                message = "Dnešní dávka (${med.dosage}) byla v pořádku podána.",
                                timeText = med.timeOfDay,
                                isRead = false
                            )
                            persistAndSync(
                                appState.copy(
                                    medications = updatedMeds,
                                    healthLogs = listOf(newLog) + appState.healthLogs,
                                    notifications = listOf(newNotif) + appState.notifications
                                ),
                                "Skvělé! Lék pro ${med.catName} je na dnešek odškrtnutý."
                            )
                        },
                        onToggleNotification = { med ->
                            val nextEnabled = !med.notificationsEnabled
                            if (nextEnabled) {
                                PlatformManager.scheduleMedicationNotification(
                                    id = med.id,
                                    catName = med.catName,
                                    medName = med.name,
                                    dosage = med.dosage,
                                    timeHhMm = med.timeOfDay
                                )
                            } else {
                                PlatformManager.cancelMedicationNotification(med.id)
                            }
                            val updatedMeds = appState.medications.map {
                                if (it.id == med.id) it.copy(notificationsEnabled = nextEnabled) else it
                            }
                            persistAndSync(
                                appState.copy(medications = updatedMeds),
                                if (nextEnabled) "Notifikace v ${med.timeOfDay} zapnuta 🔔" else "Notifikace vypnuta 🔕"
                            )
                        },
                        onRefillDoses = { med ->
                            val updatedMeds = appState.medications.map {
                                if (it.id == med.id) it.copy(remainingDoses = it.remainingDoses + 15) else it
                            }
                            persistAndSync(
                                appState.copy(medications = updatedMeds),
                                "Doplněno +15 dávek pro ${med.name}."
                            )
                        },
                        onDeleteMed = { med ->
                            PlatformManager.cancelMedicationNotification(med.id)
                            persistAndSync(
                                appState.copy(medications = appState.medications.filterNot { it.id == med.id }),
                                "Položka ${med.name} byla odstraněna."
                            )
                        },
                        onAddMedClick = { showAddMedDialog = true }
                    )

                    MainTab.ALERTS -> NotificationsAndCloudTabScreen(
                        medications = appState.medications,
                        notifications = appState.notifications,
                        familyCode = familyCode,
                        syncStatusText = syncStatusText,
                        onTestSystemNotification = {
                            PlatformManager.sendImmediateTestNotification(
                                title = "🐾 Terezčiny Kočičky – Safírka & Zlatín",
                                message = "Připomínka: Nezapomeň zkontrolovat misky, vodu a dnešní vitamíny! 💕"
                            )
                            val testItem = AppNotificationItem(
                                id = "notif_${PlatformManager.currentTimeMillis()}",
                                title = "🔔 Testovací upozornění odesláno",
                                message = "Systémová notifikace byla odeslána do horní lišty telefonu.",
                                timeText = "Právě teď",
                                isRead = false
                            )
                            persistAndSync(
                                appState.copy(notifications = listOf(testItem) + appState.notifications),
                                "Odeslána testovací notifikace do telefonu!"
                            )
                        },
                        onMarkAllRead = {
                            val marked = appState.notifications.map { it.copy(isRead = true) }
                            persistAndSync(appState.copy(notifications = marked))
                        },
                        onOpenCloudSettings = { showCloudDialog = true }
                    )
                }
            }

            BottomNavigationAndFooter(
                activeTab = activeTab,
                pendingMedsCount = pendingMedsToday,
                unreadNotifCount = unreadNotifCount,
                onSelectTab = { activeTab = it }
            )
        }

        if (editingCat != null) {
            EditCatDialog(
                cat = editingCat!!,
                onDismiss = { editingCat = null },
                onSave = { updated ->
                    val updatedList = appState.cats.map { if (it.id == updated.id) updated else it }
                    editingCat = null
                    persistAndSync(
                        appState.copy(cats = updatedList),
                        "Profil kočičky ${updated.name} byl uložen!"
                    )
                }
            )
        }

        if (showAddCatDialog) {
            AddCatDialog(
                onDismiss = { showAddCatDialog = false },
                onAdd = { newCat ->
                    showAddCatDialog = false
                    persistAndSync(
                        appState.copy(cats = appState.cats + newCat),
                        "Přidána nová kočička ${newCat.name}!"
                    )
                }
            )
        }

        if (showAddMedDialog) {
            AddMedicationDialog(
                cats = appState.cats,
                onDismiss = { showAddMedDialog = false },
                onAdd = { newMed ->
                    showAddMedDialog = false
                    if (newMed.notificationsEnabled) {
                        PlatformManager.scheduleMedicationNotification(
                            id = newMed.id,
                            catName = newMed.catName,
                            medName = newMed.name,
                            dosage = newMed.dosage,
                            timeHhMm = newMed.timeOfDay
                        )
                    }
                    persistAndSync(
                        appState.copy(medications = listOf(newMed) + appState.medications),
                        "Přidán lék ${newMed.name} pro ${newMed.catName}!"
                    )
                }
            )
        }

        if (showAddHealthLogDialog) {
            AddHealthLogDialog(
                cats = appState.cats,
                onDismiss = { showAddHealthLogDialog = false },
                onAdd = { newLog ->
                    showAddHealthLogDialog = false
                    persistAndSync(
                        appState.copy(healthLogs = listOf(newLog) + appState.healthLogs),
                        "Zdravotní záznam uložen!"
                    )
                }
            )
        }

        if (showCloudDialog) {
            CloudSyncDialog(
                currentFamilyCode = familyCode,
                syncStatusText = syncStatusText,
                isSyncing = isSyncing,
                onDismiss = { showCloudDialog = false },
                onSaveFamilyCodeAndPull = { newCode ->
                    val cleaned = newCode.trim().lowercase().replace(" ", "_")
                        .ifBlank { "terezka" }
                    familyCode = cleaned
                    PlatformManager.saveFamilyCode(cleaned)
                    showCloudDialog = false
                    pullFromServer(manual = true)
                },
                onForceUpload = {
                    showCloudDialog = false
                    persistAndSync(appState, "Data byla ručně odeslána na server ($familyCode)!")
                }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    syncStatusText: String,
    pendingMedsToday: Int,
    onCloudClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Surface(
        color = BabyPinkCard,
        elevation = 3.dp,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BabyPinkSoft)
                        .border(1.5.dp, BabyPinkAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🐱", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terezčiny Kočičky",
                        color = CocoaBrown,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Safírka (Ragdollka) & Zlatín (Mainská mývalí)",
                        color = CocoaMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgBeigeWarm)
                        .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
                        .clickable { onRefreshClick() }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text("🔄", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgBeigeLight)
                        .border(1.dp, SoftBorder, RoundedCornerShape(10.dp))
                        .clickable { onCloudClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = syncStatusText,
                        color = CocoaBrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                val pillText = if (pendingMedsToday == 0) {
                    "✨ Všechny dnešní léky podány"
                } else {
                    "💊 Dnes zbývá podat: $pendingMedsToday"
                }
                val pillColor = if (pendingMedsToday == 0) Color(0xFFE6F6EC) else Color(0xFFFFE4EC)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = pillText,
                        color = CocoaBrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CatQuickSelectorRow(
    cats: List<CatProfile>,
    selectedCatId: String?,
    onSelectCat: (String?) -> Unit,
    onAddCatClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            val isAll = selectedCatId == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isAll) BabyPinkAccent else BgBeigeLight)
                    .border(1.dp, if (isAll) BabyPinkDeep else SoftBorder, RoundedCornerShape(18.dp))
                    .clickable { onSelectCat(null) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "🐾 Všechny (${cats.size})",
                    color = if (isAll) Color.White else CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(cats) { cat ->
            val isSelected = selectedCatId == cat.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) BabyPinkAccent else BgBeigeLight)
                    .border(1.dp, if (isSelected) BabyPinkDeep else SoftBorder, RoundedCornerShape(18.dp))
                    .clickable { onSelectCat(if (isSelected) null else cat.id) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "${if (cat.breedType == "RAGDOLL") "💎" else if (cat.breedType == "MAINE_COON") "🦁" else "🐱"} ${cat.name}",
                    color = if (isSelected) Color.White else CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgBeigeWarm)
                    .border(1.dp, BabyPinkAccent, RoundedCornerShape(18.dp))
                    .clickable { onAddCatClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "+ Další kočička",
                    color = BabyPinkDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CatsTabScreen(
    cats: List<CatProfile>,
    medications: List<MedicationItem>,
    todayStr: String,
    onPickRealPhoto: (CatProfile) -> Unit,
    onResetToBreedIllustration: (CatProfile) -> Unit,
    onEditCat: (CatProfile) -> Unit,
    onAddCat: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(cats) { cat ->
            val catMeds = medications.filter { it.catId == cat.id }
            val pendingForCat = catMeds.count { it.lastGivenDate != todayStr }

            Card(
                backgroundColor = BgBeigeLight,
                shape = RoundedCornerShape(22.dp),
                elevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, SoftBorder, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatAvatar(cat = cat, size = 90.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cat.name,
                                    color = CocoaBrown,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BabyPinkSoft)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = cat.breed,
                                        color = CocoaBrown,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Věk: ${cat.ageText}  •  Váha: ${cat.weightKg} kg",
                                color = CocoaMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kontrola: ${cat.lastCheckedDate}",
                                color = CocoaMuted,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onPickRealPhoto(cat) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = BabyPinkAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("📷 Nahrát fotku", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (cat.photoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onResetToBreedIllustration(cat) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("🎨 Ilustrace", fontSize = 11.sp, color = CocoaBrown)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusMiniBadge(
                            title = "Nálada",
                            value = cat.mood,
                            modifier = Modifier.weight(1f)
                        )
                        StatusMiniBadge(
                            title = "Chuť k jídlu",
                            value = cat.appetite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusMiniBadge(
                            title = "Pitný režim",
                            value = cat.waterIntake,
                            modifier = Modifier.weight(1f)
                        )
                        StatusMiniBadge(
                            title = "Kožíšek",
                            value = cat.furStatus,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spokojenost a vitalita",
                            color = CocoaBrown,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${cat.energyLevel} % 💕",
                            color = BabyPinkDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (cat.energyLevel.coerceIn(0, 100)) / 100f,
                        color = BabyPinkAccent,
                        backgroundColor = BabyPinkSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgBeigeWarm)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "📝 ${cat.notes}",
                            color = CocoaBrown,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (pendingForCat == 0) "✅ Dnešní léky podány" else "💊 Dnes podat: $pendingForCat lék(y)",
                            color = if (pendingForCat == 0) MintSuccess else BabyPinkDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { onEditCat(cat) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BabyPinkSoft,
                                contentColor = CocoaBrown
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("✏️ Upravit stav a váhu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onAddCat,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = "🐾 Přidat další kočičku do rodiny",
                    color = BabyPinkDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun StatusMiniBadge(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BabyPinkCard)
            .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            color = CocoaMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = CocoaBrown,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusAndLogsTabScreen(
    cats: List<CatProfile>,
    logs: List<HealthLogItem>,
    onQuickUpdateCat: (CatProfile, String) -> Unit,
    onAddHealthLog: () -> Unit,
    onDeleteLog: (String) -> Unit
) {
    val moodOptions = listOf("Mazlivá 💕", "Hravá 🐾", "Ospalá 😴", "Klidná 🌸", "Zvídavá 🦋")
    val appetiteOptions = listOf("Výborná 🐟", "Velký jedlík 🥩", "Mlsná 🥣", "Menší chuť 🐾")
    val waterOptions = listOf("Pije pravidelně 💧", "Miluje fontánku ⛲", "Méně pije 🥛")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text(
                text = "💖 Rychlé přepnutí denního stavu",
                color = CocoaBrown,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        items(cats) { cat ->
            Card(
                backgroundColor = BgBeigeLight,
                shape = RoundedCornerShape(18.dp),
                elevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SoftBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatAvatar(cat = cat, size = 48.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${cat.name} (${cat.breed})",
                                color = CocoaBrown,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Klepnutím rovnou změníš dnešní náladu nebo baštění:",
                                color = CocoaMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nálada:", color = CocoaBrown, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(moodOptions) { mood ->
                            val selected = cat.mood == mood
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) BabyPinkAccent else BabyPinkCard)
                                    .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val updated = cat.copy(
                                            mood = mood,
                                            lastCheckedDate = PlatformManager.currentDateString()
                                        )
                                        onQuickUpdateCat(updated, "${cat.name} má dnes náladu: $mood")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = mood,
                                    color = if (selected) Color.White else CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Papání a pití:", color = CocoaBrown, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(appetiteOptions) { appetite ->
                            val selected = cat.appetite == appetite
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) BabyPinkAccent else BgBeigeWarm)
                                    .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val updated = cat.copy(
                                            appetite = appetite,
                                            lastCheckedDate = PlatformManager.currentDateString()
                                        )
                                        onQuickUpdateCat(updated, "${cat.name} – chuť k jídlu: $appetite")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = appetite,
                                    color = if (selected) Color.White else CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        items(waterOptions) { water ->
                            val selected = cat.waterIntake == water
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) BabyPinkAccent else BgBeigeWarm)
                                    .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val updated = cat.copy(
                                            waterIntake = water,
                                            lastCheckedDate = PlatformManager.currentDateString()
                                        )
                                        onQuickUpdateCat(updated, "${cat.name} – pitný režim: $water")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = water,
                                    color = if (selected) Color.White else CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📖 Deníček zdraví a péče (${logs.size})",
                    color = CocoaBrown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Button(
                    onClick = onAddHealthLog,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BabyPinkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("+ Nový záznam", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(logs) { log ->
            Card(
                backgroundColor = BgBeigeLight,
                shape = RoundedCornerShape(16.dp),
                elevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SoftBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BabyPinkSoft)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${log.catName} • ${log.category}",
                                    color = CocoaBrown,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = log.dateText,
                                color = CocoaMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.title,
                            color = CocoaBrown,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = log.detail,
                            color = CocoaMuted,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "🗑️",
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable { onDeleteLog(log.id) }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationsTabScreen(
    medications: List<MedicationItem>,
    todayStr: String,
    onMarkGivenToday: (MedicationItem) -> Unit,
    onToggleNotification: (MedicationItem) -> Unit,
    onRefillDoses: (MedicationItem) -> Unit,
    onDeleteMed: (MedicationItem) -> Unit,
    onAddMedClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "💊 Sledování léků a vitamínů",
                        color = CocoaBrown,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Odškrtni podání léku nebo zapni upozornění na čas",
                        color = CocoaMuted,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onAddMedClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BabyPinkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("+ Přidat lék", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(medications) { med ->
            val givenToday = med.lastGivenDate == todayStr

            Card(
                backgroundColor = if (givenToday) Color(0xFFF3FAF5) else BgBeigeLight,
                shape = RoundedCornerShape(18.dp),
                elevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = if (givenToday) MintSuccess.copy(alpha = 0.5f) else SoftBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BabyPinkSoft)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🐾 ${med.catName}",
                                color = CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgBeigeWarm)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⏰ ${med.timeOfDay} (${med.frequency})",
                                color = CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "🗑️",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { onDeleteMed(med) }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = med.name,
                        color = CocoaBrown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Dávkování: ${med.dosage}",
                        color = CocoaMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Zbývá dávek: ${med.remainingDoses}",
                                color = if (med.remainingDoses <= 5) BabyPinkDeep else CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "+ Doplnit",
                                color = BabyPinkDeep,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BabyPinkCard)
                                    .clickable { onRefillDoses(med) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        if (med.lastGivenDate.isNotBlank()) {
                            Text(
                                text = "Naposledy: ${med.lastGivenDate}",
                                color = CocoaMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onMarkGivenToday(med) },
                            enabled = !givenToday,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (givenToday) MintSuccess else BabyPinkAccent,
                                contentColor = Color.White,
                                disabledBackgroundColor = MintSuccess.copy(alpha = 0.8f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (givenToday) "✅ Dnes již podáno" else "💊 Označit: Dnes podáno",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onToggleNotification(med) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (med.notificationsEnabled) "🔔 ${med.timeOfDay}" else "🔕 Vypnuto",
                                color = if (med.notificationsEnabled) BabyPinkDeep else CocoaMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsAndCloudTabScreen(
    medications: List<MedicationItem>,
    notifications: List<AppNotificationItem>,
    familyCode: String,
    syncStatusText: String,
    onTestSystemNotification: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenCloudSettings: () -> Unit
) {
    val activeAlarms = medications.filter { it.notificationsEnabled }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                backgroundColor = BgBeigeLight,
                shape = RoundedCornerShape(18.dp),
                elevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, SoftBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🔔 Upozornění na léky a péči",
                        color = CocoaBrown,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktivní denní časy upozornění pro Safírku a Zlatína (${activeAlarms.size}):",
                        color = CocoaMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeAlarms.isEmpty()) {
                        Text(
                            text = "Zatím nejsou zapnuté žádné budíky léků. Zapni je v záložce Léky.",
                            color = CocoaMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        activeAlarms.forEach { alarm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BabyPinkCard)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏰ ${alarm.timeOfDay} • ${alarm.catName}: ${alarm.name}",
                                    color = CocoaBrown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Zapnuto",
                                    color = MintSuccess,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onTestSystemNotification,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = BabyPinkAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔔 Vyzkoušet notifikaci v telefonu hned teď",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Card(
                backgroundColor = BabyPinkCard,
                shape = RoundedCornerShape(18.dp),
                elevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BabyPinkAccent, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "☁️ Ukládání dat na server (Firebase)",
                        color = CocoaBrown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rodinný kód: $familyCode  •  Stav: $syncStatusText",
                        color = CocoaBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Všechny stavy kočiček, léky i vybrané fotky se ukládají do telefonu i na server, takže k nim máte přístup z více zařízení.",
                        color = CocoaMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenCloudSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚙️ Nastavení serveru a rodinného kódu",
                            color = CocoaBrown,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📬 Přehled upozornění (${notifications.size})",
                    color = CocoaBrown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Označit vše jako přečtené",
                    color = BabyPinkDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onMarkAllRead() }
                )
            }
        }

        items(notifications) { notif ->
            Card(
                backgroundColor = if (notif.isRead) BgBeigeLight else BabyPinkCard,
                shape = RoundedCornerShape(14.dp),
                elevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SoftBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = notif.title,
                            color = CocoaBrown,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = notif.timeText,
                            color = CocoaMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = notif.message,
                        color = CocoaMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationAndFooter(
    activeTab: MainTab,
    pendingMedsCount: Int,
    unreadNotifCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    Surface(
        color = BgBeigeLight,
        elevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SoftBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MainTab.values().forEach { tab ->
                    val selected = activeTab == tab
                    val badgeCount = when (tab) {
                        MainTab.MEDS -> pendingMedsCount
                        MainTab.ALERTS -> unreadNotifCount
                        else -> 0
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) BabyPinkSoft else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = tab.icon, fontSize = 16.sp)
                                if (badgeCount > 0) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(BabyPinkDeep)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badgeCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tab.label,
                                color = if (selected) CocoaBrown else CocoaMuted,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Text(
                text = "© 2026 Created by Pavel Mareš",
                color = CocoaMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun EditCatDialog(
    cat: CatProfile,
    onDismiss: () -> Unit,
    onSave: (CatProfile) -> Unit
) {
    var name by remember { mutableStateOf(cat.name) }
    var breed by remember { mutableStateOf(cat.breed) }
    var ageText by remember { mutableStateOf(cat.ageText) }
    var weightKg by remember { mutableStateOf(cat.weightKg) }
    var furStatus by remember { mutableStateOf(cat.furStatus) }
    var energy by remember { mutableStateOf(cat.energyLevel.toFloat()) }
    var notes by remember { mutableStateOf(cat.notes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "✏️ Upravit kočičku: ${cat.name}",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jméno kočičky") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it },
                        label = { Text("Věk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        label = { Text("Váha (kg)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = furStatus,
                    onValueChange = { furStatus = it },
                    label = { Text("Stav kožíšku") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Vitalita a spokojenost: ${energy.toInt()} %",
                    color = CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = energy,
                    onValueChange = { energy = it },
                    valueRange = 10f..100f
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Poznámka") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrušit", color = CocoaMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                cat.copy(
                                    name = name.ifBlank { cat.name },
                                    breed = breed.ifBlank { cat.breed },
                                    ageText = ageText.ifBlank { "2 roky" },
                                    weightKg = weightKg.ifBlank { "4.0" },
                                    furStatus = furStatus.ifBlank { "Hebká ✨" },
                                    energyLevel = energy.toInt(),
                                    notes = notes,
                                    lastCheckedDate = PlatformManager.currentDateString()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text("Uložit", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCatDialog(
    onDismiss: () -> Unit,
    onAdd: (CatProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedBreedType by remember { mutableStateOf("RAGDOLL") }
    var ageText by remember { mutableStateOf("1 rok") }
    var weightKg by remember { mutableStateOf("3.8") }
    var notes by remember { mutableStateOf("Milá a hravá kočička 💕") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🐾 Přidat novou kočičku",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jméno kočičky") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Plemeno:", color = CocoaBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "RAGDOLL" to "Ragdollka",
                        "MAINE_COON" to "Mainská mývalí",
                        "OTHER" to "Jiná kočička"
                    ).forEach { (typeKey, label) ->
                        val sel = selectedBreedType == typeKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .clickable { selectedBreedType = typeKey }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it },
                        label = { Text("Věk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        label = { Text("Váha (kg)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Poznámka") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrušit", color = CocoaMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val breedLabel = when (selectedBreedType) {
                                "RAGDOLL" -> "Ragdollka"
                                "MAINE_COON" -> "Mainská mývalí"
                                else -> "Kočička"
                            }
                            onAdd(
                                CatProfile(
                                    id = "cat_${PlatformManager.currentTimeMillis()}",
                                    name = name.ifBlank { "Micka" },
                                    breed = breedLabel,
                                    breedType = selectedBreedType,
                                    ageText = ageText.ifBlank { "1 rok" },
                                    weightKg = weightKg.ifBlank { "4.0" },
                                    mood = "Mazlivá 💕",
                                    appetite = "Výborná 🐟",
                                    waterIntake = "Pije pravidelně 💧",
                                    furStatus = "Hebká ✨",
                                    energyLevel = 95,
                                    notes = notes,
                                    photoBase64 = "",
                                    lastCheckedDate = PlatformManager.currentDateString()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text("Přidat", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMedicationDialog(
    cats: List<CatProfile>,
    onDismiss: () -> Unit,
    onAdd: (MedicationItem) -> Unit
) {
    var selectedCat by remember { mutableStateOf(cats.firstOrNull()) }
    var medName by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("1 tableta / dávka") }
    var timeOfDay by remember { mutableStateOf("08:00") }
    var frequency by remember { mutableStateOf("Denně") }
    var dosesText by remember { mutableStateOf("20") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💊 Přidat lék nebo vitamín",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Pro kočičku:", color = CocoaBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { cat ->
                        val sel = selectedCat?.id == cat.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .clickable { selectedCat = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.name,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = medName,
                    onValueChange = { medName = it },
                    label = { Text("Název léku / pasty / kapek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dávkování") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeOfDay,
                        onValueChange = { timeOfDay = it },
                        label = { Text("Čas (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dosesText,
                        onValueChange = { dosesText = it },
                        label = { Text("Počet dávek") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Denně", "Obden", "1x týdně").forEach { freq ->
                        val sel = frequency == freq
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BgBeigeWarm)
                                .clickable { frequency = freq }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = freq,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrušit", color = CocoaMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targetCat = selectedCat ?: cats.firstOrNull()
                            if (targetCat != null) {
                                onAdd(
                                    MedicationItem(
                                        id = "med_${PlatformManager.currentTimeMillis()}",
                                        catId = targetCat.id,
                                        catName = targetCat.name,
                                        name = medName.ifBlank { "Vitamínová pasta" },
                                        dosage = dosage.ifBlank { "1 dávka" },
                                        timeOfDay = timeOfDay.ifBlank { "08:00" },
                                        frequency = frequency,
                                        remainingDoses = dosesText.toIntOrNull() ?: 20,
                                        lastGivenDate = "",
                                        notificationsEnabled = true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text("Uložit a zapnout 🔔", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddHealthLogDialog(
    cats: List<CatProfile>,
    onDismiss: () -> Unit,
    onAdd: (HealthLogItem) -> Unit
) {
    var selectedCat by remember { mutableStateOf(cats.firstOrNull()) }
    var category by remember { mutableStateOf("Zdraví") }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📖 Nový záznam do deníčku",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { cat ->
                        val sel = selectedCat?.id == cat.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .clickable { selectedCat = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.name,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Zdraví", "Váha", "Péče", "Veterina").forEach { catg ->
                        val sel = category == catg
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BgBeigeWarm)
                                .clickable { category = catg }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = catg,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nadpis (např. Očkování, Vážení, Česání)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Podrobnosti") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrušit", color = CocoaMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targetCat = selectedCat ?: cats.firstOrNull()
                            if (targetCat != null) {
                                onAdd(
                                    HealthLogItem(
                                        id = "log_${PlatformManager.currentTimeMillis()}",
                                        catId = targetCat.id,
                                        catName = targetCat.name,
                                        dateText = PlatformManager.currentDateString(),
                                        title = title.ifBlank { "Kontrola kočičky" },
                                        detail = detail.ifBlank { "Vše v pořádku 💕" },
                                        category = category
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text("Uložit", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSyncDialog(
    currentFamilyCode: String,
    syncStatusText: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSaveFamilyCodeAndPull: (String) -> Unit,
    onForceUpload: () -> Unit
) {
    var codeInput by remember { mutableStateOf(currentFamilyCode) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "☁️ Záloha a synchronizace na server",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Stejný rodinný kód (např. terezka) zadej na všech telefonech, kde chcete sdílet Safírku a Zlatína.",
                    color = CocoaMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aktuální stav: $syncStatusText",
                    color = CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Rodinný kód složky na serveru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onForceUpload,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkSoft),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⬆️ Nahrát tato data z telefonu na server",
                        color = CocoaBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zavřít", color = CocoaMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSaveFamilyCodeAndPull(codeInput) },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text("⬇️ Uložit kód a načíst", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
