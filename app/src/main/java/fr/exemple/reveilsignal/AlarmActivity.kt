package fr.exemple.reveilsignal

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = 0xFF2A0614.toInt()
        window.navigationBarColor = 0xFF0A0B14.toInt()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })
        traiter(intent)
        setContent {
            EcranAlarme(
                onStop = { arreter() },
                onSnooze = { AlarmEngine.snoozer(this); finish() },
                onOuvrir = { ouvrirTelegram() },
                onFini = { finish() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        traiter(intent)
    }

    private fun traiter(i: Intent?) {
        if (i?.getBooleanExtra("snooze", false) == true && !AlarmEngine.sonne) {
            val s = Store.snooze(this) ?: return
            Store.effacerSnooze(this)
            AlarmEngine.declencher(this, s.regle, s.titre, s.texte)
        }
    }

    private fun arreter() {
        Store.journal(this, "⏹ Arrêtée depuis l'écran")
        AlarmEngine.arreter(this)
        finish()
    }

    private fun ouvrirTelegram() {
        AlarmEngine.arreter(this)
        val lancement = TELEGRAM.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        if (lancement != null) startActivity(lancement)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            && AlarmEngine.sonne) {
            if (!AlarmEngine.regle.defi && Store.volumeArrete(this)) arreter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun EcranAlarme(onStop: () -> Unit, onSnooze: () -> Unit, onOuvrir: () -> Unit, onFini: () -> Unit) {
    val ctx = LocalContext.current
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var defiReussi by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { maintenant = System.currentTimeMillis(); delay(1000) }
    }
    LaunchedEffect(Unit) {
        delay(2500)
        while (AlarmEngine.sonne) delay(500)
        onFini()
    }
    val r = AlarmEngine.regle
    val anim = rememberInfiniteTransition(label = "alarme")
    val pulse by anim.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")
    val haut by anim.animateColor(
        initialValue = Color(0xFF8E0E3A), targetValue = Color(0xFFE5484D),
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "fond")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(haut, Color(0xFF2A0614), Color(0xFF0A0B14))))
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(heure(maintenant), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Thin)
                Text(heure(maintenant, "EEEE d MMMM").replaceFirstChar { it.uppercase() },
                    color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Text("🔔", fontSize = 44.sp, modifier = Modifier.scale(pulse))
                Spacer(Modifier.height(6.dp))
                Text(r.nom, color = Color.White, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.10f)).padding(18.dp)) {
                Text("📢 ${AlarmEngine.titre}", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(AlarmEngine.texte, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (r.defi && !defiReussi) {
                    DefiCalcul { defiReussi = true; onStop() }
                } else {
                    Box(
                        Modifier.size(160.dp).scale(pulse).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFFFD6DC))))
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏹", fontSize = 34.sp)
                            Text("ARRÊTER", color = Color(0xFF8E0E3A), fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onSnooze),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💤  Répéter dans ${r.snoozeMin} min", color = Color.White,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onOuvrir) {
                    Text("Arrêter et ouvrir Telegram", color = Color.White.copy(alpha = 0.85f))
                }
                if (!r.defi && Store.volumeArrete(ctx)) {
                    Text("Astuce : un bouton de volume arrête aussi l'alarme",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DefiCalcul(onReussi: () -> Unit) {
    val a = remember { (12..49).random() }
    val b = remember { (12..49).random() }
    var rep by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Color.White.copy(alpha = 0.12f)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🧠 Défi pour arrêter", color = Color.White, fontWeight = FontWeight.Bold)
        Text("$a + $b = ?", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Light)
        OutlinedTextField(
            value = rep,
            onValueChange = { rep = it.filter { c -> c.isDigit() }.take(3); erreur = false },
            singleLine = true,
            isError = erreur,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.4f))
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            if (rep.toIntOrNull() == a + b) onReussi() else { erreur = true; rep = "" }
        }) { Text("Valider") }
    }
}
