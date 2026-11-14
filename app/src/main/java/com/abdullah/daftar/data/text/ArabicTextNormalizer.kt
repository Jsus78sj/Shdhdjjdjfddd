package com.abdullah.daftar.data.text

import java.text.Normalizer

/**
 * تطبيع النصوص لأغراض الفهرسة والبحث (عربي + لاتيني).
 *
 * يُستخدم بنفس الطريقة عند الفهرسة (نص الصفحة) وعند البحث (عبارة المستخدم)، فيتطابق الطرفان
 * حتى لو اختلف الشكل الظاهر للحروف. الخطوات:
 *  1) NFKC: يحوّل أشكال العرض العربية (ﻣﺴﺎﻧﺪ، ﻻ، ﷲ ...) التي يُخرجها PdfBox إلى الحروف الأساسية.
 *  2) إزالة التشكيل والتطويل (ـ) وعلامات الاتجاه غير المرئية.
 *  3) توحيد الألف (أ إ آ ٱ → ا)، والألف المقصورة (ى → ي)، والتاء المربوطة (ة → ه).
 *  4) توحيد الأرقام الهندية (٠-٩ و۰-۹) إلى 0-9.
 *  5) تحويل الحروف اللاتينية إلى صغيرة وضغط المسافات المتتالية (ومنها الأسطر الجديدة) إلى مسافة واحدة.
 *
 * ملاحظة: النص الأصلي يبقى كما هو للعرض؛ التطبيع يُخزَّن في عمود مستقل ويُستخدم للمطابقة فقط.
 * أي تغيير في هذه القواعد يستلزم إعادة فهرسة الملفات (راجع Migration عند الحاجة).
 */
object ArabicTextNormalizer {

    /** النص بعد التطبيع + موضع كل حرف منه داخل النص الأصلي (لبناء المقتطفات). */
    class NormalizedText(
        val text: String,
        /** offsets[i] = موضع الحرف i في النص الأصلي، والعنصر الأخير = طول النص الأصلي. فارغة إن لم تُطلب. */
        val offsets: IntArray
    )

    private const val DROP = -1
    private val WHITESPACE_RUN = Regex("\\s+")

    /** تطبيع نص كامل (للفهرسة). */
    fun normalize(input: String): String = process(input, trackOffsets = false).text

    /** تطبيع عبارة بحث: تطبيع + حذف المسافات من الطرفين. */
    fun normalizeQuery(query: String): String = normalize(query).trim()

    fun normalizeWithOffsets(input: String): NormalizedText = process(input, trackOffsets = true)

    /**
     * مقتطف من [text] حول أول موضع يطابق [query] بعد التطبيع.
     * يُعرض النص الأصلي (بتشكيله) لا المطبَّع. إن لم يوجد تطابق يُعاد بداية النص.
     */
    fun snippet(
        text: String,
        query: String,
        charsBefore: Int,
        charsAfter: Int,
        fallbackLength: Int = 120
    ): String {
        val nq = normalizeQuery(query)
        if (nq.isEmpty()) return WHITESPACE_RUN.replace(text, " ").trim().take(fallbackLength)

        val mapped = normalizeWithOffsets(text)
        val idx = mapped.text.indexOf(nq)
        if (idx < 0) return WHITESPACE_RUN.replace(text, " ").trim().take(fallbackLength)

        val matchStart = mapped.offsets[idx]
        val matchEnd = mapped.offsets[idx + nq.length]
        val start = (matchStart - charsBefore).coerceAtLeast(0)
        val end = (matchEnd + charsAfter).coerceAtMost(text.length)
        val body = WHITESPACE_RUN.replace(text.substring(start, end), " ").trim()
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return "$prefix$body$suffix"
    }

    // ───────────────────────── التنفيذ ─────────────────────────

