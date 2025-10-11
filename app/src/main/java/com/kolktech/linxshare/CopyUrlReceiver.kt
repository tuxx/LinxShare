package com.kolktech.linxshare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

class CopyUrlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("linx url", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $url", Toast.LENGTH_SHORT).show()
    }
}


