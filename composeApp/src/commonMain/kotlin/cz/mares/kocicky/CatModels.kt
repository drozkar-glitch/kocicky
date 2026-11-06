package cz.mares.kocicky

data class CatProfile(
    val id: String,
    val name: String,
    val breed: String,
    val breedType: String, // "RAGDOLL", "MAINE_COON", "OTHER"
    val ageText: String,
    val weightKg: String,
    val mood: String,
    val appetite: String,
    val waterIntake: String,
    val furStatus: String,
    val energyLevel: Int, // 0..100
    val notes: String,
    val photoBase64: String,
    val lastCheckedDate: String
)

data class MedicationItem(
    val id: String,
    val catId: String,
    val catName: String,
    val name: String,
    val dosage: String,
    val timeOfDay: String, // "08:00"
    val frequency: String, // "Denně", "Obden", "1x týdně", "Podle potřeby"
    val remainingDoses: Int,
    val lastGivenDate: String,
    val notificationsEnabled: Boolean
)

data class HealthLogItem(
    val id: String,
    val catId: String,
    val catName: String,
    val dateText: String,
    val title: String,
    val detail: String,
    val category: String // "Zdraví", "Váha", "Lék podán", "Péče", "Veterina"
)

data class AppNotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timeText: String,
    val isRead: Boolean
)

data class CatAppState(
    val updatedAt: Long,
    val cats: List<CatProfile>,
    val medications: List<MedicationItem>,
    val healthLogs: List<HealthLogItem>,
    val notifications: List<AppNotificationItem>
)

object DefaultCatData {
    fun createInitialState(): CatAppState {
        val today = PlatformManager.currentDateString()
        val safirka = CatProfile(
            id = "cat_safirka",
            name = "Safírka",
            breed = "Ragdollka",
            breedType = "RAGDOLL",
            ageText = "2 roky",
            weightKg = "4.2",
            mood = "Mazlivá 💕",
            appetite = "Výborná 🐟",
            waterIntake = "Pije pravidelně 💧",
            furStatus = "Hebká a načechraná ✨",
            energyLevel = 92,
            notes = "Modrooká princezna Safírka. Miluje chování v náručí, drbání pod bradičkou a odpočinek na měkkém polštářku.",
            photoBase64 = "",
            lastCheckedDate = today
        )

        val zlatin = CatProfile(
            id = "cat_zlatin",
            name = "Zlatín",
            breed = "Mainská mývalí",
            breedType = "MAINE_COON",
            ageText = "3 roky",
            weightKg = "6.8",
            mood = "Hravý 🐾",
            appetite = "Velký jedlík 🥩",
            waterIntake = "Miluje fontánku ⛲",
            furStatus = "Hustá hříva a štětičky 🦁",
            energyLevel = 95,
            notes = "Majestátní Mainský mývalí kocour s krásnými štětičkami na uších a huňatým ocasem. Rád pozoruje ptáčky a povídá si.",
            photoBase64 = "",
            lastCheckedDate = today
        )

        val meds = listOf(
            MedicationItem(
                id = "med_safirka_olej",
                catId = "cat_safirka",
                catName = "Safírka",
                name = "Lososový olej & Biotin (na srst)",
                dosage = "1 čajová lžička do kapsičky",
                timeOfDay = "08:00",
                frequency = "Denně",
                remainingDoses = 28,
                lastGivenDate = "",
                notificationsEnabled = true
            ),
            MedicationItem(
                id = "med_zlatin_pasta",
                catId = "cat_zlatin",
                catName = "Zlatín",
                name = "Sladová pasta (proti bezoárům)",
                dosage = "3 cm proužek z tuby",
                timeOfDay = "18:30",
                frequency = "Denně",
                remainingDoses = 20,
                lastGivenDate = "",
                notificationsEnabled = true
            ),
            MedicationItem(
                id = "med_safirka_kapky",
                catId = "cat_safirka",
                catName = "Safírka",
                name = "Jemné oční kapičky",
                dosage = "1 kapka k očičku na vyčištění",
                timeOfDay = "19:30",
                frequency = "Podle potřeby",
                remainingDoses = 15,
                lastGivenDate = "",
                notificationsEnabled = false
            ),
            MedicationItem(
                id = "med_zlatin_klouby",
                catId = "cat_zlatin",
                catName = "Zlatín",
                name = "Vitamín na klouby (pro velké kočky)",
                dosage = "1 pamlsková tableta",
                timeOfDay = "09:00",
                frequency = "Denně",
                remainingDoses = 30,
                lastGivenDate = "",
                notificationsEnabled = true
            )
        )

        val logs = listOf(
            HealthLogItem(
                id = "log_init_1",
                catId = "cat_safirka",
                catName = "Safírka",
                dateText = today,
                title = "Pravidelné česání a kontrola váhy",
                detail = "Safírka váží 4.2 kg, kožíšek má krásně hebký a safírové očička čisté.",
                category = "Péče"
            ),
            HealthLogItem(
                id = "log_init_2",
                catId = "cat_zlatin",
                catName = "Zlatín",
                dateText = today,
                title = "Kontrola hřívy a drápků",
                detail = "Zlatín váží 6.8 kg, štětičky na uších v pořádku, snědl celou misku.",
                category = "Zdraví"
            )
        )

        val notifs = listOf(
            AppNotificationItem(
                id = "notif_init_1",
                title = "🐾 Vítej v Terezčiných Kočičkách!",
                message = "Safírka (Ragdollka) a Zlatín (Mainská mývalí) mají připravené své karty i rozvrh léků.",
                timeText = "08:00",
                isRead = false
            ),
            AppNotificationItem(
                id = "notif_init_2",
                title = "💊 Připomínka léků a vitamínů",
                message = "Nezapomeň dnes zkontrolovat ranní a večerní dávku pro Safírku a Zlatína.",
                timeText = "18:30",
                isRead = false
            )
        )

        return CatAppState(
            updatedAt = PlatformManager.currentTimeMillis(),
            cats = listOf(safirka, zlatin),
            medications = meds,
            healthLogs = logs,
            notifications = notifs
        )
    }
}

