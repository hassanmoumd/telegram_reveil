@file:OptIn(ExperimentalLayoutApi::class)

package fr.exemple.reveilsignal

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun ajouterMots(liste: List<String>, saisie: String): List<String> =
    (liste + saisie.split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct()

fun choisirHeure(ctx: Context, actuel: Int, ok: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> ok(h * 60 + m) }, actuel / 60, actuel % 60, true).show()
}

fun nomSonnerie(ctx: Context, s: String): String =
    if (s.isEmpty()) "Alarme par défaut"
    else try { RingtoneManager.getRingtone(ctx, Uri.parse(s))?.getTitle(ctx) ?: "Personnalisée" }
         catch (e: Exception) { "Personnalisée" }

fun descriptionPlage(r: Regle): String {
    val d = (r.fin - r.debut + 1440) % 1440
    val duree = if (d == 0) "24 h" else "${d / 60} h" + (if (d % 60 > 0) " ${d % 60} min" else "")
    return "Durée : $duree" + (if (r.fin < r.debut) " · passe minuit 🌙 (le jour coché = le soir où ça commence)" else "")
}

@Composable
fun CaseHeure(label: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Couleurs.Carte2)
        .clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Couleurs.Gris, fontSize = 13.sp)
        Text(hhmm(minutes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Text("Modifier", color = Couleurs.Violet, fontSize = 12.sp)
    }
}

