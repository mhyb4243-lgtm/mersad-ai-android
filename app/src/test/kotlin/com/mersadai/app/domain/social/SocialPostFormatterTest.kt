package com.mersadai.app.domain.social

import com.mersadai.app.domain.model.ContentItem
import com.mersadai.app.domain.model.ContentType
import com.mersadai.app.domain.model.FreeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPostFormatterTest {
    @Test
    fun formatsPromptPostAndKeepsReadyPromptText() {
        val post = SocialPostFormatter.format(
            item(contentType = ContentType.PROMPT, description = "وصف الفكرة"),
            promptText = "صورة سينمائية [المشهد]",
        )

        assertTrue(post.contains("🎨 فكرة برومبت"))
        assertTrue(post.contains("\"صورة سينمائية [المشهد]\""))
        assertTrue(post.contains("وصف الفكرة"))
    }

    @Test
    fun formatsFreePerkWithDescriptionAndDirectLink() {
        val post = SocialPostFormatter.format(
            item(
                freeStatus = FreeStatus.FREE_CREDIT,
                description = "رصيد مجاني للتجربة",
                url = "https://example.com/perk",
            ),
        )

        assertTrue(post.contains("المجاني وصل 🔥"))
        assertTrue(post.contains("رصيد مجاني للتجربة"))
        assertFalse(post.contains("https://example.com/perk"))
        assertTrue(
            SocialPostFormatter.formatFirstComment(item(url = "https://example.com/perk"))
                .contains("https://example.com/perk"),
        )
        assertTrue(
            SocialPostFormatter.format(item(freeStatus = FreeStatus.FULLY_FREE))
                .contains("المجاني وصل 🔥"),
        )
    }

    @Test
    fun formatsTechnicalProjectWithPracticalSteps() {
        val post = SocialPostFormatter.format(
            item(
                contentType = ContentType.ANDROID_PROJECT,
                description = "يساعدك على إدارة ملفاتك.",
            ),
        )

        assertTrue(post.contains("مشروع مفتوح المصدر"))
        assertTrue(post.contains("1️⃣"))
        assertFalse(post.contains("https://"))
        assertFalse(post.contains("مِرصد AI"))
        assertFalse(post.contains("#مرصد_AI"))
        assertTrue(post.endsWith("🔗 رابط الأداة والتفاصيل في أول تعليق 👇\n#محمد_ابوهادي #صناعة_محتوى #ذكاء_اصطناعي #تصميم"))
    }

    @Test
    fun formatsFirstCommentWithDirectLinkAndTelegramChannel() {
        val comment = SocialPostFormatter.formatFirstComment(
            item(url = "https://example.com/tool"),
        )

        assertEquals(
            "📌 روابط الأداة والمصدر المباشر:\n" +
                "🔗 https://example.com/tool\n\n" +
                "📢 لمتابعة أحدث الأدوات والملحقات والشروحات اليومية، انضم لقناتنا على تيليجرام:\n" +
                "👉 https://t.me/hootnewss\n\n" +
                "#محمد_ابوهادي #تصوير #فوتوشوب",
            comment,
        )
    }

    private fun item(
        contentType: ContentType = ContentType.AI_TOOL,
        freeStatus: FreeStatus = FreeStatus.UNKNOWN,
        description: String? = null,
        url: String? = null,
    ) = ContentItem(
        id = "test-item",
        title = "أداة تجريبية",
        contentType = contentType,
        freeStatus = freeStatus,
        description = description,
        url = url,
        createdAt = 1L,
        updatedAt = 1L,
    )
}