object CatDataSerializer {

    private fun enc(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("|", "&#124;")
            .replace("~", "&#126;")
            .replace("^", "&#94;")
    }

    private fun dec(encoded: String): String {
        return encoded
            .replace("&#94;", "^")
            .replace("&#126;", "~")
            .replace("&#124;", "|")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    fun serialize(state: CatAppState): String {
        val catsPacked = state.cats.joinToString("^") { c ->
            listOf(
                enc(c.id),
                enc(c.name),
                enc(c.breed),
                enc(c.breedType),
                enc(c.ageText),
                enc(c.weightKg),
                enc(c.mood),
                enc(c.appetite),
                enc(c.waterIntake),
                enc(c.furStatus),
                c.energyLevel.toString(),
                enc(c.notes),
                enc(c.photoBase64),
                enc(c.lastCheckedDate)
            ).joinToString("|")
        }

        val medsPacked = state.medications.joinToString("^") { m ->
            listOf(
                enc(m.id),
                enc(m.catId),
                enc(m.catName),
                enc(m.name),
                enc(m.dosage),
                enc(m.timeOfDay),
                enc(m.frequency),
                m.remainingDoses.toString(),
                enc(m.lastGivenDate),
                if (m.notificationsEnabled) "1" else "0"
            ).joinToString("|")
        }

        val logsPacked = state.healthLogs.joinToString("^") { l ->
            listOf(
                enc(l.id),
                enc(l.catId),
                enc(l.catName),
                enc(l.dateText),
                enc(l.title),
                enc(l.detail),
                enc(l.category)
            ).joinToString("|")
        }

        val notifsPacked = state.notifications.joinToString("^") { n ->
            listOf(
                enc(n.id),
                enc(n.title),
                enc(n.message),
                enc(n.timeText),
                if (n.isRead) "1" else "0"
            ).joinToString("|")
        }

        return buildString {
            append("{")
            append("\"updatedAt\":${state.updatedAt},")
            append("\"catsData\":\"$catsPacked\",")
            append("\"medsData\":\"$medsPacked\",")
            append("\"logsData\":\"$logsPacked\",")
            append("\"notifsData\":\"$notifsPacked\"")
            append("}")
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val token = "\"$key\":\""
        val startIdx = json.indexOf(token)
        if (startIdx < 0) return null
        val contentStart = startIdx + token.length
        val sb = StringBuilder()
        var i = contentStart
        while (i < json.length) {
            val ch = json[i]
            if (ch == '\\' && i + 1 < json.length) {
                sb.append(ch)
                sb.append(json[i + 1])
                i += 2
                continue
            }
            if (ch == '"') {
                return sb.toString()
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun extractJsonLong(json: String, key: String): Long {
        val token = "\"$key\":"
        val startIdx = json.indexOf(token)
        if (startIdx < 0) return 0L
        val contentStart = startIdx + token.length
        val sb = StringBuilder()
        var i = contentStart
        while (i < json.length) {
            val ch = json[i]
            if (ch in '0'..'9') {
                sb.append(ch)
            } else if (sb.isNotEmpty()) {
                break
            }
            i++
        }
        return sb.toString().toLongOrNull() ?: 0L
    }

    fun deserialize(json: String): CatAppState? {
        if (json.isBlank() || !json.contains("\"catsData\"")) return null
        return try {
            val updatedAt = extractJsonLong(json, "updatedAt")
            val catsRaw = extractJsonString(json, "catsData") ?: ""
            val medsRaw = extractJsonString(json, "medsData") ?: ""
            val logsRaw = extractJsonString(json, "logsData") ?: ""
            val notifsRaw = extractJsonString(json, "notifsData") ?: ""

            val cats = if (catsRaw.isBlank()) emptyList() else catsRaw.split("^").mapNotNull { row ->
                val p = row.split("|")
                if (p.size < 14) null else CatProfile(
                    id = dec(p[0]),
                    name = dec(p[1]),
                    breed = dec(p[2]),
                    breedType = dec(p[3]),
                    ageText = dec(p[4]),
                    weightKg = dec(p[5]),
                    mood = dec(p[6]),
                    appetite = dec(p[7]),
                    waterIntake = dec(p[8]),
                    furStatus = dec(p[9]),
                    energyLevel = p[10].toIntOrNull() ?: 90,
                    notes = dec(p[11]),
                    photoBase64 = dec(p[12]),
                    lastCheckedDate = dec(p[13])
                )
            }

            val meds = if (medsRaw.isBlank()) emptyList() else medsRaw.split("^").mapNotNull { row ->
                val p = row.split("|")
                if (p.size < 10) null else MedicationItem(
                    id = dec(p[0]),
                    catId = dec(p[1]),
                    catName = dec(p[2]),
                    name = dec(p[3]),
                    dosage = dec(p[4]),
                    timeOfDay = dec(p[5]),
                    frequency = dec(p[6]),
                    remainingDoses = p[7].toIntOrNull() ?: 0,
                    lastGivenDate = dec(p[8]),
                    notificationsEnabled = p[9] == "1"
                )
            }

            val logs = if (logsRaw.isBlank()) emptyList() else logsRaw.split("^").mapNotNull { row ->
                val p = row.split("|")
                if (p.size < 7) null else HealthLogItem(
                    id = dec(p[0]),
                    catId = dec(p[1]),
                    catName = dec(p[2]),
                    dateText = dec(p[3]),
                    title = dec(p[4]),
                    detail = dec(p[5]),
                    category = dec(p[6])
                )
            }

            val notifs = if (notifsRaw.isBlank()) emptyList() else notifsRaw.split("^").mapNotNull { row ->
                val p = row.split("|")
                if (p.size < 5) null else AppNotificationItem(
                    id = dec(p[0]),
                    title = dec(p[1]),
                    message = dec(p[2]),
                    timeText = dec(p[3]),
                    isRead = p[4] == "1"
                )
            }

            if (cats.isEmpty()) null else CatAppState(
                updatedAt = updatedAt,
                cats = cats,
                medications = meds,
                healthLogs = logs,
                notifications = notifs
            )
        } catch (e: Exception) {
            null
        }
    }
}
