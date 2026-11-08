package fr.exemple.reveilsignal

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Store.journal(this, "✅ Surveillance connectée")
    }

    override fun onListenerDisconnected() {
        Store.journal(this, "⚠️ Surveillance déconnectée, reconnexion…")
        NotificationListenerService.requestRebind(ComponentName(this, NotifListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in TELEGRAM) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val ex = n.extras ?: return

        val brut = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: ""
        val titre = Store.nettoyerTitre(brut)

        @Suppress("DEPRECATION")
        val messages = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val dernier = (messages?.lastOrNull() as? Bundle)?.getCharSequence("text")?.toString()
        val texte = dernier
            ?: (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            ?: ""
        if (titre.isBlank() && texte.isBlank()) return

        Store.ajouterCanalVu(this, titre)

        if (!Store.surveille(this)) {
            Store.journal(this, "Ignoré (désactivé ou en pause) : ${court(titre)}")
            return
        }
        if (AlarmEngine.sonne) return

        val maintenant = System.currentTimeMillis()
        for (r in Store.regles(this)) {
            val raison = r.raisonRefus(titre, texte)
            if (raison != null) {
                Store.journal(this, "« ${r.nom} » : $raison — ${court(titre)}")
                continue
            }
            val avant = maxOf(Store.dernier(this, r.id), Store.dernierArret(this))
            if (maintenant - avant < r.pauseMin * 60_000L) {
                Store.journal(this, "« ${r.nom} » : anti-répétition")
                continue
            }
            Store.setDernier(this, r.id, maintenant)
            Store.ajouterHistorique(this, r.nom, titre, texte)
            Store.journal(this, "🔔 « ${r.nom} » : ${court(titre)} — ${court(texte)}")
            AlarmEngine.declencher(this, r, titre, texte)
            return
        }
    }

    private fun court(s: String) = if (s.length > 50) s.take(50) + "…" else s
}