    private fun process(input: String, trackOffsets: Boolean): NormalizedText {
        val n = input.length
        val out = Builder(n, trackOffsets)

        var i = 0
        while (i < n) {
            val first = input.codePointAt(i)
            var j = i + Character.charCount(first)
            // الحرف الأساسي مع علاماته المركّبة (تشكيل، أكسنت...) يُطبَّع كوحدة واحدة
            while (j < n) {
                val cp = input.codePointAt(j)
                if (!isCombiningMark(cp)) break
                j += Character.charCount(cp)
            }

            if (j == i + 1 && first < 0x80) {
                out.append(input[i], i)
            } else {
                val norm = Normalizer.normalize(input.substring(i, j), Normalizer.Form.NFKC)
                var k = 0
                while (k < norm.length) {
                    val c = norm[k]
                    // NFKC يحوّل الفتحة/الضمة المعزولة (ﹰ ﹲ ...) إلى مسافة + علامة؛ المسافة هنا زائدة
                    if (c == ' ' && k + 1 < norm.length && norm[k + 1].code in 0x064B..0x0652) {
                        k++
                        continue
                    }
                    out.append(c, i)
                    k++
                }
            }
            i = j
        }
        return out.build(n)
    }

    private fun isCombiningMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    /** يعيد الحرف بعد التوحيد، أو [DROP] إن كان يجب حذفه. */
    private fun mapChar(c: Char): Int {
        val code = c.code
        if (code < 0x80) return Character.toLowerCase(c).code
        return when {
            // تطويل
            code == 0x0640 -> DROP
            // تشكيل وعلامات قرآنية ورموز اتجاه غير مرئية
            code in 0x0610..0x061A -> DROP
            code == 0x061C -> DROP
            code in 0x064B..0x065F -> DROP
            code == 0x0670 -> DROP
            code in 0x06D6..0x06DC -> DROP
            code in 0x06DF..0x06E8 -> DROP
            code in 0x06EA..0x06ED -> DROP
            code == 0x00AD || code == 0xFEFF -> DROP
            code in 0x200C..0x200F -> DROP
            code in 0x202A..0x202E -> DROP
            code in 0x2060..0x2064 -> DROP
            code in 0x2066..0x2069 -> DROP
            // توحيد الألف: آ أ إ ٱ → ا
            code == 0x0622 || code == 0x0623 || code == 0x0625 || code == 0x0671 -> 0x0627
            // الألف المقصورة والياء الفارسية → ي
            code == 0x0649 || code == 0x06CC -> 0x064A
            // الكاف الفارسية → ك
            code == 0x06A9 -> 0x0643
            // التاء المربوطة → ه
            code == 0x0629 -> 0x0647
            // الأرقام الهندية → لاتينية
            code in 0x0660..0x0669 -> '0'.code + (code - 0x0660)
            code in 0x06F0..0x06F9 -> '0'.code + (code - 0x06F0)
            else -> Character.toLowerCase(c).code
        }
    }

    private class Builder(capacity: Int, private val trackOffsets: Boolean) {
        private val sb = StringBuilder(capacity)
        private var offsets = if (trackOffsets) IntArray(capacity + 16) else IntArray(0)

        fun append(c: Char, originalIndex: Int) {
            var mapped = mapChar(c)
            if (mapped == DROP) return

            if (mapped.toChar().isWhitespace()) {
                // ضغط المسافات وإهمال المسافة في بداية النص
                if (sb.isEmpty() || sb[sb.length - 1] == ' ') return
                mapped = ' '.code
            }

            sb.append(mapped.toChar())
            if (trackOffsets) {
                if (sb.length > offsets.size) offsets = offsets.copyOf(offsets.size * 2 + 16)
                offsets[sb.length - 1] = originalIndex
            }
        }

        fun build(originalLength: Int): NormalizedText {
            val result = if (trackOffsets) {
                IntArray(sb.length + 1).also {
                    System.arraycopy(offsets, 0, it, 0, sb.length)
                    it[sb.length] = originalLength
                }
            } else {
                IntArray(0)
            }
            return NormalizedText(sb.toString(), result)
        }
    }
}
