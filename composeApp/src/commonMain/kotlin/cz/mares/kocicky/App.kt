package cz.mares.kocicky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// Automatická barevná paleta podle systémového režimu Den / Noc (isSystemInDarkTheme)
private val BgBeigeLight: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E171B) else Color(0xFFFDFBF7)

private val BgBeigeWarm: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2B2127) else Color(0xFFF5EBE6)

private val BabyPinkSoft: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF3D2935) else Color(0xFFFADADD)

private val BabyPinkCard: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF271E24) else Color(0xFFFCEEF2)

private val BabyPinkAccent: Color
    @Composable get() = Color(0xFFE78FB3)

private val BabyPinkDeep: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF49AC0) else Color(0xFFD46A92)

private val CocoaBrown: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF8EFEA) else Color(0xFF5C4033)

private val CocoaMuted: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFC4B2AA) else Color(0xFF8C6D5E)

private val MintSuccess: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4CAF7A) else Color(0xFF529E72)

private val SoftBorder: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4A3742) else Color(0xFFF2D0DC)

private val CompletedCardBg: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1C2D24) else Color(0xFFF3FAF5)

private enum class MainTab(val label: String, val icon: String) {
    PETS("Mazlíčci", "🐾"),
    STATUS("Stavy", "💖"),
    FOOD("Jídlo", "🥣"),
    MEDS("Léky", "💊"),
    ALERTS("Notifikace", "🔔")
}

