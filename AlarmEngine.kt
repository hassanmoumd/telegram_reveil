package fr.exemple.reveilsignal

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object AlarmEngine {
    const val ACTION_STOP = "fr.exemple.reveilsignal.STOP"
    const val ACTION_SNOOZE = "fr.exemple.reveilsignal.SNOOZE"
    private const val CANAL = "alarme_signal"
    private const val NOTIF_ID = 77
    private const val CODE_ECRAN = 1
    private const val CODE_SNOOZE = 6

    @Volatile var sonne = false
        private set
    @Volatile var regle = Regle()
        private set
    @Volatile var titre = ""
        private set
    @Volatile var texte = ""
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibreur: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var camera: CameraManager? = null
    private var flashId: String? = null
    private var flashOn = false
    private var wlCpu: PowerManager.WakeLock? = null
    private var wlEcran: PowerManager.WakeLock? = null
    private var volumeAvant = -1
    private var volumeCourant = 1f
    private var enLecture = false

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun declencher(ctx: Context, r: Regle, t: String, m: String) {
        val app = ctx.applicationContext
        handler.post { demarrer(app, r, t, m) }
    }

    private fun demarrer(app: Context, r: Regle, t: String, m: String) {
        if (sonne) return
        sonne = true
        regle = r; titre = t; texte = m
        annulerSnooze(app)
        val duree = r.dureeMin * 60_000L

        val pm = app.getSystemService(PowerManager::class.java)
        wlCpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reveil:cpu")
            .apply { acquire(duree + 60_000L) }
        try {
            @Suppress("DEPRECATION")
            val f = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
            wlEcran = pm.newWakeLock(f, "reveil:ecran").apply { acquire(duree + 60_000L) }
        } catch (e: Exception) {
        }

        afficherNotification(app)
        lancerEcran(app)
        demarrerSon(app, r)
        if (r.vibrer) vibrer(app)
        if (r.flash) demarrerFlash(app)
        if (r.lireMessage) preparerVoix(app)

        handler.postDelayed({
            Store.journal(app, "⏱ Arrêt automatique")
            arreter(app)
        }, duree)
    }

    private fun piEcran(app: Context, code: Int, snooze: Boolean = false): PendingIntent =
        PendingIntent.getActivity(app, code,
            Intent(app, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra("snooze", snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun peutProgrammer(ctx: Context): Boolean =
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun programmer(app: Context, quand: Long, pi: PendingIntent) {
        try {
            if (peutProgrammer(app)) {
                app.getSystemService(AlarmManager::class.java)
                    .setAlarmClock(AlarmManager.AlarmClockInfo(quand, pi), pi)
            }
        } catch (e: Exception) {
            Store.journal(app, "Réveil système impossible : ${e.javaClass.simpleName}")
        }
    }

    private fun lancerEcran(app: Context) {
        try {
            app.startActivity(Intent(app, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
        programmer(app, System.currentTimeMillis() + 1500, piEcran(app, CODE_ECRAN))
    }

    private fun creerPlayer(app: Context, uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(app, uri)
            isLooping = true
            prepare()
            start()
        }
    } catch (e: Exception) { null }

    private fun demarrerSon(app: Context, r: Regle) {
        val am = app.getSystemService(AudioManager::class.java)
        if (r.volumeMax) {
            try {
                volumeAvant = am.getStreamVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            } catch (e: Exception) {
            }
        }
        val uris = listOfNotNull(
            if (r.sonnerie.isNotEmpty()) Uri.parse(r.sonnerie) else null,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        for (u in uris) {
            player = creerPlayer(app, u)
            if (player != null) break
        }
        if (player == null) Store.journal(app, "⚠️ Sonnerie illisible")
        volumeCourant = if (r.crescendo) 0.1f else 1f
        player?.setVolume(volumeCourant, volumeCourant)
        if (r.crescendo) handler.postDelayed(crescendo, 2000)
    }

    private val crescendo = object : Runnable {
        override fun run() {
            if (!sonne) return
            volumeCourant = (volumeCourant + 0.06f).coerceAtMost(1f)
            if (!enLecture) player?.setVolume(volumeCourant, volumeCourant)
            if (volumeCourant < 1f) handler.postDelayed(this, 2000)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrer(app: Context) {
        try {
            val v = app.getSystemService(VibratorManager::class.java).defaultVibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 600), 0), attrs)
            vibreur = v
        } catch (e: Exception) {
        }
    }

    private fun demarrerFlash(app: Context) {
        try {
            val cm = app.getSystemService(CameraManager::class.java)
            camera = cm
            flashId = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (flashId != null) handler.post(clignotement)
        } catch (e: Exception) {
        }
    }

    private val clignotement = object : Runnable {
        override fun run() {
            val cm = camera ?: return
            val id = flashId ?: return
            if (!sonne) return
            flashOn = !flashOn
            try { cm.setTorchMode(id, flashOn) } catch (e: Exception) { }
            handler.postDelayed(this, 400)
        }
    }

    private fun preparerVoix(app: Context) {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) handler.postDelayed({ parler() }, 2500)
        }
    }

    private fun parler() {
        val t = tts ?: return
        if (!sonne) return
        t.setLanguage(Locale.FRANCE)
        t.setAudioAttributes(attrs)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post {
                    enLecture = false
                    player?.setVolume(volumeCourant, volumeCourant)
                    if (sonne) handler.postDelayed({ parler() }, 20_000)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone(utteranceId) }
        })
        enLecture = true
        player?.setVolume(0.08f, 0.08f)
        t.speak("Signal reçu. $titre. ${texte.take(300)}", TextToSpeech.QUEUE_FLUSH, null, "msg")
    }

    private fun afficherNotification(app: Context) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Alarme signal", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        val stop = PendingIntent.getBroadcast(app, 2,
            Intent(app, AlarmReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = PendingIntent.getBroadcast(app, 3,
            Intent(app, AlarmReceiver::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(app, CANAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 ${regle.nom}")
            .setContentText(if (titre.isNotEmpty()) "$titre : $texte" else texte)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(piEcran(app, 4))
            .setFullScreenIntent(piEcran(app, 5), true)
            .addAction(Notification.Action.Builder(null, "⏹ Arrêter", stop).build())
            .addAction(Notification.Action.Builder(null, "💤 ${regle.snoozeMin} min", snooze).build())
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (e: Exception) { }
    }

    fun arreter(ctx: Context) {
        val app = ctx.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { arreter(app) }
            return
        }
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
        vibreur?.cancel()
        vibreur = null
        flashId?.let { id -> try { camera?.setTorchMode(id, false) } catch (e: Exception) { } }
        flashId = null
        camera = null
        tts?.let { it.stop(); it.shutdown() }
        tts = null
        enLecture = false
        if (volumeAvant >= 0) {
            try {
                app.getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, volumeAvant, 0)
            } catch (e: Exception) {
            }
        }
        volumeAvant = -1
        wlCpu?.let { if (it.isHeld) it.release() }
        wlCpu = null
        wlEcran?.let { if (it.isHeld) it.release() }
        wlEcran = null
        app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        app.getSystemService(AlarmManager::class.java).cancel(piEcran(app, CODE_ECRAN))
        if (sonne) Store.setDernierArret(app, System.currentTimeMillis())
        sonne = false
    }

    fun snoozer(ctx: Context) {
        val app = ctx.applicationContext
        val r = regle
        val t = titre
        val m = texte
        arreter(app)
        val quand = System.currentTimeMillis() + r.snoozeMin * 60_000L
        Store.setSnooze(app, Snooze(quand, r, t, m))
        programmer(app, quand, piEcran(app, CODE_SNOOZE, true))
        Store.journal(app, "💤 Répétition prévue à ${heure(quand)}")
    }

    fun annulerSnooze(ctx: Context) {
        val app = ctx.applicationContext
        app.getSystemService(AlarmManager::class.java).cancel(piEcran(app, CODE_SNOOZE, true))
        Store.effacerSnooze(app)
    }
}
