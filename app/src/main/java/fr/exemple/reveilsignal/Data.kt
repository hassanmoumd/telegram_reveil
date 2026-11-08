package fr.exemple.reveilsignal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val TELEGRAM = listOf(
    "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
    "org.thunderdog.challegram", "org.telegram.plus"
)
val JOURS_COURTS = listOf("L", "M", "M", "J", "V", "S", "D")
val JOURS_NOMS = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

fun hhmm(m: Int): String = String.format(Locale.FRANCE, "%02d:%02d", m / 60, m % 60)
fun heure(ms: Long, motif: String = "HH:mm"): String =
    SimpleDateFormat(motif, Locale.FRANCE).format(Date(ms))

private fun liste(a: JSONArray?): List<String> =
    if (a == null) emptyList() else List(a.length()) { a.getString(it) }

data class Regle(
    val id: Long = System.currentTimeMillis(),
    val nom: String = "Signaux de trading",
    val active: Boolean = true,
    val canaux: List<String> = emptyList(),
    val motsInclus: List<String> = emptyList(),
    val motsExclus: List<String> = emptyList(),
    val toutLeTemps: Boolean = false,
    val debut: Int = 22 * 60,
    val fin: Int = 8 * 60,
    val jours: Int = 127,
    val dureeMin: Int = 5,
    val snoozeMin: Int = 5,
    val pauseMin: Int = 1,
    val crescendo: Boolean = true,
    val volumeMax: Boolean = true,
    val vibrer: Boolean = true,
    val flash: Boolean = false,
    val lireMessage: Boolean = false,
    val defi: Boolean = false,
    val sonnerie: String = ""
) {
    fun jourActif(j: Int): Boolean = (jours and (1 shl j)) != 0

    fun dansPlage(c: Calendar = Calendar.getInstance()): Boolean {
        val jour = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (toutLeTemps || debut == fin) return jourActif(jour)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (debut < fin) jourActif(jour) && m >= debut && m < fin
        else if (m >= debut) jourActif(jour)
        else if (m < fin) jourActif((jour + 6) % 7)
        else false
    }

    // null = l'alarme doit sonner, sinon la raison du refus
    fun raisonRefus(titre: String, texte: String, avecHoraire: Boolean = true): String? {
        if (avecHoraire && !active) return "réveil désactivé"
        if (canaux.isNotEmpty() && canaux.none { titre.contains(it.trim(), ignoreCase = true) })
            return "ce canal n'est pas sélectionné"
        motsExclus.firstOrNull { texte.contains(it, ignoreCase = true) }
            ?.let { return "contient le mot exclu « $it »" }
        if (motsInclus.isNotEmpty() && motsInclus.none { texte.contains(it, ignoreCase = true) })
            return "aucun mot-clé trouvé"
        if (avecHoraire && !dansPlage()) return "hors de la plage horaire"
        return null
    }

    fun joursTexte(): String = when (jours) {
        127 -> "Tous les jours"
        31 -> "Lun → Ven"
        96 -> "Week-end"
        0 -> "Aucun jour"
        else -> (0..6).filter { jourActif(it) }.joinToString(" ") { JOURS_NOMS[it] }
    }

    fun horaireTexte(): String = if (toutLeTemps) "24h/24" else "${hhmm(debut)} → ${hhmm(fin)}"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("nom", nom).put("active", active)
        .put("canaux", JSONArray(canaux)).put("inclus", JSONArray(motsInclus))
        .put("exclus", JSONArray(motsExclus))
        .put("toujours", toutLeTemps).put("debut", debut).put("fin", fin).put("jours", jours)
        .put("duree", dureeMin).put("snooze", snoozeMin).put("pause", pauseMin)
        .put("crescendo", crescendo).put("volmax", volumeMax).put("vibrer", vibrer)
        .put("flash", flash).put("lire", lireMessage).put("defi", defi)
        .put("sonnerie", sonnerie)

    companion object {
        fun fromJson(o: JSONObject) = Regle(
            id = o.optLong("id", System.currentTimeMillis()),
            nom = o.optString("nom", "Réveil"),
            active = o.optBoolean("active", true),
            canaux = liste(o.optJSONArray("canaux")),
            motsInclus = liste(o.optJSONArray("inclus")),
            motsExclus = liste(o.optJSONArray("exclus")),
            toutLeTemps = o.optBoolean("toujours", false),
            debut = o.optInt("debut", 22 * 60),
            fin = o.optInt("fin", 8 * 60),
            jours = o.optInt("jours", 127),
            dureeMin = o.optInt("duree", 5),
            snoozeMin = o.optInt("snooze", 5),
            pauseMin = o.optInt("pause", 1),
            crescendo = o.optBoolean("crescendo", true),
            volumeMax = o.optBoolean("volmax", true),
            vibrer = o.optBoolean("vibrer", true),
            flash = o.optBoolean("flash", false),
            lireMessage = o.optBoolean("lire", false),
            defi = o.optBoolean("defi", false),
            sonnerie = o.optString("sonnerie", "")
        )
    }
}

