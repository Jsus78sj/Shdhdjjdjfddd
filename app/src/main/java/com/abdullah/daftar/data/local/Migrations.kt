package com.abdullah.daftar.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ترحيلات قاعدة البيانات.
 *
 * النسخة 3 هي خط الأساس: النسختان 1 و2 كانتا نسخ تطوير بلا مخطط مُصدَّر،
 * لذلك لا يمكن ترحيلهما (انظر fallbackToDestructiveMigrationFrom في AppModule).
 * من النسخة 3 فصاعداً كل تغيير في الجداول يحتاج Migration صريحاً هنا،
 * وإلا سيتوقف التطبيق عند الفتح بدلاً من مسح بياناتك بصمت.
 *
 * مثال (عند إضافة عمود في النسخة 5):
 *
 * val MIGRATION_4_5 = object : Migration(4, 5) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE pdf_files ADD COLUMN notes TEXT")
 *     }
 * }
 * ثم أضفه إلى ALL أدناه.
 */
object DaftarMigrations {

    /** أقدم نسخة من قاعدة البيانات يمكن فتحها أو استعادتها من نسخة احتياطية. */
    const val MIN_SUPPORTED_VERSION = 3

    /**
     * 3 → 4:
     *  - pdf_page_texts.normalizedText: نص مُطبَّع للبحث العربي (انظر ArabicTextNormalizer).
     *  - pdf_files.extractionError: سبب فشل الفهرسة (ملف تالف/بكلمة مرور) بدل إعادة المحاولة للأبد.
     *
     * التطبيع لا يمكن حسابه في SQL، لذلك تُحذف النصوص المفهرسة القديمة ويُعلَّم كل ملف
     * "غير مفهرس"؛ يعيد التطبيع فهرستها تلقائياً في الخلفية عند أول فتح (MainActivity).
     * ملفات PDF والملاحظات والتظليلات لا تتأثر.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pdf_page_texts ADD COLUMN normalizedText TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE pdf_files ADD COLUMN extractionError TEXT")
            db.execSQL("DELETE FROM pdf_page_texts")
            db.execSQL("UPDATE pdf_files SET isTextExtracted = 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_3_4,
    )
}