@Composable
fun Editeur(initial: Regle, fermer: () -> Unit) {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf(initial) }
    val existe = remember { Store.regles(ctx).any { it.id == initial.id } }
    val vus = remember { Store.canauxVus(ctx) }
    var canalSaisi by remember { mutableStateOf("") }
    var motSaisi by remember { mutableStateOf("") }
    var exclusSaisi by remember { mutableStateOf("") }
    var essaiCanal by remember { mutableStateOf("") }
    var essaiMessage by remember { mutableStateOf("") }
    var resultat by remember { mutableStateOf<String?>(null) }
    var confirmer by remember { mutableStateOf(false) }

    val choixSonnerie = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val u: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            r = r.copy(sonnerie =
                if (u == null || u == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) ""
                else u.toString())
        }
    }

    Column(Modifier.fillMaxSize().background(Couleurs.Fond)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = fermer) { Text("←", fontSize = 24.sp, color = Color.White) }
            Text(if (existe) "Modifier le réveil" else "Nouveau réveil", color = Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (existe) TextButton(onClick = { confirmer = true }) { Text("Supprimer", color = Couleurs.Rose) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            Carte {
                ChampTexte(r.nom, { r = r.copy(nom = it) }, "Nom du réveil")
                Spacer(Modifier.height(8.dp))
                LigneSwitch("Réveil activé", null, r.active) { r = r.copy(active = it) }
            }

            Titre("📢  CANAUX TELEGRAM")
            Carte {
                Text(
                    if (r.canaux.isEmpty()) "Aucun canal choisi : TOUS les messages Telegram feront sonner l'alarme."
                    else "Seuls les messages de ces canaux feront sonner l'alarme :",
                    color = if (r.canaux.isEmpty()) Couleurs.Orange else Couleurs.Gris, fontSize = 14.sp)
                if (r.canaux.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.canaux.forEach { c -> Pastille("$c  ✕", Couleurs.Violet) { r = r.copy(canaux = r.canaux - c) } }
                    }
                }
                val proposes = vus.filter { it !in r.canaux }
                Spacer(Modifier.height(14.dp))
                if (proposes.isNotEmpty()) {
                    Text("Détectés sur votre téléphone · touchez pour ajouter", color = Couleurs.Gris, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        proposes.take(20).forEach { c -> Pastille("＋ $c", Couleurs.Cyan) { r = r.copy(canaux = r.canaux + c) } }
                    }
                } else {
                    Text("Aucun canal détecté pour l'instant. Attendez un message du canal, ou tapez son nom :",
                        color = Couleurs.Gris, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChampTexte(canalSaisi, { canalSaisi = it }, "Nom du canal", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    BoutonSecondaire("Ajouter") {
                        if (canalSaisi.isNotBlank()) {
                            r = r.copy(canaux = (r.canaux + canalSaisi.trim()).distinct())
                            canalSaisi = ""
                        }
                    }
                }
            }

            Titre("🔑  MOTS-CLÉS")
            Carte {
                Text("Le message doit contenir au moins un de ces mots (vide = n'importe quel message)",
                    color = Couleurs.Gris, fontSize = 13.sp)
                if (r.motsInclus.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.motsInclus.forEach { m -> Pastille("$m  ✕", Couleurs.Rose) { r = r.copy(motsInclus = r.motsInclus - m) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChampTexte(motSaisi, { motSaisi = it }, "ex : buy, sell, long", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    BoutonSecondaire("Ajouter", couleur = Couleurs.Rose) {
                        r = r.copy(motsInclus = ajouterMots(r.motsInclus, motSaisi)); motSaisi = ""
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Couleurs.Carte2)
                Spacer(Modifier.height(12.dp))
                Text("Ne PAS sonner si le message contient :", color = Couleurs.Gris, fontSize = 13.sp)
                if (r.motsExclus.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.motsExclus.forEach { m -> Pastille("$m  ✕", Couleurs.Gris) { r = r.copy(motsExclus = r.motsExclus - m) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChampTexte(exclusSaisi, { exclusSaisi = it }, "ex : test, pub, résultat", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    BoutonSecondaire("Ajouter", couleur = Couleurs.Gris) {
                        r = r.copy(motsExclus = ajouterMots(r.motsExclus, exclusSaisi)); exclusSaisi = ""
                    }
                }
            }

            Titre("🕙  HORAIRES")
            Carte {
                LigneSwitch("Sonner 24h/24", "Sinon uniquement entre l'heure de début et de fin", r.toutLeTemps) {
                    r = r.copy(toutLeTemps = it)
                }
                if (!r.toutLeTemps) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CaseHeure("Début", r.debut, Modifier.weight(1f)) {
                            choisirHeure(ctx, r.debut) { m -> r = r.copy(debut = m) }
                        }
                        CaseHeure("Fin", r.fin, Modifier.weight(1f)) {
                            choisirHeure(ctx, r.fin) { m -> r = r.copy(fin = m) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(descriptionPlage(r), color = Couleurs.Gris, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Jours", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (j in 0..6) {
                        val on = r.jourActif(j)
                        Box(Modifier.size(40.dp).clip(CircleShape)
                            .background(if (on) Couleurs.Violet else Couleurs.Carte2)
                            .clickable { r = r.copy(jours = r.jours xor (1 shl j)) },
                            contentAlignment = Alignment.Center) {
                            Text(JOURS_COURTS[j], color = if (on) Color.White else Couleurs.Gris,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pastille("Tous les jours", Couleurs.Cyan) { r = r.copy(jours = 127) }
                    Pastille("Lun → Ven", Couleurs.Cyan) { r = r.copy(jours = 31) }
                    Pastille("Week-end", Couleurs.Cyan) { r = r.copy(jours = 96) }
                }
            }

            Titre("🔔  ALARME")
            Carte {
                ReglageSlider("Durée maximale", r.dureeMin, 1..30, "min") { r = r.copy(dureeMin = it) }
                ReglageSlider("Répéter (snooze) dans", r.snoozeMin, 1..30, "min") { r = r.copy(snoozeMin = it) }
                ReglageSlider("Anti-répétition", r.pauseMin, 0..60, "min",
                    "Ne pas resonner pour d'autres messages pendant…") { r = r.copy(pauseMin = it) }
                LigneSwitch("Volume progressif", "Commence doucement puis monte en 30 s", r.crescendo) { r = r.copy(crescendo = it) }
                LigneSwitch("Forcer le volume au maximum", null, r.volumeMax) { r = r.copy(volumeMax = it) }
                LigneSwitch("Vibration", null, r.vibrer) { r = r.copy(vibrer = it) }
                LigneSwitch("Flash clignotant", "Le flash de l'appareil photo clignote", r.flash) { r = r.copy(flash = it) }
                LigneSwitch("Lire le message à voix haute", "Synthèse vocale toutes les 20 s", r.lireMessage) { r = r.copy(lireMessage = it) }
                LigneSwitch("Défi pour arrêter", "Résoudre un calcul : impossible d'arrêter à moitié endormi", r.defi) { r = r.copy(defi = it) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie du réveil")
                    if (r.sonnerie.isNotEmpty()) {
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(r.sonnerie))
                    }
                    choixSonnerie.launch(i)
                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sonnerie", color = Color.White, fontSize = 16.sp)
                        Text(nomSonnerie(ctx, r.sonnerie), color = Couleurs.Cyan, fontSize = 13.sp)
                    }
                    Text("›", color = Couleurs.Gris, fontSize = 22.sp)
                }
            }

            Titre("🧪  TESTER CE RÉVEIL")
            Carte {
                ChampTexte(essaiCanal, { essaiCanal = it }, "Nom du canal")
                Spacer(Modifier.height(8.dp))
                ChampTexte(essaiMessage, { essaiMessage = it }, "Message, ex : BUY GOLD 2350")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonSecondaire("🔎 Vérifier", Modifier.weight(1f)) {
                        val raison = r.raisonRefus(essaiCanal.trim(), essaiMessage, avecHoraire = false)
                        resultat = if (raison == null)
                            "✅ Ce message ferait sonner l'alarme" + (if (!r.toutLeTemps) " (entre ${r.horaireTexte()})" else "")
                        else "❌ Pas d'alarme : $raison"
                    }
                    BoutonSecondaire("🔔 Écouter", Modifier.weight(1f), Couleurs.Cyan) {
                        AlarmEngine.declencher(ctx, r.copy(dureeMin = 1), essaiCanal.ifBlank { "Test" },
                            essaiMessage.ifBlank { "Test de « ${r.nom} »" })
                    }
                }
                resultat?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = if (it.startsWith("✅")) Couleurs.Vert else Couleurs.Rose, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.padding(16.dp)) {
            BoutonGradient("💾  Enregistrer") {
                val final = r.copy(
                    nom = r.nom.trim().ifEmpty { "Réveil" },
                    canaux = if (canalSaisi.isNotBlank()) (r.canaux + canalSaisi.trim()).distinct() else r.canaux,
                    motsInclus = ajouterMots(r.motsInclus, motSaisi),
                    motsExclus = ajouterMots(r.motsExclus, exclusSaisi)
                )
                if (final.jours == 0) {
                    Toast.makeText(ctx, "Choisissez au moins un jour", Toast.LENGTH_SHORT).show()
                } else {
                    Store.enregistrer(ctx, final)
                    Toast.makeText(ctx, "Réveil enregistré ✅", Toast.LENGTH_SHORT).show()
                    fermer()
                }
            }
        }
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            title = { Text("Supprimer ce réveil ?") },
            text = { Text("« ${r.nom} » sera définitivement supprimé.") },
            confirmButton = {
                TextButton(onClick = { Store.supprimer(ctx, r.id); confirmer = false; fermer() }) {
                    Text("Supprimer", color = Couleurs.Rose)
                }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler") } },
            containerColor = Couleurs.Carte
        )
    }
}
