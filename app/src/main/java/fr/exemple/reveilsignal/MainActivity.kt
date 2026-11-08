@file:OptIn(ExperimentalLayoutApi::class)

package fr.exemple.reveilsignal

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Couleurs.Fond.toArgb()
        window.navigationBarColor = Couleurs.Fond.toArgb()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            ThemeReveil {
                val t = tick.intValue
                App(t) { tick.intValue = tick.intValue + 1 }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue = tick.intValue + 1
    }
}

class Verif(val nom: String, val aide: String, val ok: Boolean, val action: () -> Unit)

fun ouvrirReglage(ctx: Context, i: Intent) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun verifications(ctx: Context): List<Verif> {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val pm = ctx.getSystemService(PowerManager::class.java)
    val uri = Uri.parse("package:${ctx.packageName}")
    val ecoute = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        ?.contains(ctx.packageName) == true
    val l = mutableListOf(
        Verif("Accès aux notifications", "Pour lire les messages Telegram", ecoute) {
            ouvrirReglage(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        Verif("Notifications de l'app", "Pour afficher l'alarme", nm.areNotificationsEnabled()) {
            ouvrirReglage(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        },
        Verif("Afficher par-dessus les autres applis", "Pour allumer l'écran", Settings.canDrawOverlays(ctx)) {
            ouvrirReglage(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
        },
        Verif("Batterie non restreinte", "Pour fonctionner toute la nuit",
            pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            ouvrirReglage(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
        },
        Verif("Alarmes et rappels", "Pour réveiller l'écran et répéter", AlarmEngine.peutProgrammer(ctx)) {
            ouvrirReglage(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
        }
    )
    if (Build.VERSION.SDK_INT >= 34) {
        l.add(Verif("Notifications plein écran", "Écran d'alarme sur l'écran verrouillé",
            nm.canUseFullScreenIntent()) {
            ouvrirReglage(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri))
        })
    }
    return l
}

fun prochain8h(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 8); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}

@Composable
fun App(tick: Int, rafraichir: () -> Unit) {
    var edition by remember { mutableStateOf<Regle?>(null) }
    val e = edition
    Box(Modifier.fillMaxSize().background(Couleurs.Fond)) {
        if (e != null) {
            BackHandler { edition = null; rafraichir() }
            key(e.id) { Editeur(e) { edition = null; rafraichir() } }
        } else {
            Accueil(tick, rafraichir) { edition = it }
        }
    }
}

@Composable
fun Accueil(tick: Int, rafraichir: () -> Unit, ouvrir: (Regle) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { while (true) { delay(2000); rafraichir() } }
    val maintenant = System.currentTimeMillis()
    val actif = Store.actif(ctx)
    val pause = Store.pauseJusqua(ctx)
    val enPause = actif && pause > maintenant
    val regles = Store.regles(ctx)
    val verifs = verifications(ctx)
    val histo = Store.historique(ctx)
    val snooze = Store.snooze(ctx)
    var voirJournal by remember { mutableStateOf(false) }
    var voirVerifs by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        item { EnTete(actif, enPause, pause, regles, histo, rafraichir) }

        if (AlarmEngine.sonne) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Couleurs.Rose, Color(0xFFFF6B3D))))
                .clickable { AlarmEngine.arreter(ctx); rafraichir() }.padding(20.dp)) {
                Text("🔔 Alarme en cours", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Touchez ici pour l'arrêter", color = Color.White.copy(alpha = 0.85f))
            }
        }

        if (snooze != null && snooze.quand > maintenant) item {
            Carte {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💤", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Répétition à ${heure(snooze.quand)}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(snooze.titre, color = Couleurs.Gris, fontSize = 13.sp, maxLines = 1)
                    }
                    TextButton(onClick = { AlarmEngine.annulerSnooze(ctx); rafraichir() }) {
                        Text("Annuler", color = Couleurs.Rose)
                    }
                }
            }
        }

        item { CarteVerifs(verifs, voirVerifs) { voirVerifs = !voirVerifs } }

        item { Titre("MES RÉVEILS") }

        items(regles, key = { it.id }) { r ->
            CarteRegle(r,
                onToggle = { b -> Store.enregistrer(ctx, r.copy(active = b)); rafraichir() },
                onClick = { ouvrir(r) })
        }

        item {
            BoutonGradient("＋  Nouveau réveil") {
                ouvrir(Regle(id = System.currentTimeMillis(), nom = "Nouveau réveil"))
            }
        }

        item {
            Column {
                Titre("TESTER")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoutonSecondaire("🔔 Tester", Modifier.weight(1f)) {
                        AlarmEngine.declencher(ctx, Regle(nom = "Test", dureeMin = 1),
                            "Canal de test", "BUY GOLD 2350 — ceci est un test")
                    }
                    BoutonSecondaire("⏳ Dans 10 s", Modifier.weight(1f), Couleurs.Cyan) {
                        Toast.makeText(ctx, "Verrouillez l'écran maintenant 🔒", Toast.LENGTH_LONG).show()
                        val app = ctx.applicationContext
                        Handler(Looper.getMainLooper()).postDelayed({
                            AlarmEngine.declencher(app, Regle(nom = "Test écran verrouillé", dureeMin = 1),
                                "Test", "Si l'écran s'est allumé tout seul, tout fonctionne ✅")
                        }, 10_000)
                    }
                }
            }
        }

        item {
            Column {
                Titre("HISTORIQUE DES ALARMES")
                Carte {
                    if (histo.isEmpty()) Text("Aucune alarme pour l'instant.", color = Couleurs.Gris)
                    histo.take(10).forEachIndexed { i, ev ->
                        if (i > 0) HorizontalDivider(color = Couleurs.Carte2)
                        Row(Modifier.padding(vertical = 10.dp)) {
                            Column(Modifier.width(60.dp)) {
                                Text(heure(ev.t), color = Color.White, fontWeight = FontWeight.Bold)
                                Text(heure(ev.t, "dd/MM"), color = Couleurs.Gris, fontSize = 12.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(ev.titre.ifBlank { ev.nom }, color = Color.White,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(ev.texte, color = Couleurs.Gris, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("via ${ev.nom}", color = Couleurs.Violet, fontSize = 11.sp)
                            }
                        }
                    }
                    if (histo.isNotEmpty()) {
                        TextButton(onClick = { Store.effacerHistorique(ctx); rafraichir() }) {
                            Text("Effacer l'historique", color = Couleurs.Gris)
                        }
                    }
                }
            }
        }

        item {
            Column {
                Titre("RÉGLAGES")
                Carte {
                    LigneSwitch("Boutons de volume = arrêter",
                        "Volume + ou – coupe l'alarme (sauf avec le défi)", Store.volumeArrete(ctx)) {
                        Store.setVolumeArrete(ctx, it); rafraichir()
                    }
                    LigneSwitch("Afficher le diagnostic", "Journal technique des notifications", voirJournal) {
                        voirJournal = it
                    }
                    Spacer(Modifier.height(6.dp))
                    BoutonSecondaire("Infos de l'application", Modifier.fillMaxWidth(), Couleurs.Gris) {
                        ouvrirReglage(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
        }

        if (voirJournal) item {
            Carte {
                Text(Store.lireJournal(ctx).ifEmpty { "Journal vide." }, color = Couleurs.Gris,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                TextButton(onClick = { Store.effacerJournal(ctx); rafraichir() }) {
                    Text("Effacer le journal", color = Couleurs.Gris)
                }
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun EnTete(actif: Boolean, enPause: Boolean, pause: Long, regles: List<Regle>,
           histo: List<Evenement>, rafraichir: () -> Unit) {
    val ctx = LocalContext.current
    val enCours = regles.any { it.active && it.dansPlage() }
    val statut = when {
        !actif -> "🔴 Désactivé"
        enPause -> "⏸ En pause jusqu'à ${heure(pause)}"
        regles.none { it.active } -> "⚪ Aucun réveil activé"
        enCours -> "🟢 Surveillance active"
        else -> "🌙 En attente de la plage horaire"
    }
    val semaine = histo.count { it.t > System.currentTimeMillis() - 7L * 24 * 3600 * 1000 }
    val degrade = if (actif && !enPause) listOf(Couleurs.Violet, Couleurs.Bleu, Couleurs.Cyan)
                  else listOf(Color(0xFF3A3F5C), Color(0xFF23263A))

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(degrade)).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Réveil Signal", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(statut, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
            }
            Switch(checked = actif, onCheckedChange = { Store.setActif(ctx, it); rafraichir() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.35f),
                    uncheckedThumbColor = Couleurs.Gris,
                    uncheckedTrackColor = Color.Black.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.height(12.dp))
        Text("📊 $semaine alarme(s) ces 7 derniers jours",
            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        if (actif) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enPause) {
                    PuceBlanche("▶ Reprendre") { Store.setPause(ctx, 0L); rafraichir() }
                } else {
                    PuceBlanche("⏸ Pause 1 h") {
                        Store.setPause(ctx, System.currentTimeMillis() + 3_600_000L); rafraichir()
                    }
                    PuceBlanche("⏸ Jusqu'à 8 h") { Store.setPause(ctx, prochain8h()); rafraichir() }
                }
            }
        }
    }
}

@Composable
fun PuceBlanche(t: String, onClick: () -> Unit) {
    Text(t, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun CarteVerifs(verifs: List<Verif>, ouvert: Boolean, basculer: () -> Unit) {
    val ok = verifs.count { it.ok }
    val tout = ok == verifs.size
    val deplie = ouvert || !tout
    Carte {
        Row(Modifier.fillMaxWidth().clickable(onClick = basculer),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (tout) "✅" else "⚠️", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (tout) "Tout est prêt" else "Configuration incomplète",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("$ok / ${verifs.size} autorisations", color = Couleurs.Gris, fontSize = 13.sp)
            }
            Text(if (deplie) "▲" else "▼", color = Couleurs.Gris)
        }
        if (deplie) {
            Spacer(Modifier.height(8.dp))
            verifs.forEach { v ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = v.action).padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (v.ok) "✅" else "❌", fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.nom, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(v.aide, color = Couleurs.Gris, fontSize = 12.sp)
                    }
                    if (!v.ok) Text("Corriger ›", color = Couleurs.Rose, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CarteRegle(r: Regle, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val enCours = r.active && r.dansPlage()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Couleurs.Carte)
        .clickable(onClick = onClick).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.nom, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(r.horaireTexte(), fontSize = 30.sp, fontWeight = FontWeight.Light,
                    color = if (r.active) Color.White else Couleurs.Gris)
            }
            Switch(checked = r.active, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                    checkedTrackColor = Couleurs.Violet, uncheckedTrackColor = Couleurs.Carte2))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (enCours) Pastille("● En cours", Couleurs.Vert)
            Pastille(r.joursTexte(), Couleurs.Cyan)
            if (r.canaux.isEmpty()) Pastille("📢 Tous les messages", Couleurs.Orange)
            else r.canaux.forEach { Pastille("📢 $it") }
            r.motsInclus.forEach { Pastille("🔑 $it", Couleurs.Rose) }
            if (r.motsExclus.isNotEmpty()) Pastille("🚫 ${r.motsExclus.joinToString(", ")}", Couleurs.Gris)
            if (r.defi) Pastille("🧠 Défi", Couleurs.Orange)
        }
        Spacer(Modifier.height(6.dp))
        Text("Toucher pour modifier", color = Couleurs.Gris, fontSize = 12.sp)
    }
}