private data class PendingDeleteAction(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

private fun formatSyncTimestamp(epochMillis: Long, dateFallback: String): String {
    if (epochMillis <= 0L) return dateFallback
    val totalMinutes = (epochMillis / 60000L) + 60L
    val minute = ((totalMinutes % 60L) + 60L) % 60L
    val hour = (((totalMinutes / 60L) % 24L) + 24L) % 24L
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')
    return "$dateFallback v $hh:$mm"
}

@Composable
fun App() {
    val isDark = isSystemInDarkTheme()

    var appState by remember {
        val savedJson = PlatformManager.loadLocalData()
        val parsed = if (savedJson != null) CatDataSerializer.deserialize(savedJson) else null
        mutableStateOf(parsed ?: DefaultCatData.createInitialState())
    }

    var familyCode by remember { mutableStateOf(PlatformManager.loadFamilyCode()) }
    var familyPin by remember { mutableStateOf(PlatformManager.loadFamilyPin()) }

    var syncStatusText by remember {
        mutableStateOf(
            if (familyCode.isNotBlank() && familyPin.isNotBlank()) {
                "☁️ Připojování ($familyCode)..."
            } else {
                "📱 Pouze v telefonu (nepřihlášeno)"
            }
        )
    }
    var lastSyncTimeText by remember {
        mutableStateOf(
            if (familyCode.isNotBlank() && familyPin.isNotBlank()) {
                formatSyncTimestamp(appState.updatedAt, PlatformManager.currentDateString())
            } else {
                "Zatím nesynchronizováno"
            }
        )
    }
    var isSyncing by remember { mutableStateOf(false) }

    var activeTab by remember { mutableStateOf(MainTab.PETS) }
    var selectedCatIdFilter by remember { mutableStateOf<String?>(null) }

    var editingCat by remember { mutableStateOf<CatProfile?>(null) }
    var deletingCat by remember { mutableStateOf<CatProfile?>(null) }
    var weighingCat by remember { mutableStateOf<CatProfile?>(null) }
    var showAddCatDialog by remember { mutableStateOf(false) }

    var showFoodDialog by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }

    var showMedDialog by remember { mutableStateOf(false) }
    var editingMed by remember { mutableStateOf<MedicationItem?>(null) }

    var showVetDialog by remember { mutableStateOf(false) }
    var editingVetVisit by remember { mutableStateOf<VetVisitItem?>(null) }

    var showAddHealthLogDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<PendingDeleteAction?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    fun persistAndSync(newState: CatAppState, toastMsg: String? = null) {
        val nowMillis = PlatformManager.currentTimeMillis()
        val nowDate = PlatformManager.currentDateString()
        val stamped = newState.copy(updatedAt = nowMillis)
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
            if (ok) {
                syncStatusText = "☁️ Synchronizováno ($familyCode)"
                lastSyncTimeText = formatSyncTimestamp(nowMillis, nowDate)
            } else {
                syncStatusText = "📱 V telefonu ($info)"
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
                    appState = remoteState
                    PlatformManager.saveLocalData(CatDataSerializer.serialize(remoteState))
                    scheduleAllActiveNotifications(remoteState)
                    syncStatusText = "☁️ Synchronizováno ($targetCode)"
                    lastSyncTimeText = formatSyncTimestamp(
                        PlatformManager.currentTimeMillis(),
                        PlatformManager.currentDateString()
                    )
                    if (manualLogin) {
                        bannerMessage = "Data pro účet '$targetCode' byla úspěšně načtena ze serveru!"
                    }
                } else {
                    syncStatusText = "⚠️ Neplatná data na serveru"
                }
            } else if (ok && (remoteJson == null || remoteJson == "null")) {
                syncStatusText = "⚠️ Účet/PIN nenalezen"
                if (manualLogin) {
                    bannerMessage = "Pod účtem '$targetCode' a zadaným PINem na serveru nic není. Zkontroluj překlep, nebo zvol 'Nahrát tento telefon'."
                }
            } else {
                syncStatusText = "📱 Offline / Chyba ($info)"
                if (manualLogin) {
                    bannerMessage = "Server vrátil odpověď: $info"
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

    val visiblePets = remember(appState.cats, appState.preferredSpeciesMode) {
        when (appState.preferredSpeciesMode) {
            "CAT" -> appState.cats.filter { it.species == "CAT" }
            "DOG" -> appState.cats.filter { it.species == "DOG" }
            else -> appState.cats
        }
    }
    val visiblePetIds = remember(visiblePets) { visiblePets.map { it.id }.toSet() }

    val activeFilterPetIds = remember(visiblePetIds, selectedCatIdFilter) {
        if (selectedCatIdFilter != null && visiblePetIds.contains(selectedCatIdFilter)) {
            setOf(selectedCatIdFilter!!)
        } else {
            visiblePetIds
        }
    }

    val unreadNotifCount = appState.notifications.count { !it.isRead }
    val todayStr = PlatformManager.currentDateString()
    val pendingFoodsToday = appState.foods.count { activeFilterPetIds.contains(it.catId) && it.lastFedDate != todayStr }
    val pendingMedsToday = appState.medications.count { activeFilterPetIds.contains(it.catId) && it.lastGivenDate != todayStr }
    val pendingVetCount = appState.vetVisits.count { activeFilterPetIds.contains(it.catId) && !it.isCompleted }

    val petsSubtitle = when {
        visiblePets.isNotEmpty() -> visiblePets.joinToString(" & ") { "${if (it.species == "DOG") "🐶" else "🐱"} ${it.name}" }
        appState.preferredSpeciesMode == "DOG" -> "Zatím žádný pejsek – přidej prvního 🐶"
        appState.preferredSpeciesMode == "CAT" -> "Zatím žádná kočička – přidej první 🐱"
        else -> "Zatím žádný mazlíček – přidej prvního 🐾"
    }

    val themeColors = if (isDark) {
        darkColors(
            primary = BabyPinkAccent,
            primaryVariant = BabyPinkDeep,
            secondary = BabyPinkSoft,
            background = Color(0xFF151013),
            surface = Color(0xFF221A1F),
            onPrimary = Color.White,
            onBackground = Color(0xFFF8EFEA),
            onSurface = Color(0xFFF8EFEA)
        )
    } else {
        lightColors(
            primary = BabyPinkAccent,
            primaryVariant = BabyPinkDeep,
            secondary = BabyPinkSoft,
            background = Color(0xFFFDFBF7),
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Color(0xFF5C4033),
            onSurface = Color(0xFF5C4033)
        )
    }

    val bgGradient = if (isDark) {
        listOf(Color(0xFF21181E), Color(0xFF161114), Color(0xFF1D161A))
    } else {
        listOf(Color(0xFFFADADD), Color(0xFFFDFBF7), Color(0xFFF5EBE6))
    }

    MaterialTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = bgGradient))
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            TopHeaderBar(
                petsSubtitle = petsSubtitle,
                pendingFoodsToday = pendingFoodsToday,
                pendingMedsToday = pendingMedsToday,
                onOpenSettings = { showSettingsDialog = true }
            )

            if (bannerMessage != null) {
                Card(
                    backgroundColor = BabyPinkCard,
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
                cats = visiblePets,
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
                    MainTab.PETS -> CatsTabScreen(
                        cats = visiblePets.filter { activeFilterPetIds.contains(it.id) },
                        foods = appState.foods,
                        medications = appState.medications,
                        vetVisits = appState.vetVisits,
                        todayStr = todayStr,
                        preferredSpeciesMode = appState.preferredSpeciesMode,
                        onEditCat = { editingCat = it },
                        onWeighCat = { weighingCat = it },
                        onAddCat = { showAddCatDialog = true },
                        onResetSpeciesFilter = {
                            selectedCatIdFilter = null
                            persistAndSync(appState.copy(preferredSpeciesMode = "ALL"), "Zobrazeni všichni mazlíčci 🐾")
                        },
                        onOpenVetSection = { activeTab = MainTab.STATUS }
                    )

                    MainTab.STATUS -> StatusAndLogsTabScreen(
                        cats = visiblePets.filter { activeFilterPetIds.contains(it.id) },
                        weightRecords = appState.weightRecords.filter { activeFilterPetIds.contains(it.catId) },
                        vetVisits = appState.vetVisits.filter { activeFilterPetIds.contains(it.catId) },
                        logs = appState.healthLogs.filter { activeFilterPetIds.contains(it.catId) },
                        onWeighCatClick = { weighingCat = it },
                        onDeleteWeightRecord = { rec ->
                            pendingDeleteAction = PendingDeleteAction(
                                title = "Smazat záznam vážení?",
                                message = "Opravdu chceš smazat vážení ${WeightFormatHelper.formatWeight(rec.weightKg)} kg ze dne ${rec.dateText}?",
                                onConfirm = {
                                    val remaining = appState.weightRecords.filterNot { it.id == rec.id }
                                    val latestForPet = remaining.filter { it.catId == rec.catId }.lastOrNull()
                                    val updatedCats = if (latestForPet != null) {
                                        appState.cats.map {
                                            if (it.id == rec.catId) it.copy(weightKg = WeightFormatHelper.formatWeight(latestForPet.weightKg)) else it
                                        }
                                    } else appState.cats
                                    persistAndSync(
                                        appState.copy(
                                            cats = updatedCats,
                                            weightRecords = remaining
                                        ),
                                        "Záznam vážení byl odstraněn."
                                    )
                                }
                            )
                        },
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
                                "Stav pro ${updatedCat.name} byl uložen!"
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
                        onToggleCompleteVetVisit = { visit ->
                            val nextCompleted = !visit.isCompleted
                            if (nextCompleted) {
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
                                    "Návštěva lékaře pro ${visit.catName} byla splněna!"
                                )
                            } else {
                                val updatedVisits = appState.vetVisits.map {
                                    if (it.id == visit.id) it.copy(isCompleted = false) else it
                                }
                                persistAndSync(
                                    appState.copy(vetVisits = updatedVisits),
                                    "Označení splněné návštěvy pro ${visit.catName} bylo vráceno zpět."
                                )
                            }
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
                                if (nextEnabled) "Upozornění na veterinu (${visit.dateText}) zapnuto 🔔" else "Upozornění vypnuto 🔕"
                            )
                        },
                        onDeleteVetVisit = { visit ->
                            pendingDeleteAction = PendingDeleteAction(
                                title = "Smazat termín u lékaře?",
                                message = "Opravdu chceš smazat termín „${visit.title}“ (${visit.dateText}) pro ${visit.catName}?",
                                onConfirm = {
                                    PlatformManager.cancelMedicationNotification(visit.id)
                                    persistAndSync(
                                        appState.copy(vetVisits = appState.vetVisits.filterNot { it.id == visit.id }),
                                        "Termín u lékaře byl odstraněn."
                                    )
                                }
                            )
                        },
                        onAddHealthLog = { showAddHealthLogDialog = true },
                        onDeleteLog = { logItem ->
                            pendingDeleteAction = PendingDeleteAction(
                                title = "Smazat záznam z deníčku?",
                                message = "Opravdu chceš smazat záznam „${logItem.title}“ pro ${logItem.catName}?",
                                onConfirm = {
                                    persistAndSync(
                                        appState.copy(healthLogs = appState.healthLogs.filterNot { it.id == logItem.id }),
                                        "Záznam z deníčku byl odstraněn."
                                    )
                                }
                            )
                        }
                    )

                    MainTab.FOOD -> FoodTabScreen(
                        foods = appState.foods.filter { activeFilterPetIds.contains(it.catId) },
                        todayStr = todayStr,
                        onToggleFedToday = { food ->
                            val alreadyFedToday = food.lastFedDate == todayStr
                            if (!alreadyFedToday) {
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
                            } else {
                                val restoredPortions = food.remainingPortions + 1
                                val updatedFoods = appState.foods.map {
                                    if (it.id == food.id) {
                                        it.copy(lastFedDate = "", remainingPortions = restoredPortions)
                                    } else it
                                }
                                persistAndSync(
                                    appState.copy(foods = updatedFoods),
                                    "Dnešní krmení (${food.name}) pro ${food.catName} bylo odznačeno a porce vrácena."
                                )
                            }
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
                            pendingDeleteAction = PendingDeleteAction(
                                title = "Smazat položku jídla?",
                                message = "Opravdu chceš smazat krmení „${food.name}“ pro ${food.catName}?",
                                onConfirm = {
                                    PlatformManager.cancelMedicationNotification(food.id)
                                    persistAndSync(
                                        appState.copy(foods = appState.foods.filterNot { it.id == food.id }),
                                        "Položka jídla ${food.name} byla odstraněna."
                                    )
                                }
                            )
                        },
                        onAddFoodClick = {
                            editingFood = null
                            showFoodDialog = true
                        }
                    )

                    MainTab.MEDS -> MedicationsTabScreen(
                        medications = appState.medications.filter { activeFilterPetIds.contains(it.catId) },
                        todayStr = todayStr,
                        onToggleGivenToday = { med ->
                            val alreadyGivenToday = med.lastGivenDate == todayStr
                            if (!alreadyGivenToday) {
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
                            } else {
                                val restoredDoses = med.remainingDoses + 1
                                val updatedMeds = appState.medications.map {
                                    if (it.id == med.id) {
                                        it.copy(lastGivenDate = "", remainingDoses = restoredDoses)
                                    } else it
                                }
                                persistAndSync(
                                    appState.copy(medications = updatedMeds),
                                    "Dnešní podání léku (${med.name}) pro ${med.catName} bylo odznačeno a dávka vrácena."
                                )
                            }
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
                            pendingDeleteAction = PendingDeleteAction(
                                title = "Smazat lék nebo vitamín?",
                                message = "Opravdu chceš smazat položku „${med.name}“ pro ${med.catName}?",
                                onConfirm = {
                                    PlatformManager.cancelMedicationNotification(med.id)
                                    persistAndSync(
                                        appState.copy(medications = appState.medications.filterNot { it.id == med.id }),
                                        "Položka ${med.name} byla odstraněna."
                                    )
                                }
                            )
                        },
                        onAddMedClick = {
                            editingMed = null
                            showMedDialog = true
                        }
                    )

                    MainTab.ALERTS -> NotificationsTabScreen(
                        foods = appState.foods.filter { activeFilterPetIds.contains(it.catId) },
                        medications = appState.medications.filter { activeFilterPetIds.contains(it.catId) },
                        vetVisits = appState.vetVisits.filter { activeFilterPetIds.contains(it.catId) },
                        notifications = appState.notifications,
                        onTestSystemNotification = {
                            PlatformManager.sendImmediateTestNotification(
                                title = "🐾 Moji mazlíčci ($petsSubtitle)",
                                message = "Připomínka: Nezapomeň zkontrolovat misky, vodu, léky a termíny očkování! 💕"
                            )
                            val testItem = AppNotificationItem(
                                id = "notif_${PlatformManager.currentTimeMillis()}",
                                title = "🔔 Testovací upozornění odesláno",
                                message = "Systémová notifikace Moji mazlíčci byla odeslána do horní lišty telefonu.",
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

        if (weighingCat != null) {
            AddWeightRecordDialog(
                cat = weighingCat!!,
                onDismiss = { weighingCat = null },
                onSaveWeight = { targetCat, weightFloat, dateStr, noteStr ->
                    weighingCat = null
                    val formattedKg = WeightFormatHelper.formatWeight(weightFloat)
                    val newRecord = WeightRecord(
                        id = "w_${PlatformManager.currentTimeMillis()}",
                        catId = targetCat.id,
                        dateText = dateStr,
                        weightKg = weightFloat,
                        note = noteStr
                    )
                    val updatedCats = appState.cats.map {
                        if (it.id == targetCat.id) {
                            it.copy(weightKg = formattedKg, lastCheckedDate = dateStr)
                        } else it
                    }
                    val newLog = HealthLogItem(
                        id = "log_${PlatformManager.currentTimeMillis()}",
                        catId = targetCat.id,
                        catName = targetCat.name,
                        dateText = dateStr,
                        title = "Vážení mazlíčka: $formattedKg kg ⚖️",
                        detail = if (noteStr.isNotBlank()) "Váha: $formattedKg kg ($noteStr)." else "Zapsána nová váha $formattedKg kg do grafu.",
                        category = "Váha"
                    )
                    persistAndSync(
                        appState.copy(
                            cats = updatedCats,
                            weightRecords = appState.weightRecords + newRecord,
                            healthLogs = listOf(newLog) + appState.healthLogs
                        ),
                        "Nová váha $formattedKg kg pro ${targetCat.name} byla uložena do grafu!"
                    )
                }
            )
        }

        if (editingCat != null) {
            val prevCat = editingCat!!
            EditCatDialog(
                cat = prevCat,
                onDismiss = { editingCat = null },
                onRequestDelete = { catToDelete ->
                    editingCat = null
                    deletingCat = catToDelete
                },
                onSave = { updated ->
                    val updatedList = appState.cats.map { if (it.id == updated.id) updated else it }
                    val updatedFoods = appState.foods.map {
                        if (it.catId == updated.id) it.copy(catName = updated.name) else it
                    }
                    val updatedMeds = appState.medications.map {
                        if (it.catId == updated.id) it.copy(catName = updated.name) else it
                    }
                    val updatedVets = appState.vetVisits.map {
                        if (it.catId == updated.id) it.copy(catName = updated.name) else it
                    }

                    val oldW = WeightFormatHelper.parseWeightKg(prevCat.weightKg)
                    val newW = WeightFormatHelper.parseWeightKg(updated.weightKg)
                    val updatedWeights = if (newW != null && (oldW == null || kotlin.math.abs(newW - oldW) >= 0.05f)) {
                        appState.weightRecords + WeightRecord(
                            id = "w_${PlatformManager.currentTimeMillis()}",
                            catId = updated.id,
                            dateText = todayStr,
                            weightKg = newW,
                            note = "Upraveno v profilu"
                        )
                    } else {
                        appState.weightRecords
                    }

                    editingCat = null
                    persistAndSync(
                        appState.copy(
                            cats = updatedList,
                            foods = updatedFoods,
                            medications = updatedMeds,
                            vetVisits = updatedVets,
                            weightRecords = updatedWeights
                        ),
                        "Profil mazlíčka ${updated.name} (${updated.breed}) byl uložen!"
                    )
                }
            )
        }

        if (deletingCat != null) {
            DeleteCatConfirmDialog(
                cat = deletingCat!!,
                onDismiss = { deletingCat = null },
                onConfirmDelete = { catToRemove ->
                    deletingCat = null
                    appState.foods.filter { it.catId == catToRemove.id }.forEach {
                        PlatformManager.cancelMedicationNotification(it.id)
                    }
                    appState.medications.filter { it.catId == catToRemove.id }.forEach {
                        PlatformManager.cancelMedicationNotification(it.id)
                    }
                    appState.vetVisits.filter { it.catId == catToRemove.id }.forEach {
                        PlatformManager.cancelMedicationNotification(it.id)
                    }
                    if (selectedCatIdFilter == catToRemove.id) {
                        selectedCatIdFilter = null
                    }
                    persistAndSync(
                        appState.copy(
                            cats = appState.cats.filterNot { it.id == catToRemove.id },
                            foods = appState.foods.filterNot { it.catId == catToRemove.id },
                            medications = appState.medications.filterNot { it.catId == catToRemove.id },
                            vetVisits = appState.vetVisits.filterNot { it.catId == catToRemove.id },
                            healthLogs = appState.healthLogs.filterNot { it.catId == catToRemove.id },
                            weightRecords = appState.weightRecords.filterNot { it.catId == catToRemove.id }
                        ),
                        "Mazlíček ${catToRemove.name} byl smazán."
                    )
                }
            )
        }

        if (pendingDeleteAction != null) {
            val action = pendingDeleteAction!!
            ConfirmDeleteItemDialog(
                title = action.title,
                message = action.message,
                onDismiss = { pendingDeleteAction = null },
                onConfirm = {
                    pendingDeleteAction = null
                    action.onConfirm()
                }
            )
        }

        if (showAddCatDialog) {
            val defaultSp = if (appState.preferredSpeciesMode == "DOG") "DOG" else "CAT"
            AddCatDialog(
                initialSpecies = defaultSp,
                onDismiss = { showAddCatDialog = false },
                onAdd = { newPet ->
                    showAddCatDialog = false
                    val initialW = WeightFormatHelper.parseWeightKg(newPet.weightKg) ?: 4.0f
                    val firstWeightRecord = WeightRecord(
                        id = "w_init_${newPet.id}",
                        catId = newPet.id,
                        dateText = todayStr,
                        weightKg = initialW,
                        note = "Výchozí váha"
                    )
                    val nextMode = if (
                        (appState.preferredSpeciesMode == "CAT" && newPet.species == "DOG") ||
                        (appState.preferredSpeciesMode == "DOG" && newPet.species == "CAT")
                    ) "ALL" else appState.preferredSpeciesMode

                    persistAndSync(
                        appState.copy(
                            cats = appState.cats + newPet,
                            weightRecords = appState.weightRecords + firstWeightRecord,
                            preferredSpeciesMode = nextMode
                        ),
                        "Přidán nový mazlíček ${newPet.name} (${newPet.breed})!"
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

        if (showSettingsDialog) {
            SettingsAndSyncDialog(
                cats = appState.cats,
                currentSpeciesMode = appState.preferredSpeciesMode,
                currentFamilyCode = familyCode,
                currentFamilyPin = familyPin,
                syncStatusText = syncStatusText,
                lastSyncTimeText = lastSyncTimeText,
                isSyncing = isSyncing,
                onDismiss = { showSettingsDialog = false },
                onChangeSpeciesMode = { newMode ->
                    selectedCatIdFilter = null
                    persistAndSync(
                        appState.copy(preferredSpeciesMode = newMode),
                        when (newMode) {
                            "CAT" -> "Filtr nastaven: Kočičky 🐱"
                            "DOG" -> "Filtr nastaven: Pejsci 🐶"
                            else -> "Filtr nastaven: Všichni mazlíčci 🐾"
                        }
                    )
                },
                onEditPetFromSettings = { pet ->
                    showSettingsDialog = false
                    editingCat = pet
                },
                onAddPetFromSettings = {
                    showSettingsDialog = false
                    showAddCatDialog = true
                },
                onLoginAndDownload = { codeInput, pinInput ->
                    val cleanedCode = codeInput.trim().lowercase().replace(" ", "_")
                    val cleanedPin = pinInput.trim().lowercase().replace(" ", "_")
                    if (cleanedCode.isBlank() || cleanedPin.isBlank()) {
                        bannerMessage = "Zadej prosím Rodinný účet i Rodinné heslo / PIN."
                    } else {
                        familyCode = cleanedCode
                        familyPin = cleanedPin
                        PlatformManager.saveFamilyCode(cleanedCode)
                        PlatformManager.saveFamilyPin(cleanedPin)
                        showSettingsDialog = false
                        pullFromServer(cleanedCode, cleanedPin, manualLogin = true)
                    }
                },
                onSaveAndUpload = { codeInput, pinInput ->
                    val cleanedCode = codeInput.trim().lowercase().replace(" ", "_")
                    val cleanedPin = pinInput.trim().lowercase().replace(" ", "_")
                    if (cleanedCode.isBlank() || cleanedPin.isBlank()) {
                        bannerMessage = "Pro nahrání na server vyplň Rodinný účet i PIN."
                    } else {
                        familyCode = cleanedCode
                        familyPin = cleanedPin
                        PlatformManager.saveFamilyCode(cleanedCode)
                        PlatformManager.saveFamilyPin(cleanedPin)
                        showSettingsDialog = false
                        persistAndSync(appState, "Data z telefonu byla nahrána pod účet '$cleanedCode'!")
                    }
                },
                onRefreshNow = {
                    if (familyCode.isNotBlank() && familyPin.isNotBlank()) {
                        showSettingsDialog = false
                        pullFromServer(familyCode, familyPin, manualLogin = true)
                    } else {
                        bannerMessage = "Nejprve vyplň a ulož Rodinný účet a PIN."
                    }
                },
                onDisconnectCloud = {
                    familyCode = ""
                    familyPin = ""
                    PlatformManager.saveFamilyCode("")
                    PlatformManager.saveFamilyPin("")
                    syncStatusText = "📱 Pouze v telefonu (nepřihlášeno)"
                    lastSyncTimeText = "Odpojeno"
                    showSettingsDialog = false
                    bannerMessage = "Server byl odpojen. Data zůstávají bezpečně v telefonu."
                }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    petsSubtitle: String,
    pendingFoodsToday: Int,
    pendingMedsToday: Int,
    onOpenSettings: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val donePillBg = if (isDark) Color(0xFF1F382B) else Color(0xFFE6F6EC)
    val pendingPillBg = if (isDark) Color(0xFF3E2430) else Color(0xFFFFE4EC)

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
                Text("🐾", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Moji mazlíčci",
                    color = CocoaBrown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = petsSubtitle,
                    color = CocoaMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val pillText = if (pendingFoodsToday == 0 && pendingMedsToday == 0) {
                "✨ Hotovo"
            } else {
                "🥣 $pendingFoodsToday • 💊 $pendingMedsToday"
            }
            val pillColor = if (pendingFoodsToday == 0 && pendingMedsToday == 0) donePillBg else pendingPillBg

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(pillColor)
                    .border(1.dp, SoftBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = pillText,
                    color = CocoaBrown,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BgBeigeLight)
                    .border(1.5.dp, BabyPinkAccent, CircleShape)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text("⚙️", fontSize = 17.sp)
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
                    text = "🐾 Všichni (${cats.size})",
                    color = if (isAll) Color.White else CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        items(cats) { cat ->
            val isSelected = selectedCatId == cat.id
            val icon = when {
                cat.species == "DOG" -> "🐶"
                cat.breedType == "RAGDOLL" -> "💎"
                cat.breedType == "MAINE_COON" -> "🦁"
                else -> "🐱"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) BabyPinkAccent else BgBeigeLight)
                    .border(1.dp, if (isSelected) BabyPinkDeep else SoftBorder, RoundedCornerShape(18.dp))
                    .clickable { onSelectCat(if (isSelected) null else cat.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$icon ${cat.name}",
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
                    text = "+ Další mazlíček",
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
    preferredSpeciesMode: String,
    onEditCat: (CatProfile) -> Unit,
    onWeighCat: (CatProfile) -> Unit,
    onAddCat: () -> Unit,
    onResetSpeciesFilter: () -> Unit,
    onOpenVetSection: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        if (cats.isEmpty()) {
            item {
                Card(
                    backgroundColor = BgBeigeLight,
                    shape = RoundedCornerShape(20.dp),
                    elevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, SoftBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (preferredSpeciesMode == "DOG") "🐶 Zatím tu není žádný pejsek"
                            else if (preferredSpeciesMode == "CAT") "🐱 Zatím tu není žádná kočička"
                            else "🐾 Zatím tu není žádný mazlíček",
                            color = CocoaBrown,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Přidej si mazlíčka tlačítkem níže, nebo v ⚙️ Nastavení přepni filtr na zobrazení všech zvířátek.",
                            color = CocoaMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (preferredSpeciesMode != "ALL") {
                            OutlinedButton(
                                onClick = onResetSpeciesFilter,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🐾 Zobrazit všechny mazlíčky",
                                    color = CocoaBrown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        items(cats) { cat ->
            val pendingFoodsForCat = foods.count { it.catId == cat.id && it.lastFedDate != todayStr }
            val pendingMedsForCat = medications.count { it.catId == cat.id && it.lastGivenDate != todayStr }
            val nextVetForCat = vetVisits.firstOrNull { it.catId == cat.id && !it.isCompleted }
            val computedAgeText = CatAgeCalculator.formatFullAgeSummary(
                birthDate = cat.birthDate,
                species = cat.species,
                breedSize = cat.breedSize,
                fallbackAgeText = cat.ageText,
                todayDate = todayStr
            )

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
                        CatAvatar(
                            cat = cat,
                            size = 80.dp,
                            modifier = Modifier.clickable { onEditCat(cat) }
                        )
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
                                        text = "${if (cat.species == "DOG") "🐶" else "🐱"} ${cat.breed}",
                                        color = CocoaBrown,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🎂 Narození: ${cat.birthDate}  •  Váha: ${cat.weightKg} kg",
                                color = CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "⏳ Věk: $computedAgeText",
                                color = BabyPinkDeep,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (cat.insuranceFrom.isNotBlank() || cat.insuranceTo.isNotBlank()) {
                                val insTitle = if (cat.insuranceName.isNotBlank()) "${cat.insuranceName}: " else ""
                                Text(
                                    text = "🛡️ Pojištění: $insTitle${cat.insuranceFrom} – ${cat.insuranceTo}",
                                    color = CocoaMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                            title = "Kožíšek / srst",
                            value = cat.furStatus,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                                    "🩺 Veterina a očkování: Vše splněno"
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

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onWeighCat(cat) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "⚖️ Zvážit",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

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
        }

        item {
            OutlinedButton(
                onClick = onAddCat,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🐾 Přidat dalšího mazlíčka do rodiny",
                    color = BabyPinkDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
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
    weightRecords: List<WeightRecord>,
    vetVisits: List<VetVisitItem>,
    logs: List<HealthLogItem>,
    onWeighCatClick: (CatProfile) -> Unit,
    onDeleteWeightRecord: (WeightRecord) -> Unit,
    onQuickUpdateCat: (CatProfile, String) -> Unit,
    onAddVetVisitClick: () -> Unit,
    onEditVetVisit: (VetVisitItem) -> Unit,
    onToggleCompleteVetVisit: (VetVisitItem) -> Unit,
    onToggleVetNotification: (VetVisitItem) -> Unit,
    onDeleteVetVisit: (VetVisitItem) -> Unit,
    onAddHealthLog: () -> Unit,
    onDeleteLog: (HealthLogItem) -> Unit
) {
    val moodOptions = listOf("Mazlivá 💕", "Hravá 🐾", "Veselá 🎾", "Ospalá 😴", "Klidná 🌸")
    val appetiteOptions = listOf("Výborná 🐟", "Velký jedlík 🥩", "Mlsná 🥣", "Menší chuť 🐾")
    val waterOptions = listOf("Pije pravidelně 💧", "Miluje fontánku ⛲", "Méně pije 🥛")

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
                    text = "📊 Graf vývoje váhy a kondice",
                    color = CocoaBrown,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Sleduj váhu v čase a zaznamenej každé nové vážení jedním klepnutím",
                    color = CocoaMuted,
                    fontSize = 11.sp
                )
            }
        }

        items(cats) { cat ->
            val petRecords = weightRecords.filter { it.catId == cat.id }
            PetWeightAndVitalityChart(
                cat = cat,
                records = petRecords,
                onAddWeightClick = { onWeighCatClick(cat) },
                onDeleteWeightRecord = { onDeleteWeightRecord(it) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
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
                backgroundColor = if (visit.isCompleted) CompletedCardBg else BgBeigeLight,
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
                            onClick = { onToggleCompleteVetVisit(visit) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (visit.isCompleted) MintSuccess else BabyPinkAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (visit.isCompleted) "✅ Návštěva proběhla (zrušit)" else "🩺 Označit: Splněno",
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
                            .clickable { onDeleteLog(log) }
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
    onToggleFedToday: (FoodItem) -> Unit,
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
                    text = "🥣 Jídelníček a krmení mazlíčků",
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
                backgroundColor = if (fedToday) CompletedCardBg else BgBeigeLight,
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
                            onClick = { onToggleFedToday(food) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (fedToday) MintSuccess else BabyPinkAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (fedToday) "✅ Dnes nakrmeno (zrušit)" else "🥣 Dnes nakrmeno",
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
    onToggleGivenToday: (MedicationItem) -> Unit,
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
                backgroundColor = if (givenToday) CompletedCardBg else BgBeigeLight,
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
                            onClick = { onToggleGivenToday(med) },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (givenToday) MintSuccess else BabyPinkAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (givenToday) "✅ Dnes podáno (zrušit)" else "💊 Dnes podáno",
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
private fun AddWeightRecordDialog(
    cat: CatProfile,
    onDismiss: () -> Unit,
    onSaveWeight: (CatProfile, Float, String, String) -> Unit
) {
    var weightText by remember { mutableStateOf(cat.weightKg) }
    var dateText by remember { mutableStateOf(PlatformManager.currentDateString()) }
    var noteText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "⚖️ Zvážit mazlíčka: ${cat.name}",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Zadej novou váhu v kilogramech. Hodnota se automaticky přidá do grafu váhy a aktualizuje profil mazlíčka.",
                    color = CocoaMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = {
                            weightText = it
                            errorText = null
                        },
                        label = { Text("Váha v kg (např. 4.3)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Datum vážení") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Poznámka (nepovinné, např. Po krmení / Na veterině)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorText!!,
                        color = Color(0xFFD32F2F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                            val parsed = WeightFormatHelper.parseWeightKg(weightText)
                            if (parsed == null) {
                                errorText = "⚠️ Zadej prosím platnou váhu v kg (např. 4.2)!"
                            } else {
                                onSaveWeight(
                                    cat,
                                    parsed,
                                    dateText.trim().ifBlank { PlatformManager.currentDateString() },
                                    noteText.trim()
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent)
                    ) {
                        Text("Uložit do grafu 📊", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteItemDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkDeep, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🗑️ $title",
                    color = CocoaBrown,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = CocoaMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrušit", color = CocoaMuted, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkDeep)
                    ) {
                        Text("🗑️ Smazat", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCatDialog(
    cat: CatProfile,
    onDismiss: () -> Unit,
    onRequestDelete: (CatProfile) -> Unit,
    onSave: (CatProfile) -> Unit
) {
    var species by remember { mutableStateOf(cat.species) }
    var name by remember { mutableStateOf(cat.name) }
    var breed by remember { mutableStateOf(cat.breed) }
    var selectedBreedType by remember { mutableStateOf(cat.breedType) }
    var breedSize by remember { mutableStateOf(cat.breedSize) }
    var photoBase64 by remember { mutableStateOf(cat.photoBase64) }
    var birthDate by remember { mutableStateOf(cat.birthDate) }
    var weightKg by remember { mutableStateOf(cat.weightKg) }
    var furStatus by remember { mutableStateOf(cat.furStatus) }
    var insuranceName by remember { mutableStateOf(cat.insuranceName) }
    var insuranceFrom by remember { mutableStateOf(cat.insuranceFrom) }
    var insuranceTo by remember { mutableStateOf(cat.insuranceTo) }
    var energy by remember { mutableStateOf(cat.energyLevel.toFloat()) }
    var notes by remember { mutableStateOf(cat.notes) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val previewCat = cat.copy(
        name = name.ifBlank { cat.name },
        breed = breed.ifBlank { cat.breed },
        breedType = selectedBreedType,
        photoBase64 = photoBase64,
        species = species,
        breedSize = breedSize
    )
    val liveAgePreview = CatAgeCalculator.formatFullAgeSummary(
        birthDate = birthDate,
        species = species,
        breedSize = breedSize,
        fallbackAgeText = cat.ageText
    )
    val presets = if (species == "DOG") PetBreedCatalog.dogPresets else PetBreedCatalog.catPresets

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✏️ Upravit: ${cat.name}",
                        color = CocoaBrown,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BabyPinkSoft)
                            .border(1.dp, BabyPinkDeep, RoundedCornerShape(10.dp))
                            .clickable { onRequestDelete(cat) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "🗑️ Smazat",
                            color = BabyPinkDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CAT" to "🐱 Kočička", "DOG" to "🐶 Pejsek").forEach { (spKey, spLabel) ->
                        val sel = species == spKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .border(1.dp, if (sel) BabyPinkDeep else SoftBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    species = spKey
                                    selectedBreedType = if (spKey == "DOG") "DOG_MEDIUM" else "RAGDOLL"
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = spLabel,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    backgroundColor = BabyPinkCard,
                    shape = RoundedCornerShape(14.dp),
                    elevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoftBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatAvatar(cat = previewCat, size = 68.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (photoBase64.isNotBlank()) "Zobrazena: Vlastní fotka 📷" else "Zobrazena: Ilustrace rasy 🎨",
                                color = CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        PlatformManager.pickPhotoFromGallery { picked ->
                                            if (!picked.isNullOrBlank()) {
                                                photoBase64 = picked
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = BabyPinkAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📷 Fotka", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { photoBase64 = "" },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎨 Ilustrace", fontSize = 11.sp, color = CocoaBrown, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jméno mazlíčka") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rasa / Plemeno (povinné * – ovlivňuje výpočet věku):",
                    color = CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { preset ->
                        val sel = breed.equals(preset.label, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .border(1.dp, SoftBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    breed = preset.label
                                    selectedBreedType = preset.breedType
                                    breedSize = preset.breedSize
                                    validationError = null
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.label,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = breed,
                    onValueChange = {
                        breed = it
                        validationError = null
                    },
                    label = { Text("Rasa / Plemeno * (povinné pole)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (species == "DOG") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Velikost psí rasy (pro přesný přepočet psích let):",
                        color = CocoaBrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "SMALL" to "Malá <10kg",
                            "MEDIUM" to "Střední",
                            "LARGE" to "Velká",
                            "GIANT" to "Obří >45kg"
                        ).forEach { (szKey, szLabel) ->
                            val sel = breedSize == szKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) BabyPinkAccent else BgBeigeWarm)
                                    .clickable {
                                        breedSize = szKey
                                        selectedBreedType = "DOG_$szKey"
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = szLabel,
                                    color = if (sel) Color.White else CocoaBrown,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = validationError!!,
                        color = Color(0xFFD32F2F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("Narození (DD. M. RRRR)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        label = { Text("Váha (kg)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⏳ Vypočtený věk: $liveAgePreview",
                    color = BabyPinkDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "🛡️ Pojištění mazlíčka (Od – Do):",
                    color = CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = insuranceName,
                    onValueChange = { insuranceName = it },
                    label = { Text("Pojišťovna / smlouva (např. PetExpert)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = insuranceFrom,
                        onValueChange = { insuranceFrom = it },
                        label = { Text("Platnost Od") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = insuranceTo,
                        onValueChange = { insuranceTo = it },
                        label = { Text("Platnost Do") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = furStatus,
                    onValueChange = { furStatus = it },
                    label = { Text("Stav srsti / péče") },
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
                            if (breed.trim().isBlank()) {
                                validationError = "⚠️ Vyplň prosím povinnou položku Rasa / Plemeno!"
                            } else {
                                val computedActual = CatAgeCalculator.formatActualAge(birthDate) ?: cat.ageText
                                val normalizedW = WeightFormatHelper.parseWeightKg(weightKg)?.let {
                                    WeightFormatHelper.formatWeight(it)
                                } ?: weightKg.ifBlank { "4.0" }

                                onSave(
                                    cat.copy(
                                        name = name.trim().ifBlank { cat.name },
                                        breed = breed.trim(),
                                        breedType = selectedBreedType,
                                        photoBase64 = photoBase64,
                                        birthDate = birthDate.ifBlank { cat.birthDate },
                                        ageText = computedActual,
                                        weightKg = normalizedW,
                                        furStatus = furStatus.ifBlank { "Hebká ✨" },
                                        insuranceName = insuranceName.trim(),
                                        insuranceFrom = insuranceFrom.trim(),
                                        insuranceTo = insuranceTo.trim(),
                                        energyLevel = energy.toInt(),
                                        notes = notes,
                                        lastCheckedDate = PlatformManager.currentDateString(),
                                        species = species,
                                        breedSize = breedSize
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
private fun DeleteCatConfirmDialog(
    cat: CatProfile,
    onDismiss: () -> Unit,
    onConfirmDelete: (CatProfile) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkDeep, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🗑️ Smazat mazlíčka ${cat.name}?",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Opravdu chceš smazat mazlíčka ${cat.name} (${cat.breed})? Spolu s ním se odstraní i jeho položky krmení, léků, vážení a veteriny.",
                    color = CocoaMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Zrušit", color = CocoaMuted, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirmDelete(cat) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkDeep)
                    ) {
                        Text("🗑️ Ano, smazat", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCatDialog(
    initialSpecies: String = "CAT",
    onDismiss: () -> Unit,
    onAdd: (CatProfile) -> Unit
) {
    var species by remember { mutableStateOf(initialSpecies) }
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var selectedBreedType by remember {
        mutableStateOf(if (initialSpecies == "DOG") "DOG_MEDIUM" else "RAGDOLL")
    }
    var breedSize by remember { mutableStateOf("MEDIUM") }
    var photoBase64 by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("1. 5. 2025") }
    var weightKg by remember { mutableStateOf(if (initialSpecies == "DOG") "12.0" else "3.8") }
    var insuranceName by remember { mutableStateOf("PetExpert") }
    var insuranceFrom by remember { mutableStateOf("1. 1. 2026") }
    var insuranceTo by remember { mutableStateOf("31. 12. 2026") }
    var notes by remember { mutableStateOf("Milý a hravý mazlíček 💕") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val previewCat = CatProfile(
        id = "preview",
        name = name.ifBlank { if (species == "DOG") "Bady" else "Micka" },
        breed = breed.ifBlank { if (species == "DOG") "Pejsek" else "Kočička" },
        breedType = selectedBreedType,
        ageText = "1 rok",
        weightKg = weightKg,
        mood = "",
        appetite = "",
        waterIntake = "",
        furStatus = "",
        energyLevel = 95,
        notes = "",
        photoBase64 = photoBase64,
        lastCheckedDate = "",
        species = species,
        breedSize = breedSize
    )
    val liveAgePreview = CatAgeCalculator.formatFullAgeSummary(
        birthDate = birthDate,
        species = species,
        breedSize = breedSize,
        fallbackAgeText = "1 rok"
    )
    val presets = if (species == "DOG") PetBreedCatalog.dogPresets else PetBreedCatalog.catPresets

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(20.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "🐾 Přidat nového mazlíčka",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CAT" to "🐱 Kočička", "DOG" to "🐶 Pejsek").forEach { (spKey, spLabel) ->
                        val sel = species == spKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .border(1.dp, if (sel) BabyPinkDeep else SoftBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    species = spKey
                                    selectedBreedType = if (spKey == "DOG") "DOG_MEDIUM" else "RAGDOLL"
                                    weightKg = if (spKey == "DOG") "12.0" else "3.8"
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = spLabel,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    backgroundColor = BabyPinkCard,
                    shape = RoundedCornerShape(14.dp),
                    elevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoftBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatAvatar(cat = previewCat, size = 64.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (photoBase64.isNotBlank()) "Zobrazena: Vlastní fotka 📷" else "Zobrazena: Ilustrace rasy 🎨",
                                color = CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        PlatformManager.pickPhotoFromGallery { picked ->
                                            if (!picked.isNullOrBlank()) {
                                                photoBase64 = picked
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = BabyPinkAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("📷 Fotka", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { photoBase64 = "" },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🎨 Ilustrace", fontSize = 11.sp, color = CocoaBrown, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Jméno mazlíčka") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Vyber rasu nebo ji napiš (povinné *):",
                    color = CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { preset ->
                        val sel = breed.equals(preset.label, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                .border(1.dp, SoftBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    breed = preset.label
                                    selectedBreedType = preset.breedType
                                    breedSize = preset.breedSize
                                    validationError = null
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.label,
                                color = if (sel) Color.White else CocoaBrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = breed,
                    onValueChange = {
                        breed = it
                        validationError = null
                    },
                    label = { Text("Rasa / Plemeno * (povinné pole)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (species == "DOG") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Velikost psí rasy (pro přesný přepočet psích let):",
                        color = CocoaBrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "SMALL" to "Malá <10kg",
                            "MEDIUM" to "Střední",
                            "LARGE" to "Velká",
                            "GIANT" to "Obří >45kg"
                        ).forEach { (szKey, szLabel) ->
                            val sel = breedSize == szKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) BabyPinkAccent else BgBeigeWarm)
                                    .clickable {
                                        breedSize = szKey
                                        selectedBreedType = "DOG_$szKey"
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = szLabel,
                                    color = if (sel) Color.White else CocoaBrown,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = validationError!!,
                        color = Color(0xFFD32F2F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("Narození (DD. M. RRRR)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        label = { Text("Váha (kg)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⏳ Vypočtený věk: $liveAgePreview",
                    color = BabyPinkDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🛡️ Pojištění mazlíčka (Od – Do):",
                    color = CocoaBrown,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = insuranceName,
                    onValueChange = { insuranceName = it },
                    label = { Text("Pojišťovna / smlouva") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = insuranceFrom,
                        onValueChange = { insuranceFrom = it },
                        label = { Text("Platnost Od") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = insuranceTo,
                        onValueChange = { insuranceTo = it },
                        label = { Text("Platnost Do") },
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
                            if (breed.trim().isBlank()) {
                                validationError = "⚠️ Vyplň prosím povinnou položku Rasa / Plemeno!"
                            } else {
                                val computedActual = CatAgeCalculator.formatActualAge(birthDate) ?: "1 rok"
                                val defaultName = if (species == "DOG") "Bady" else "Micka"
                                val normalizedW = WeightFormatHelper.parseWeightKg(weightKg)?.let {
                                    WeightFormatHelper.formatWeight(it)
                                } ?: if (species == "DOG") "12.0" else "4.0"

                                onAdd(
                                    CatProfile(
                                        id = "pet_${PlatformManager.currentTimeMillis()}",
                                        name = name.trim().ifBlank { defaultName },
                                        breed = breed.trim(),
                                        breedType = selectedBreedType,
                                        ageText = computedActual,
                                        weightKg = normalizedW,
                                        mood = if (species == "DOG") "Veselá 🎾" else "Mazlivá 💕",
                                        appetite = "Výborná 🐟",
                                        waterIntake = "Pije pravidelně 💧",
                                        furStatus = "Hebká ✨",
                                        energyLevel = 95,
                                        notes = notes,
                                        photoBase64 = photoBase64,
                                        lastCheckedDate = PlatformManager.currentDateString(),
                                        birthDate = birthDate.ifBlank { "1. 5. 2025" },
                                        insuranceName = insuranceName.trim(),
                                        insuranceFrom = insuranceFrom.trim(),
                                        insuranceTo = insuranceTo.trim(),
                                        species = species,
                                        breedSize = breedSize
                                    )
                                )
                            }
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
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEdit) "✏️ Upravit termín u lékaře" else "🩺 Naplánovat očkování / veterinu",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Pro mazlíčka:", color = CocoaBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cats) { cat ->
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
                        label = { Text("Datum (DD. M. RRRR)") },
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
    var portion by remember { mutableStateOf(initialFood?.portion ?: "1 porce / miska") }
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
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEdit) "✏️ Upravit krmení" else "🥣 Přidat jídlo nebo kapsičku",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Pro mazlíčka:", color = CocoaBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cats) { cat ->
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
                    label = { Text("Název jídla / granulí / kapsičky") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = portion,
                    onValueChange = { portion = it },
                    label = { Text("Velikost porce (např. 1 kapsička / 85 g)") },
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
                        label = { Text("Zásoba porcí") },
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
                                        name = foodName.ifBlank { "Krmení do misky" },
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
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEdit) "✏️ Upravit lék nebo vitamín" else "💊 Přidat lék nebo vitamín",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Pro mazlíčka:", color = CocoaBrown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cats) { cat ->
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
                                        name = medName.ifBlank { "Vitamín" },
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
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "📖 Nový záznam do deníčku",
                    color = CocoaBrown,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cats) { cat ->
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
                                        title = title.ifBlank { "Kontrola mazlíčka" },
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
private fun SettingsAndSyncDialog(
    cats: List<CatProfile>,
    currentSpeciesMode: String,
    currentFamilyCode: String,
    currentFamilyPin: String,
    syncStatusText: String,
    lastSyncTimeText: String,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onChangeSpeciesMode: (String) -> Unit,
    onEditPetFromSettings: (CatProfile) -> Unit,
    onAddPetFromSettings: () -> Unit,
    onLoginAndDownload: (String, String) -> Unit,
    onSaveAndUpload: (String, String) -> Unit,
    onRefreshNow: () -> Unit,
    onDisconnectCloud: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isLogged = currentFamilyCode.isNotBlank() && currentFamilyPin.isNotBlank()
    var isEditingCredentials by remember { mutableStateOf(!isLogged) }
    var showPinPlain by remember { mutableStateOf(false) }

    var codeInput by remember { mutableStateOf(currentFamilyCode) }
    var pinInput by remember { mutableStateOf(currentFamilyPin) }
    val normalizedMode = if (currentSpeciesMode == "CAT" || currentSpeciesMode == "DOG") currentSpeciesMode else "ALL"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            backgroundColor = BgBeigeLight,
            shape = RoundedCornerShape(22.dp),
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .border(2.dp, BabyPinkAccent, RoundedCornerShape(22.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Ukotvená horní hlavička okna
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BabyPinkCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚙️ Nastavení a synchronizace",
                            color = CocoaBrown,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isDark) "🌙 Tmavý režim (podle systému)" else "☀️ Světlý režim (podle systému)",
                            color = CocoaMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(BgBeigeLight)
                            .border(1.dp, SoftBorder, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = CocoaBrown,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(color = SoftBorder)

                // Posuvný střed okna
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 1. Volba zobrazených zvířátek
                    Text(
                        text = "🐾 Zobrazení mazlíčků v aplikaci:",
                        color = CocoaBrown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "🐾 Všichni",
                            "CAT" to "🐱 Kočičky",
                            "DOG" to "🐶 Pejsci"
                        ).forEach { (mKey, mLabel) ->
                            val sel = normalizedMode == mKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) BabyPinkAccent else BabyPinkCard)
                                    .border(1.dp, if (sel) BabyPinkDeep else SoftBorder, RoundedCornerShape(10.dp))
                                    .clickable { onChangeSpeciesMode(mKey) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mLabel,
                                    color = if (sel) Color.White else CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SoftBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Rychlá správa mazlíčků
                    Text(
                        text = "📋 Správa mazlíčků (rasa, fotka, pojištění):",
                        color = CocoaBrown,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    cats.forEach { pet ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BabyPinkCard)
                                .border(1.dp, SoftBorder, RoundedCornerShape(10.dp))
                                .clickable { onEditPetFromSettings(pet) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (pet.species == "DOG") "🐶" else "🐱"} ${pet.name} (${pet.breed} • ${pet.weightKg} kg)",
                                color = CocoaBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "✏️ Upravit",
                                color = BabyPinkDeep,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onAddPetFromSettings,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "+ Přidat nového mazlíčka (kočičku nebo pejska)",
                            color = BabyPinkDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SoftBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Synchronizace a záloha na server (Firebase)
                    Text(
                        text = "☁️ Synchronizace a záloha (Firebase)",
                        color = CocoaBrown,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BabyPinkCard)
                            .border(1.dp, SoftBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Stav: $syncStatusText",
                            color = CocoaBrown,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🕒 Naposledy: $lastSyncTimeText",
                            color = CocoaMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isLogged && !isEditingCredentials) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔐 Účet: $currentFamilyCode  •  PIN: ••••••",
                                    color = CocoaBrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "✏️ Změnit",
                                    color = BabyPinkDeep,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isEditingCredentials = true }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLogged && !isEditingCredentials) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRefreshNow,
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkAccent),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "🔄 Stáhnout",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { onSaveAndUpload(currentFamilyCode, currentFamilyPin) },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(backgroundColor = BabyPinkSoft),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "⬆️ Nahrát",
                                    color = CocoaBrown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Zadej váš Rodinný účet (např. terezka) a tajné Rodinné heslo / PIN:",
                            color = CocoaMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

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
                            visualTransformation = if (showPinPlain) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Text(
                                    text = if (showPinPlain) "🙈" else "👁️",
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .clickable { showPinPlain = !showPinPlain }
                                        .padding(6.dp)
                                )
                            },
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

                        if (isLogged) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    codeInput = currentFamilyCode
                                    pinInput = currentFamilyPin
                                    isEditingCredentials = false
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Zrušit úpravu účtu", color = CocoaMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Ukotvená spodní patička okna
                Divider(color = SoftBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BabyPinkCard)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLogged) {
                        TextButton(onClick = onDisconnectCloud) {
                            Text("🔌 Odpojit server", color = BabyPinkDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Zavřít", color = CocoaBrown, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
