package com.mersadai.app.domain.social

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus

object SocialPostFormatter {
    fun format(item: ContentItem, promptText: String? = null): String {
        val title = item.displayTitleAr?.takeIf { it.isNotBlank() }
            ?: item.originalTitle?.takeIf { it.isNotBlank() }
            ?: item.title
        val description = item.displayDescriptionAr?.takeIf { it.isNotBlank() }
            ?: item.description?.takeIf { it.isNotBlank() }
            ?: item.originalDescription?.takeIf { it.isNotBlank() }
            ?: "لا يوجد وصف متاح."
        val url = item.url ?: item.source?.homepageUrl ?: "غير متوفر"

        return when {
            item.contentType == ContentType.PROMPT -> promptPost(
                title = title,
                description = description,
                promptText = promptText?.takeIf { it.isNotBlank() }
                    ?: item.originalDescription?.takeIf { it.isNotBlank() }
                    ?: item.description.orEmpty(),
            )
            item.classifiedFreeStatus() in setOf(
                FreeStatus.FULLY_FREE,
                FreeStatus.FREE_TIER,
                FreeStatus.FREE_CREDIT,
                FreeStatus.TEMPORARY_OFFER,
            ) -> freePerkPost(title, description, url)
            else -> technicalProjectPost(title, description, url)
        }
    }

    private fun technicalProjectPost(title: String, summary: String, url: String): String = """
        🚨 تخيل ${summary.firstSentence()} بضغطة واحدة وبدون تعقيد! 🤯

        مشروع مفتوح المصدر ومجاني تماماً:
        ✨ $title

        فكرته ببساطة:
        $summary

        كيف تستفيد منه؟
        1️⃣ افتح رابط المشروع وتأكد من متطلبات التشغيل.
        2️⃣ جرّب ميزته الأساسية على مهمة صغيرة.
        3️⃣ راجع الترخيص والتحديثات قبل دمجه في سير عملك.

        🎯 مناسب خصوصاً لـ:
        • صنّاع المحتوى والريلز (Reels / Shorts)
        • المصممين والمطورين
        • أصحاب المشاريع المستقلة

        والأجمل؟ الأداة مجانية وبدون إعلانات ومفتوحة المصدر بالكامل.

        📌 احفظ المنشور عندك لأنك ستحتاجه بالتأكيد.
        🔗 رابط المشروع والتحميل: $url

        منسق ومكتشف عبر تطبيق مِرصد AI 🚀
        #مرصد_AI #أندرويد #ذكاء_اصطناعي #FOSS
    """.trimIndent()

    private fun freePerkPost(title: String, description: String, url: String): String = """
        المجاني وصل 🔥

        لقيت لكم باقة / كنز مجاني رسمي تقدر تستغله فوراً:
        💎 $title

        المميزات:
        👀 $description
        • وصول رسمي ومجاني تماماً
        • بدون الحاجة لبطاقة بنكية أو تعقيدات

        فادخلوا جربوا واستفيدوا من الباقة قبل ما تقفل أو تتغير الشروط!

        🔗 رابط الوصول المباشر: $url
        📌 تابع كل جديد يومياً عبر تطبيق مِرصد AI 🚀
        #مرصد_AI #فرص_مجانية #ذكاء_اصطناعي
    """.trimIndent()

    private fun promptPost(title: String, description: String, promptText: String): String = """
        🎨 فكرة برومبت وتلاعب بصري تريند للريلز والصور! 📸

        الفكرة: $title
        $description

        📋 نص البرومبت الجاهز (انسخه وطبقه):
        "$promptText"

        💡 نصيحة التطبيق:
        استخدمه مع نماذج (Midjourney / Kling / Veo / Flux) واستبدل الأوصاف بين الأقواس بصورتك أو فكرتك.

        📌 احفظ البوست لتجربته، وشاركنا نتيجتك!
        عبر مكتبة برومبتات مِرصد AI 🚀
        #مرصد_AI #برومبت #ذكاء_اصطناعي #خدع_بصرية
    """.trimIndent()

    private fun String.firstSentence(): String =
        takeWhile { it != '.' && it != '\n' }.trim().ifBlank { "تنجز مهمتك التقنية بسهولة" }
}