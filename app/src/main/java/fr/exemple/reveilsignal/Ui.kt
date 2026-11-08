package fr.exemple.reveilsignal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object Couleurs {
    val Fond = Color(0xFF0A0B14)
    val Carte = Color(0xFF151827)
    val Carte2 = Color(0xFF1F2338)
    val Violet = Color(0xFF7C5CFF)
    val Bleu = Color(0xFF3B82F6)
    val Cyan = Color(0xFF22D3EE)
    val Rose = Color(0xFFFF4D8D)
    val Vert = Color(0xFF34D399)
    val Orange = Color(0xFFFFB020)
    val Gris = Color(0xFF9AA0B8)
}

@Composable
fun ThemeReveil(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Couleurs.Violet, onPrimary = Color.White,
            secondary = Couleurs.Cyan,
            background = Couleurs.Fond, onBackground = Color.White,
            surface = Couleurs.Carte, onSurface = Color.White,
            surfaceVariant = Couleurs.Carte2, onSurfaceVariant = Couleurs.Gris,
            outline = Color(0xFF2E3350)
        ),
        content = content
    )
}

@Composable
fun Carte(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Couleurs.Carte).padding(18.dp),
        content = content
    )
}

@Composable
fun Titre(t: String) {
    Text(t, color = Couleurs.Gris, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 10.dp))
}

@Composable
fun Pastille(t: String, couleur: Color = Couleurs.Violet, onClick: (() -> Unit)? = null) {
    val base = Modifier.clip(RoundedCornerShape(50)).background(couleur.copy(alpha = 0.16f))
    Text(t, color = couleur, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun LigneSwitch(titre: String, sous: String?, valeur: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .clickable { onChange(!valeur) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titre, color = Color.White, fontSize = 16.sp)
            if (sous != null) Text(sous, color = Couleurs.Gris, fontSize = 12.sp)
        }
        Switch(checked = valeur, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Couleurs.Violet,
                uncheckedThumbColor = Couleurs.Gris, uncheckedTrackColor = Couleurs.Carte2,
                uncheckedBorderColor = Couleurs.Carte2))
    }
}

@Composable
fun BoutonGradient(texte: String, modifier: Modifier = Modifier,
                   couleurs: List<Color> = listOf(Couleurs.Violet, Couleurs.Bleu, Couleurs.Cyan),
                   onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.horizontalGradient(couleurs)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(texte, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoutonSecondaire(texte: String, modifier: Modifier = Modifier,
                     couleur: Color = Couleurs.Violet, onClick: () -> Unit) {
    Box(modifier.height(50.dp).clip(RoundedCornerShape(16.dp))
        .background(couleur.copy(alpha = 0.15f)).clickable(onClick = onClick)
        .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(texte, color = couleur, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun ChampTexte(valeur: String, onChange: (String) -> Unit, label: String,
               modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = valeur, onValueChange = onChange, label = { Text(label) },
        singleLine = true, shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = Couleurs.Violet, unfocusedBorderColor = Color(0xFF2E3350),
            focusedLabelColor = Couleurs.Violet, unfocusedLabelColor = Couleurs.Gris,
            cursorColor = Couleurs.Violet)
    )
}

@Composable
fun ReglageSlider(titre: String, valeur: Int, plage: IntRange, unite: String,
                  aide: String? = null, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titre, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("$valeur $unite", color = Couleurs.Cyan, fontWeight = FontWeight.Bold)
        }
        if (aide != null) Text(aide, color = Couleurs.Gris, fontSize = 12.sp)
        Slider(
            value = valeur.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = plage.first.toFloat()..plage.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Couleurs.Violet,
                activeTrackColor = Couleurs.Violet, inactiveTrackColor = Couleurs.Carte2)
        )
    }
}
