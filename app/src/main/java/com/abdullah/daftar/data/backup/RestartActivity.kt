package com.abdullah.daftar.data.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.IntentCompat

/**
 * نشاط شفاف يعمل في عملية منفصلة (:restart) — راجع المانيفست.
 * يقتل العملية الرئيسية ثم يطلق التطبيق من جديد، فيبدأ Room بالقاعدة المستعادة.
 * (نفس فكرة مكتبة ProcessPhoenix.)
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nextIntent = IntentCompat.getParcelableExtra(intent, EXTRA_NEXT_INTENT, Intent::class.java)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)

        if (mainPid > 0) Process.killProcess(mainPid)
        if (nextIntent != null) startActivity(nextIntent)

        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_NEXT_INTENT = "next_intent"
        const val EXTRA_MAIN_PID = "main_pid"
    }
}
