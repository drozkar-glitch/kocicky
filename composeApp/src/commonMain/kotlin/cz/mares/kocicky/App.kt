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
    FOOD("Jídlo", "🥣"),
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
    var familyPin by remember { mutableStateOf(PlatformManager.loadFamilyPin()) }
    val isCloudLogged = familyCode.isNotBlank() && familyPin.isNotBlank()

    var syncStatusText by remember {
        mutableStateOf(
            if (familyCode.isNotBlank() && familyPin.isNotBlank()) {
                "☁️ Připojování ($familyCode)..."
            } else {
                "📱 Pouze v telefonu (nepřihlášeno)"
            }
        )
    }
    var isSyncing by remember { mutableStateOf(false) }

    var activeTab by remember { mutableStateOf(MainTab.CATS) }
    var selectedCatIdFilter by remember { mutableStateOf<String?>(null) }

    // Dialogy pro přidávání a úpravu
    var editingCat by remember { mutableStateOf<CatProfile?>(null) }
    var showAddCatDialog by remember { mutableStateOf(false) }

    var showFoodDialog by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }

    var showMedDialog by remember { mutableStateOf(false) }
    var editingMed by remember { mutableStateOf<MedicationItem?>(null) }

    var showVetDialog by remember { mutableStateOf(false) }
    var editingVetVisit by remember { mutableStateOf<VetVisitItem?>(null) }

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

        if (familyCode.isBlank() || familyPin.isBlank()) {
            syncStatusText = "📱 Pouze v telefonu (nepřihlášeno)"
            return
        }

        isSyncing = true
        syncStatusText = "☁️ Ukládám ($familyCode)..."
        PlatformManager.syncToFirebase(familyCode, familyPin, json) { ok, info ->
            isSyncing = false
            syncStatusText = if (ok) {
                "☁️ Synchronizováno ($familyCode)"
            } else {
                "📱 V telefonu ($info)"
            }
        }
    }

    fun scheduleAllActiveNotifications(state: CatAppState) {
        state.medications.forEach { med ->
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
        state.foods.forEach { food ->
            if (food.notificationsEnabled) {
                PlatformManager.scheduleMedicationNotification(
                    id = food.id,
                    catName = food.catName,
                    medName = "🥣 Krmení: ${food.name}",
                    dosage = food.portion,
                    timeHhMm = food.timeOfDay
                )
            }
        }
        state.vetVisits.forEach { visit ->
            if (visit.notificationsEnabled && !visit.isCompleted) {
                PlatformManager.scheduleMedicationNotification(
                    id = visit.id,
                    catName = visit.catName,
                    medName = "🩺 Veterina (${visit.dateText}): ${visit.title}",
                    dosage = visit.notes.ifBlank { "Kontrola u veterináře" },
                    timeHhMm = visit.timeOfDay
                )
            }
        }
    }

    fun pullFromServer(
        targetCode: String = familyCode,
        targetPin: String = familyPin,
        manualLogin: Boolean = false
    ) {
        if (targetCode.isBlank() || targetPin.isBlank()) {
            syncStatusText = "📱 Pouze v telefonu (nepřihlášeno)"
            return
        }

        isSyncing = true
        syncStatusText = "☁️ Ověřuji $targetCode..."
        PlatformManager.fetchFromFirebase(targetCode, targetPin) { ok, remoteJson, info ->
            isSyncing = false
            if (ok && !remoteJson.isNullOrBlank() && remoteJson != "null") {
                val remoteState = CatDataSerializer.deserialize(remoteJson)
                if (remoteState != null) {
                    if (manualLogin || remoteState.updatedAt >= appState.updatedAt) {
                        appState = remoteState
                        PlatformManager.saveLocalData(CatDataSerializer.serialize(remoteState))
                        scheduleAllActiveNotifications(remoteState)
                        syncStatusText = "☁️ Synchronizováno ($targetCode)"
                        if (manualLogin) {
                            bannerMessage = "Data pro účet '$targetCode' byla úspěšně načtena ze serveru!"
                        }
                    } else {
                        val localJson = CatDataSerializer.serialize(appState)
                        PlatformManager.syncToFirebase(targetCode, targetPin, localJson) { upOk, _ ->
                            syncStatusText = if (upOk) "☁️ Synchronizováno ($targetCode)" else "📱 V telefonu"
                        }
                    }
                } else {
                    syncStatusText = "⚠️ Neplatná data na serveru"
                }
            } else if (ok && (remoteJson == null || remoteJson == "null")) {
                syncStatusText = "⚠️ Účet/PIN nenalezen"
                if (manualLogin) {
                    bannerMessage = "Pod účtem '$targetCode' a zadaným PINem na serveru nic není. Zkontroluj překlep, nebo zvol 'Nahrát tento telefon na server'."
                }
            } else {
                syncStatusText = "📱 Offline / Chyba ($info)"
                if (manualLogin) {
                    bannerMessage = "Server vrátil odpověď: $info (zkontroluj účet, PIN nebo pravidla Firebase)."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (familyCode.isNotBlank() && familyPin.isNotBlank()) {
            pullFromServer(familyCode, familyPin, manualLogin = false)
        }
        scheduleAllActiveNotifications(appState)
    }

    val unreadNotifCount = appState.notifications.count { !it.isRead }
    val todayStr = PlatformManager.currentDateString()
    val pendingFoodsToday = appState.foods.count { it.lastFedDate != todayStr }
    val pendingMedsToday = appState.medications.count { it.lastGivenDate != todayStr }
    val pendingVetCount = appState.vetVisits.count { !it.isCompleted }

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
                pendingFoodsToday = pendingFoodsToday,
                pendingMedsToday = pendingMedsToday
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
                        foods = appState.foods,
                        medications = appState.medications,
                        vetVisits = appState.vetVisits,
                        todayStr = todayStr,
                        familyCode = familyCode,
                        isCloudLogged = isCloudLogged,
                        syncStatusText = syncStatusText,
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
                        onAddCat = { showAddCatDialog = true },
                        onOpenVetSection = { activeTab = MainTab.STATUS },
                        onOpenCloudSettings = { showCloudDialog = true },
                        onRefreshCloud = {
                            if (isCloudLogged) {
                                pullFromServer(familyCode, familyPin, manualLogin = true)
                            } else {
                                showCloudDialog = true
                            }
                        }
                    )

                    MainTab.STATUS -> StatusAndLogsTabScreen(
                        cats = appState.cats.filter { selectedCatIdFilter == null || it.id == selectedCatIdFilter },
                        vetVisits = appState.vetVisits.filter { selectedCatIdFilter == null || it.catId == selectedCatIdFilter },
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
                        onAddVetVisitClick = {
                            editingVetVisit = null
                            showVetDialog = true
                        },
                        onEditVetVisit = { visit ->
                            editingVetVisit = visit
                            showVetDialog = true
                        },
                        onCompleteVetVisit = { visit ->
                            PlatformManager.cancelMedicationNotification(visit.id)
                            val updatedVisits = appState.vetVisits.map {
                                if (it.id == visit.id) it.copy(isCompleted = true, notificationsEnabled = false) else it
                            }
                            val newLog = HealthLogItem(
                                id = "log_${PlatformManager.currentTimeMillis()}",
                                catId = visit.catId,
                                catName = visit.catName,
                                dateText = todayStr,
                                title = "Veterina / Očkování splněno: ${visit.title} 🩺",
                                detail = "Termín: ${visit.dateText}. ${visit.notes}",
                                category = "Veterina"
                            )
                            val newNotif = AppNotificationItem(
                                id = "notif_${PlatformManager.currentTimeMillis()}",
                                title = "🩺 Hotovo: ${visit.title} (${visit.catName})",
                                message = "Návštěva lékaře / očkování byla označena jako splněná a zapsána do deníčku.",
                                timeText = todayStr,
                                isRead = false
                            )
                            persistAndSync(
                                appState.copy(
                                    vetVisits = updatedVisits,
                                    healthLogs = listOf(newLog) + appState.healthLogs,
                                    notifications = listOf(newNotif) + appState.notifications
                                ),
                                "Návštěva lékaře pro ${visit.catName} byla splněna a zapsána do deníčku!"
                            )
                        },
                        onToggleVetNotification = { visit ->
                            val nextEnabled = !visit.notificationsEnabled
                            if (nextEnabled && !visit.isCompleted) {
                                PlatformManager.scheduleMedicationNotification(
                                    id = visit.id,
                                    catName = visit.catName,
                                    medName = "🩺 Veterina (${visit.dateText}): ${visit.title}",
                                    dosage = visit.notes.ifBlank { "Kontrola u veterináře" },
                                    timeHhMm = visit.timeOfDay
                                )
                            } else {
                                PlatformManager.cancelMedicationNotification(visit.id)
                            }
                            val updatedVisits = appState.vetVisits.map {
                                if (it.id == visit.id) it.copy(notificationsEnabled = nextEnabled) else it
                            }
                            persistAndSync(
                                appState.copy(vetVisits = updatedVisits),
                                if (nextEnabled) "Upozornění na veterinu zapnuto 🔔" else "Upozornění vypnuto 🔕"
                            )
                        },
                        onDeleteVetVisit = { visit ->
                            PlatformManager.cancelMedicationNotification(visit.id)
                            persistAndSync(
                                appState.copy(vetVisits = appState.vetVisits.filterNot { it.id == visit.id }),
                                "Termín u lékaře byl odstraněn."
                            )
                        },
                        onAddHealthLog = { showAddHealthLogDialog = true },
                        onDeleteLog = { logId ->
                            persistAndSync(
                                appState.copy(healthLogs = appState.healthLogs.filterNot { it.id == logId })
                            )
                        }
                    )

                    MainTab.FOOD -> FoodTabScreen(
                        foods = appState.foods.filter { selectedCatIdFilter == null || it.catId == selectedCatIdFilter },
                        todayStr = todayStr,
                        onMarkFedToday = { food ->
                            val newRemaining = (food.remainingPortions - 1).coerceAtLeast(0)
                            val updatedFoods = appState.foods.map {
                                if (it.id == food.id) {
                                    it.copy(lastFedDate = todayStr, remainingPortions = newRemaining)
                                } else it
                            }
                            val newLog = HealthLogItem(
                                id = "log_${PlatformManager.currentTimeMillis()}",
                                catId = food.catId,
                                catName = food.catName,
                                dateText = todayStr,
                                title = "Nakrmeno: ${food.name} 🥣",
                                detail = "Porce: ${food.portion} (zbývá $newRemaining porcí).",
                                category = "Jídlo"
                            )
                            val newNotif = AppNotificationItem(
                                id = "notif_${PlatformManager.currentTimeMillis()}",
                                title = "🥣 Nakrmeno: ${food.name} (${food.catName})",
                                message = "Porce (${food.portion}) byla dána do mističky.",
                                timeText = food.timeOfDay,
                                isRead = false
                            )
                            persistAndSync(
                                appState.copy(
                                    foods = updatedFoods,
                                    healthLogs = listOf(newLog) + appState.healthLogs,
                                    notifications = listOf(newNotif) + appState.notifications
                                ),
                                "Mňam! Krmení pro ${food.catName} je na dnešek odškrtnuté."
                            )
                        },
                        onToggleNotification = { food ->
                            val nextEnabled = !food.notificationsEnabled
                            if (nextEnabled) {
                                PlatformManager.scheduleMedicationNotification(
                                    id = food.id,
                                    catName = food.catName,
                                    medName = "🥣 Krmení: ${food.name}",
                                    dosage = food.portion,
                                    timeHhMm = food.timeOfDay
                                )
                            } else {
                                PlatformManager.cancelMedicationNotification(food.id)
                            }
                            val updatedFoods = appState.foods.map {
                                if (it.id == food.id) it.copy(notificationsEnabled = nextEnabled) else it
                            }
                            persistAndSync(
                                appState.copy(foods = updatedFoods),
                                if (nextEnabled) "Upozornění na krmení v ${food.timeOfDay} zapnuto 🔔" else "Upozornění vypnuto 🔕"
                            )
                        },
                        onRefillPortions = { food ->
                            val updatedFoods = appState.foods.map {
                                if (it.id == food.id) it.copy(remainingPortions = it.remainingPortions + 12) else it
                            }
                            persistAndSync(
                                appState.copy(foods = updatedFoods),
                                "Doplněno +12 porcí pro ${food.name}."
                            )
                        },
                        onEditFood = { food ->
                            editingFood = food
                            showFoodDialog = true
                        },
                        onDeleteFood = { food ->
                            PlatformManager.cancelMedicationNotification(food.id)
                            persistAndSync(
                                appState.copy(foods = appState.foods.filterNot { it.id == food.id }),
                                "Položka jídla ${food.name} byla odstraněna."
                            )
                        },
                        onAddFoodClick = {
                            editingFood = null
                            showFoodDialog = true
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
                        onEditMed = { med ->
                            editingMed = med
                            showMedDialog = true
                        },
                        onDeleteMed = { med ->
                            PlatformManager.cancelMedicationNotification(med.id)
                            persistAndSync(
                                appState.copy(medications = appState.medications.filterNot { it.id == med.id }),
                                "Položka ${med.name} byla odstraněna."
                            )
                        },
                        onAddMedClick = {
                            editingMed = null
                            showMedDialog = true
                        }
                    )

                    MainTab.ALERTS -> NotificationsTabScreen(
                        foods = appState.foods,
                        medications = appState.medications,
                        vetVisits = appState.vetVisits,
                        notifications = appState.notifications,
                        onTestSystemNotification = {
                            PlatformManager.sendImmediateTestNotification(
                                title = "🐾 Terezčiny Kočičky – Safírka & Zlatín",
                                message = "Připomínka: Nezapomeň zkontrolovat misky, vodu, léky a termíny očkování! 💕"
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
                        }
                    )
                }
            }

            BottomNavigationAndFooter(
                activeTab = activeTab,
                pendingVetCount = pendingVetCount,
                pendingFoodsCount = pendingFoodsToday,
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

        if (showFoodDialog) {
            AddOrEditFoodDialog(
                cats = appState.cats,
                initialFood = editingFood,
                onDismiss = {
                    showFoodDialog = false
                    editingFood = null
                },
                onSave = { savedFood ->
                    val isEdit = editingFood != null
                    showFoodDialog = false
                    editingFood = null

                    if (savedFood.notificationsEnabled) {
                        PlatformManager.scheduleMedicationNotification(
                            id = savedFood.id,
                            catName = savedFood.catName,
                            medName = "🥣 Krmení: ${savedFood.name}",
                            dosage = savedFood.portion,
                            timeHhMm = savedFood.timeOfDay
                        )
                    } else {
                        PlatformManager.cancelMedicationNotification(savedFood.id)
                    }

                    val updatedFoods = if (isEdit) {
                        appState.foods.map { if (it.id == savedFood.id) savedFood else it }
                    } else {
                        listOf(savedFood) + appState.foods
                    }

                    persistAndSync(
                        appState.copy(foods = updatedFoods),
                        if (isEdit) "Krmení ${savedFood.name} bylo upraveno!" else "Přidáno krmení ${savedFood.name} pro ${savedFood.catName}!"
                    )
                }
            )
        }

        if (showMedDialog) {
            AddOrEditMedicationDialog(
                cats = appState.cats,
                initialMed = editingMed,
                onDismiss = {
                    showMedDialog = false
                    editingMed = null
                },
                onSave = { savedMed ->
                    val isEdit = editingMed != null
                    showMedDialog = false
                    editingMed = null

                    if (savedMed.notificationsEnabled) {
                        PlatformManager.scheduleMedicationNotification(
                            id = savedMed.id,
                            catName = savedMed.catName,
                            medName = savedMed.name,
                            dosage = savedMed.dosage,
                            timeHhMm = savedMed.timeOfDay
                        )
                    } else {
                        PlatformManager.cancelMedicationNotification(savedMed.id)
                    }

                    val updatedMeds = if (isEdit) {
                        appState.medications.map { if (it.id == savedMed.id) savedMed else it }
                    } else {
                        listOf(savedMed) + appState.medications
                    }

                    persistAndSync(
                        appState.copy(medications = updatedMeds),
                        if (isEdit) "Lék ${savedMed.name} byl upraven!" else "Přidán lék ${savedMed.name} pro ${savedMed.catName}!"
                    )
                }
            )
        }

        if (showVetDialog) {
            AddOrEditVetVisitDialog(
                cats = appState.cats,
                initialVisit = editingVetVisit,
                onDismiss = {
                    showVetDialog = false
                    editingVetVisit = null
                },
                onSave = { savedVisit ->
                    val isEdit = editingVetVisit != null
                    showVetDialog = false
                    editingVetVisit = null

                    if (savedVisit.notificationsEnabled && !savedVisit.isCompleted) {
                        PlatformManager.scheduleMedicationNotification(
                            id = savedVisit.id,
                            catName = savedVisit.catName,
                            medName = "🩺 Veterina (${savedVisit.dateText}): ${savedVisit.title}",
                            dosage = savedVisit.notes.ifBlank { "Kontrola u veterináře" },
                            timeHhMm = savedVisit.timeOfDay
                        )
                    } else {
                        PlatformManager.cancelMedicationNotification(savedVisit.id)
                    }

                    val updatedVisits = if (isEdit) {
                        appState.vetVisits.map { if (it.id == savedVisit.id) savedVisit else it }
                    } else {
                        listOf(savedVisit) + appState.vetVisits
                    }

                    persistAndSync(
                        appState.copy(vetVisits = updatedVisits),
                        if (isEdit) "Termín u lékaře byl upraven!" else "Naplánována veterina pro ${savedVisit.catName}!"
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
                currentFamilyPin = familyPin,
                syncStatusText = syncStatusText,
                isSyncing = isSyncing,
                onDismiss = { showCloudDialog = false },
                onLoginAndDownload = { codeInput, pinInput ->
                    val cleanedCode = codeInput.trim().lowercase().replace(" ", "_")
                    val cleanedPin = pinInput.trim().replace(" ", "_")
                    if (cleanedCode.isBlank() || cleanedPin.isBlank()) {
                        bannerMessage = "Zadej prosím Rodinný účet i Rodinné heslo / PIN."
                    } else {
                        familyCode = cleanedCode
                        familyPin = cleanedPin
                        PlatformManager.saveFamilyCode(cleanedCode)
                        PlatformManager.saveFamilyPin(cleanedPin)
                        showCloudDialog = false
                        pullFromServer(cleanedCode, cleanedPin, manualLogin = true)
                    }
                },
                onSaveAndUpload = { codeInput, pinInput ->
                    val cleanedCode = codeInput.trim().lowercase().replace(" ", "_")
                    val cleanedPin = pinInput.trim().replace(" ", "_")
                    if (cleanedCode.isBlank() || cleanedPin.isBlank()) {
                        bannerMessage = "Pro nahrání na server vyplň Rodinný účet i PIN."
                    } else {
                        familyCode = cleanedCode
                        familyPin = cleanedPin
                        PlatformManager.saveFamilyCode(cleanedCode)
                        PlatformManager.saveFamilyPin(cleanedPin)
                        showCloudDialog = false
                        persistAndSync(appState, "Data z telefonu byla nahrána pod účet '$cleanedCode'!")
                    }
                },
                onDisconnectCloud = {
                    familyCode = ""
                    familyPin = ""
                    PlatformManager.saveFamilyCode("")
                    PlatformManager.saveFamilyPin("")
                    syncStatusText = "📱 Pouze v telefonu (nepřihlášeno)"
                    showCloudDialog = false
                    bannerMessage = "Server byl odpojen. Data zůstávají bezpečně v telefonu."
                }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    pendingFoodsToday: Int,
    pendingMedsToday: Int
) {
    Surface(
        color = BabyPinkCard,
        elevation = 3.dp,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BabyPinkSoft)
                    .border(1.5.dp, BabyPinkAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🐱", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Terezčiny Kočičky",
                    color = CocoaBrown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Safírka & Zlatín",
                    color = CocoaMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val pillText = if (pendingFoodsToday == 0 && pendingMedsToday == 0) {
                "✨ Vše splněno"
            } else {
                "🥣 $pendingFoodsToday  •  💊 $pendingMedsToday"
            }
            val pillColor = if (pendingFoodsToday == 0 && pendingMedsToday == 0) {
                Color(0xFFE6F6EC)
            } else {
                Color(0xFFFFE4EC)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillColor)
                    .border(1.dp, SoftBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = pillText,
                    color = CocoaBrown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🐾 Všechny (${cats.size})",
                    color = if (isAll) Color.White else CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${if (cat.breedType == "RAGDOLL") "💎" else if (cat.breedType == "MAINE_COON") "🦁" else "🐱"} ${cat.name}",
                    color = if (isSelected) Color.White else CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+ Další kočička",
                    color = BabyPinkDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CatsTabScreen(
    cats: List<CatProfile>,
    foods: List<FoodItem>,
    medications: List<MedicationItem>,
    vetVisits: List<VetVisitItem>,
    todayStr: String,
    familyCode: String,
    isCloudLogged: Boolean,
    syncStatusText: String,
    onPickRealPhoto: (CatProfile) -> Unit,
    onResetToBreedIllustration: (CatProfile) -> Unit,
    onEditCat: (CatProfile) -> Unit,
    onAddCat: () -> Unit,
    onOpenVetSection: () -> Unit,
    onOpenCloudSettings: () -> Unit,
    onRefreshCloud: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        items(cats) { cat ->
            val pendingFoodsForCat = foods.count { it.catId == cat.id && it.lastFedDate != todayStr }
            val pendingMedsForCat = medications.count { it.catId == cat.id && it.lastGivenDate != todayStr }
            val nextVetForCat = vetVisits.firstOrNull { it.catId == cat.id && !it.isCompleted }

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
                        CatAvatar(cat = cat, size = 84.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    color = CocoaBrown,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BabyPinkSoft)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = cat.breed,
                                        color = CocoaBrown,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🎂 Narození: ${cat.birthDate}",
                                color = CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Věk: ${cat.ageText}  •  Váha: ${cat.weightKg} kg",
                                color = CocoaMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onPickRealPhoto(cat) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = BabyPinkAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "📷 Fotka",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                if (cat.photoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onResetToBreedIllustration(cat) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "🎨 Ilustrace",
                                            fontSize = 11.sp,
                                            color = CocoaBrown,
                                            maxLines = 1
                                        )
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

                    // Proužek s nejbližší návštěvou veterináře / očkováním
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BabyPinkCard)
                            .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
                            .clickable { onOpenVetSection() }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (nextVetForCat != null) {
                                    "🩺 Veterina: ${nextVetForCat.dateText} – ${nextVetForCat.title}"
                                } else {
                                    "🩺 Veterina a očkování: Vše splněno (klepni pro plán)"
                                },
                                color = CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "➔",
                                color = BabyPinkDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                        val statusSummary = if (pendingFoodsForCat == 0 && pendingMedsForCat == 0) {
                            "✅ Jídlo i léky podány"
                        } else {
                            "🥣 Jídlo: $pendingFoodsForCat  •  💊 Léky: $pendingMedsForCat"
                        }
                        Text(
                            text = statusSummary,
                            color = if (pendingFoodsForCat == 0 && pendingMedsForCat == 0) MintSuccess else BabyPinkDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onEditCat(cat) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BabyPinkSoft,
                                contentColor = CocoaBrown
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "✏️ Upravit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onAddCat,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🐾 Přidat další kočičku do rodiny",
                    color = BabyPinkDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Karta pro synchronizaci a zálohu přímo v záložce Kočičky
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
                        text = "☁️ Synchronizace a záloha na server",
                        color = CocoaBrown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isCloudLogged) {
                            "Účet: $familyCode (chráněno PINem)  •  $syncStatusText"
                        } else {
                            "Stav: $syncStatusText"
                        },
                        color = CocoaBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Při výměně telefonu stačí zadat váš Rodinný účet a PIN a všechny kočičky, fotky, jídlo, léky i veterina se ihned obnoví.",
                        color = CocoaMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenCloudSettings,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = BabyPinkAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🔐 Účet a PIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onRefreshCloud,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "🔄 Obnovit data",
                                color = CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
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
    vetVisits: List<VetVisitItem>,
    logs: List<HealthLogItem>,
    onQuickUpdateCat: (CatProfile, String) -> Unit,
    onAddVetVisitClick: () -> Unit,
    onEditVetVisit: (VetVisitItem) -> Unit,
    onCompleteVetVisit: (VetVisitItem) -> Unit,
    onToggleVetNotification: (VetVisitItem) -> Unit,
    onDeleteVetVisit: (VetVisitItem) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        // Sekce: Plánované návštěvy lékaře a očkování
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "🩺 Plánované návštěvy lékaře a očkování",
                    color = CocoaBrown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Hlídej termíny očkování, odčervení a preventivních prohlídek",
                    color = CocoaMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAddVetVisitClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BabyPinkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "➕ Naplánovat očkování nebo veterinu",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(vetVisits) { visit ->
            Card(
                backgroundColor = if (visit.isCompleted) Color(0xFFF3FAF5) else BgBeigeLight,
                shape = RoundedCornerShape(18.dp),
                elevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = if (visit.isCompleted) MintSuccess.copy(alpha = 0.5f) else SoftBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BabyPinkSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🐾 ${visit.catName}",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgBeigeWarm)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "📅 ${visit.dateText} (${visit.timeOfDay})",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BabyPinkCard)
                                    .border(1.dp, SoftBorder, RoundedCornerShape(8.dp))
                                    .clickable { onEditVetVisit(visit) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✏️ Upravit",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgBeigeWarm)
                                    .clickable { onDeleteVetVisit(visit) }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🗑️",
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = visit.title,
                        color = CocoaBrown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (visit.notes.isNotBlank()) {
                        Text(
                            text = "Poznámka: ${visit.notes}",
                            color = CocoaMuted,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onCompleteVetVisit(visit) },
                            enabled = !visit.isCompleted,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (visit.isCompleted) MintSuccess else BabyPinkAccent,
                                contentColor = Color.White,
                                disabledBackgroundColor = MintSuccess.copy(alpha = 0.8f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (visit.isCompleted) "✅ Návštěva proběhla" else "🩺 Označit: Splněno",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (!visit.isCompleted) {
                            OutlinedButton(
                                onClick = { onToggleVetNotification(visit) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (visit.notificationsEnabled) "🔔 ${visit.timeOfDay}" else "🔕 Vypnuto",
                                    color = if (visit.notificationsEnabled) BabyPinkDeep else CocoaMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sekce: Rychlé přepnutí denního stavu
        item {
            Spacer(modifier = Modifier.height(4.dp))
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

        // Sekce: Historie a deníček zdraví
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "📖 Deníček zdraví a péče (${logs.size})",
                    color = CocoaBrown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAddHealthLog,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BabyPinkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "➕ Přidat záznam do deníčku",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
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
private fun FoodTabScreen(
    foods: List<FoodItem>,
    todayStr: String,
    onMarkFedToday: (FoodItem) -> Unit,
    onToggleNotification: (FoodItem) -> Unit,
    onRefillPortions: (FoodItem) -> Unit,
    onEditFood: (FoodItem) -> Unit,
    onDeleteFood: (FoodItem) -> Unit,
    onAddFoodClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "🥣 Jídelníček a krmení kočiček",
                    color = CocoaBrown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Sleduj kapsičky, granulky, zásoby a časy krmení",
                    color = CocoaMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddFoodClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BabyPinkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "➕ Přidat jídlo nebo kapsičku",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(foods) { food ->
            val fedToday = food.lastFedDate == todayStr

            Card(
                backgroundColor = if (fedToday) Color(0xFFF3FAF5) else BgBeigeLight,
                shape = RoundedCornerShape(18.dp),
                elevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = if (fedToday) MintSuccess.copy(alpha = 0.5f) else SoftBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BabyPinkSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🐾 ${food.catName}",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgBeigeWarm)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⏰ ${food.timeOfDay}",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BabyPinkCard)
                                    .border(1.dp, SoftBorder, RoundedCornerShape(8.dp))
                                    .clickable { onEditFood(food) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✏️ Upravit",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgBeigeWarm)
                                    .clickable { onDeleteFood(food) }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🗑️",
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = food.name,
                        color = CocoaBrown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Porce: ${food.portion}  •  ${food.frequency}",
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
                                text = "Zásoba: ${food.remainingPortions} ks",
                                color = if (food.remainingPortions <= 5) BabyPinkDeep else CocoaBrown,
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
                                    .clickable { onRefillPortions(food) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        if (food.lastFedDate.isNotBlank()) {
                            Text(
                                text = "Naposledy: ${food.lastFedDate}",
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
                            onClick = { onMarkFedToday(food) },
                            enabled = !fedToday,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (fedToday) MintSuccess else BabyPinkAccent,
                                contentColor = Color.White,
                                disabledBackgroundColor = MintSuccess.copy(alpha = 0.8f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (fedToday) "✅ Dnes nakrmeno" else "🥣 Dnes nakrmeno",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = { onToggleNotification(food) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (food.notificationsEnabled) "🔔 ${food.timeOfDay}" else "🔕 Vypnuto",
                                color = if (food.notificationsEnabled) BabyPinkDeep else CocoaMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
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
    onEditMed: (MedicationItem) -> Unit,
    onDeleteMed: (MedicationItem) -> Unit,
    onAddMedClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "💊 Sledování léků a vitamínů",
                    color = CocoaBrown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Odškrtni podání léku, uprav dávkování nebo zapni upozornění",
                    color = CocoaMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddMedClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BabyPinkAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "➕ Přidat lék nebo vitamín",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BabyPinkSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🐾 ${med.catName}",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgBeigeWarm)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⏰ ${med.timeOfDay}",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BabyPinkCard)
                                    .border(1.dp, SoftBorder, RoundedCornerShape(8.dp))
                                    .clickable { onEditMed(med) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✏️ Upravit",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgBeigeWarm)
                                    .clickable { onDeleteMed(med) }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🗑️",
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = med.name,
                        color = CocoaBrown,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Dávka: ${med.dosage}  •  ${med.frequency}",
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
                                text = "Zbývá: ${med.remainingDoses} dávek",
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (givenToday) "✅ Dnes podáno" else "💊 Dnes podáno",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = { onToggleNotification(med) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (med.notificationsEnabled) "🔔 ${med.timeOfDay}" else "🔕 Vypnuto",
                                color = if (med.notificationsEnabled) BabyPinkDeep else CocoaMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsTabScreen(
    foods: List<FoodItem>,
    medications: List<MedicationItem>,
    vetVisits: List<VetVisitItem>,
    notifications: List<AppNotificationItem>,
    onTestSystemNotification: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    val activeFoodAlarms = foods.filter { it.notificationsEnabled }
    val activeMedAlarms = medications.filter { it.notificationsEnabled }
    val activeVetAlarms = vetVisits.filter { it.notificationsEnabled && !it.isCompleted }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
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
                        text = "🔔 Upozornění na krmení, léky a veterinu",
                        color = CocoaBrown,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktivní časy upozornění (${activeFoodAlarms.size + activeMedAlarms.size + activeVetAlarms.size}):",
                        color = CocoaMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeFoodAlarms.isEmpty() && activeMedAlarms.isEmpty() && activeVetAlarms.isEmpty()) {
                        Text(
                            text = "Zatím nejsou zapnuté žádné budíky.",
                            color = CocoaMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        activeVetAlarms.forEach { alarm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BabyPinkSoft)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🩺 ${alarm.dateText} (${alarm.timeOfDay}) • ${alarm.catName}: ${alarm.title}",
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

                        activeFoodAlarms.forEach { alarm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgBeigeWarm)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🥣 ${alarm.timeOfDay} • ${alarm.catName}: ${alarm.name}",
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

                        activeMedAlarms.forEach { alarm ->
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
                                    text = "💊 ${alarm.timeOfDay} • ${alarm.catName}: ${alarm.name}",
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔔 Vyzkoušet notifikaci v telefonu",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
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
    pendingVetCount: Int,
    pendingFoodsCount: Int,
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
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MainTab.values().forEach { tab ->
                    val selected = activeTab == tab
                    val badgeCount = when (tab) {
                        MainTab.STATUS -> pendingVetCount
                        MainTab.FOOD -> pendingFoodsCount
                        MainTab.MEDS -> pendingMedsCount
                        MainTab.ALERTS -> unreadNotifCount
                        else -> 0
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) BabyPinkSoft else Color.Transparent)
                            .clickable { onSelectTab(tab) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = tab.icon, fontSize = 15.sp)
                                if (badgeCount > 0) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(BabyPinkDeep)
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badgeCount.toString(),
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tab.label,
                                color = if (selected) CocoaBrown else CocoaMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
    var birthDate by remember { mutableStateOf(cat.birthDate) }
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
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jméno kočičky") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Datum narození (např. 14. 5. 2024)") },
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
                                    birthDate = birthDate.ifBlank { cat.birthDate },
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
    var birthDate by remember { mutableStateOf("1. 5. 2025") }
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
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jméno kočičky") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Datum narození (např. 1. 5. 2025)") },
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
                                    lastCheckedDate = PlatformManager.currentDateString(),
                                    birthDate = birthDate.ifBlank { "1. 5. 2025" }
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
private fun AddOrEditVetVisitDialog(
    cats: List<CatProfile>,
    initialVisit: VetVisitItem?,
    onDismiss: () -> Unit,
    onSave: (VetVisitItem) -> Unit
) {
    val isEdit = initialVisit != null
    var selectedCat by remember {
        mutableStateOf(
            cats.find { it.id == initialVisit?.catId } ?: cats.firstOrNull()
        )
    }
    var title by remember { mutableStateOf(initialVisit?.title ?: "") }
    var dateText by remember { mutableStateOf(initialVisit?.dateText ?: "20. 11. 2026") }
    var timeOfDay by remember { mutableStateOf(initialVisit?.timeOfDay ?: "09:30") }
    var notes by remember { mutableStateOf(initialVisit?.notes ?: "Vzít očkovací průkaz") }

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
                    text = if (isEdit) "✏️ Upravit termín u lékaře" else "🩺 Naplánovat očkování / veterinu",
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
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Úkon (např. Roční očkování, Kontrola zoubků)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Datum (např. 20. 11. 2026)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeOfDay,
                        onValueChange = { timeOfDay = it },
                        label = { Text("Čas (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Poznámka (např. Vzít očkovací průkaz)") },
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
                                onSave(
                                    VetVisitItem(
                                        id = initialVisit?.id ?: "vet_${PlatformManager.currentTimeMillis()}",
                                        catId = targetCat.id,
                                        catName = targetCat.name,
                                        title = title.ifBlank { "Očkování a prohlídka" },
                                        dateText = dateText.ifBlank { PlatformManager.currentDateString() },
                                        timeOfDay = timeOfDay.ifBlank { "09:30" },
                                        notes = notes,
                                        isCompleted = initialVisit?.isCompleted ?: false,
                                        notificationsEnabled = initialVisit?.notificationsEnabled ?: true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text(
                            text = if (isEdit) "Uložit změny" else "Uložit a zapnout 🔔",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOrEditFoodDialog(
    cats: List<CatProfile>,
    initialFood: FoodItem?,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    val isEdit = initialFood != null
    var selectedCat by remember {
        mutableStateOf(
            cats.find { it.id == initialFood?.catId } ?: cats.firstOrNull()
        )
    }
    var foodName by remember { mutableStateOf(initialFood?.name ?: "") }
    var portion by remember { mutableStateOf(initialFood?.portion ?: "1 kapsička (85 g)") }
    var timeOfDay by remember { mutableStateOf(initialFood?.timeOfDay ?: "07:30") }
    var frequency by remember { mutableStateOf(initialFood?.frequency ?: "Denně") }
    var portionsText by remember { mutableStateOf((initialFood?.remainingPortions ?: 20).toString()) }

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
                    text = if (isEdit) "✏️ Upravit krmení" else "🥣 Přidat jídlo nebo kapsičku",
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
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text("Název jídla / kapsičky / granulek") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = portion,
                    onValueChange = { portion = it },
                    label = { Text("Velikost porce (např. 1 kapsička / 50 g)") },
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
                        value = portionsText,
                        onValueChange = { portionsText = it },
                        label = { Text("Zásoba kusů") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Denně", "Obden", "Pamlsek").forEach { freq ->
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
                                onSave(
                                    FoodItem(
                                        id = initialFood?.id ?: "food_${PlatformManager.currentTimeMillis()}",
                                        catId = targetCat.id,
                                        catName = targetCat.name,
                                        name = foodName.ifBlank { "Masová kapsička" },
                                        portion = portion.ifBlank { "1 porce" },
                                        timeOfDay = timeOfDay.ifBlank { "07:30" },
                                        frequency = frequency,
                                        remainingPortions = portionsText.toIntOrNull() ?: 20,
                                        lastFedDate = initialFood?.lastFedDate ?: "",
                                        notificationsEnabled = initialFood?.notificationsEnabled ?: true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text(
                            text = if (isEdit) "Uložit změny" else "Uložit a zapnout 🔔",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOrEditMedicationDialog(
    cats: List<CatProfile>,
    initialMed: MedicationItem?,
    onDismiss: () -> Unit,
    onSave: (MedicationItem) -> Unit
) {
    val isEdit = initialMed != null
    var selectedCat by remember {
        mutableStateOf(
            cats.find { it.id == initialMed?.catId } ?: cats.firstOrNull()
        )
    }
    var medName by remember { mutableStateOf(initialMed?.name ?: "") }
    var dosage by remember { mutableStateOf(initialMed?.dosage ?: "1 tableta / dávka") }
    var timeOfDay by remember { mutableStateOf(initialMed?.timeOfDay ?: "08:00") }
    var frequency by remember { mutableStateOf(initialMed?.frequency ?: "Denně") }
    var dosesText by remember { mutableStateOf((initialMed?.remainingDoses ?: 20).toString()) }

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
                    text = if (isEdit) "✏️ Upravit lék nebo vitamín" else "💊 Přidat lék nebo vitamín",
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
                    listOf("Denně", "Obden", "1x týdně", "Podle potřeby").forEach { freq ->
                        val sel = frequency == freq
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BgBeigeWarm)
                                .clickable { frequency = freq }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = freq,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 10.sp,
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
                                onSave(
                                    MedicationItem(
                                        id = initialMed?.id ?: "med_${PlatformManager.currentTimeMillis()}",
                                        catId = targetCat.id,
                                        catName = targetCat.name,
                                        name = medName.ifBlank { "Vitamínová pasta" },
                                        dosage = dosage.ifBlank { "1 dávka" },
                                        timeOfDay = timeOfDay.ifBlank { "08:00" },
                                        frequency = frequency,
                                        remainingDoses = dosesText.toIntOrNull() ?: 20,
                                        lastGivenDate = initialMed?.lastGivenDate ?: "",
                                        notificationsEnabled = initialMed?.notificationsEnabled ?: true
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text(
                            text = if (isEdit) "Uložit změny" else "Uložit a zapnout 🔔",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
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
    currentFamilyPin: String,
    syncStatusText: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onLoginAndDownload: (String, String) -> Unit,
    onSaveAndUpload: (String, String) -> Unit,
    onDisconnectCloud: () -> Unit
) {
    var codeInput by remember { mutableStateOf(currentFamilyCode) }
    var pinInput by remember { mutableStateOf(currentFamilyPin) }

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
                    text = "🔐 Rodinný účet a PIN (Server)",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Zadej váš Rodinný účet (např. terezka) a vaše tajné Rodinné heslo / PIN. Při výměně telefonu stačí zadat stejné údaje a data se obnoví.",
                    color = CocoaMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Stav: $syncStatusText",
                    color = CocoaBrown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Rodinný účet (např. terezka)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("Rodinné heslo / PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onLoginAndDownload(codeInput, pinInput) },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⬇️ Přihlásit a stáhnout ze serveru",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { onSaveAndUpload(codeInput, pinInput) },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkSoft),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⬆️ Založit / Nahrát tento telefon",
                        color = CocoaBrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentFamilyCode.isNotBlank() || currentFamilyPin.isNotBlank()) {
                        TextButton(onClick = onDisconnectCloud) {
                            Text("🔌 Odpojit", color = BabyPinkDeep, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Zavřít", color = CocoaMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