data class Evenement(val t: Long, val nom: String, val titre: String, val texte: String)
data class Snooze(val quand: Long, val regle: Regle, val titre: String, val texte: String)

object Store {
    private fun p(c: Context) =
        c.applicationContext.getSharedPreferences("reveil_signal", Context.MODE_PRIVATE)

    fun actif(c: Context) = p(c).getBoolean("actif", true)
    fun setActif(c: Context, b: Boolean) = p(c).edit().putBoolean("actif", b).apply()
    fun pauseJusqua(c: Context) = p(c).getLong("pause", 0L)
    fun setPause(c: Context, ms: Long) = p(c).edit().putLong("pause", ms).apply()
    fun surveille(c: Context) = actif(c) && System.currentTimeMillis() >= pauseJusqua(c)
    fun volumeArrete(c: Context) = p(c).getBoolean("vol_stop", true)
    fun setVolumeArrete(c: Context, b: Boolean) = p(c).edit().putBoolean("vol_stop", b).apply()

    fun regles(c: Context): List<Regle> {
        val s = p(c).getString("regles", null)
        if (s == null) {
            val l = listOf(Regle())
            sauverRegles(c, l)
            return l
        }
        return try {
            val a = JSONArray(s)
            List(a.length()) { Regle.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun sauverRegles(c: Context, l: List<Regle>) {
        val a = JSONArray()
        l.forEach { a.put(it.toJson()) }
        p(c).edit().putString("regles", a.toString()).apply()
    }

    fun enregistrer(c: Context, r: Regle) {
        val l = regles(c).toMutableList()
        val i = l.indexOfFirst { it.id == r.id }
        if (i >= 0) l[i] = r else l.add(r)
        sauverRegles(c, l)
    }

    fun supprimer(c: Context, id: Long) = sauverRegles(c, regles(c).filter { it.id != id })

    fun dernier(c: Context, id: Long) = p(c).getLong("dernier_$id", 0L)
    fun setDernier(c: Context, id: Long, ms: Long) = p(c).edit().putLong("dernier_$id", ms).apply()
    fun dernierArret(c: Context) = p(c).getLong("dernier_arret", 0L)
    fun setDernierArret(c: Context, ms: Long) = p(c).edit().putLong("dernier_arret", ms).apply()

    fun nettoyerTitre(t: String): String =
        t.replace("\n", " ").replace(Regex("\\s*\$\\d+[^)]*\$\\s*$"), "").trim()

    fun canauxVus(c: Context): List<String> =
        (p(c).getString("canaux_vus", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun ajouterCanalVu(c: Context, t: String) {
        if (t.isBlank()) return
        val l = canauxVus(c).toMutableList()
        l.remove(t)
        l.add(0, t)
        p(c).edit().putString("canaux_vus", l.take(30).joinToString("\n")).apply()
    }

    fun historique(c: Context): List<Evenement> {
        val a = try { JSONArray(p(c).getString("histo", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Evenement(o.optLong("t"), o.optString("nom"), o.optString("titre"), o.optString("texte"))
        }
    }

    fun ajouterHistorique(c: Context, nom: String, titre: String, texte: String) {
        val l = historique(c).toMutableList()
        l.add(0, Evenement(System.currentTimeMillis(), nom, titre, texte))
        val a = JSONArray()
        l.take(50).forEach {
            a.put(JSONObject().put("t", it.t).put("nom", it.nom).put("titre", it.titre).put("texte", it.texte))
        }
        p(c).edit().putString("histo", a.toString()).apply()
    }

    fun effacerHistorique(c: Context) = p(c).edit().remove("histo").apply()

    @Synchronized
    fun journal(c: Context, msg: String) {
        val l = lireJournal(c).split("\n").filter { it.isNotBlank() }.toMutableList()
        l.add(0, heure(System.currentTimeMillis(), "dd/MM HH:mm:ss") + "  " + msg)
        p(c).edit().putString("journal", l.take(60).joinToString("\n")).apply()
    }

    fun lireJournal(c: Context): String = p(c).getString("journal", "") ?: ""
    fun effacerJournal(c: Context) = p(c).edit().remove("journal").apply()

    fun setSnooze(c: Context, s: Snooze) = p(c).edit()
        .putLong("sn_t", s.quand).putString("sn_r", s.regle.toJson().toString())
        .putString("sn_ti", s.titre).putString("sn_te", s.texte).apply()

    fun snooze(c: Context): Snooze? {
        val t = p(c).getLong("sn_t", 0L)
        if (t == 0L) return null
        val r = try { Regle.fromJson(JSONObject(p(c).getString("sn_r", "{}") ?: "{}")) }
                catch (e: Exception) { return null }
        return Snooze(t, r, p(c).getString("sn_ti", "") ?: "", p(c).getString("sn_te", "") ?: "")
    }

    fun effacerSnooze(c: Context) = p(c).edit().remove("sn_t").apply()
}
