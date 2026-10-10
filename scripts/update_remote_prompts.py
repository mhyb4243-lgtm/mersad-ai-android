#!/usr/bin/env python3
"""Refresh the public prompt feed from Hugging Face and verify official free plans."""

from __future__ import annotations

import argparse
import email.utils
import hashlib
import html
import json
import re
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
FEED_PATH = ROOT / "remote_prompts.json"
DATASET = "fka/prompts.chat"
DATASET_ROWS_URL = "https://datasets-server.huggingface.co/rows"
SECTION_ID = "ai-deals-and-trials"
SECTION_TITLE = "عروض واشتراكات الذكاء الاصطناعي المجانية (AI Deals & Trials)"
PAGE_SIZE = 100
ACTIVE_DEAL_DAYS = 30
DEAL_CATEGORY_ID = "ai-deals"
DEAL_CATEGORY_NAME = "🎁 عروض واشتراكات مجانية (AI Deals & Trials)"
FACTVERSE_SECTION_ID = "factverse-science"
FACTVERSE_SECTION_TITLE = "🌌 FactVerse (Science, Future & AI)"
FACTVERSE_CATEGORY_ID = "factverse"
FACTVERSE_MAX_ITEMS = 70
FACTVERSE_RETENTION_DAYS = 180
PHOTO_MANIPULATION_SECTION_ID = "PHOTO_MANIPULATION"
PHOTO_MANIPULATION_SECTION_TITLE = "📸 صورة وفوتوغرافي — خداع بصري / تلاعب"
PHOTO_MANIPULATION_CATEGORY_ID = "visual-tricks"
PHOTO_MANIPULATION_CATEGORY_NAME = "📸 صورة وفوتوغرافي"
PHOTO_MANIPULATION_MAX_ITEMS = 60
PHOTO_MANIPULATION_RETENTION_DAYS = 45
PHOTO_MANIPULATION_QUERIES = (
    (
        "optical illusion forced perspective photography",
        "خدعة المنظور القسري العملاق",
        "صورة فوتوغرافية توهم بضخامة عنصر قريب أو بُعد عنصر بعيد عبر محاذاة دقيقة بين مقدمة المشهد وخلفيته.",
    ),
    (
        "surreal photo manipulation double exposure",
        "دمج تعريض مزدوج بين البورتريه والغابة",
        "بورتريه سريالي يمزج ملامح الوجه مع طبقات غابة بتعريض مزدوج متوازن يحافظ على وضوح الشخصية.",
    ),
    (
        "tilt shift miniature dioramas photorealistic",
        "تأثير تيلت شيفت للعالم المصغر",
        "مشهد واقعي يبدو كأنه مجسم مصغر باستخدام منظور مرتفع وعمق ميدان ضحل وانتقائية التركيز.",
    ),
    (
        "levitation surreal portrait cinematic",
        "بورتريه سريالي لشخصية تحلّق",
        "بورتريه سينمائي يوحي بالتحليق مع ظلال واتزان بصري واقعيين يثبتان الشخصية داخل المكان.",
    ),
)
PHOTO_MANIPULATION_TEMPLATES = (
    (
        "photo-manipulation-forced-perspective",
        "خدعة المنظور القسري العملاق",
        "لقطة فوتوغرافية تجعل شخصاً يبدو كأنه يمسك مبنى عملاقاً بين أصابعه عبر محاذاة المنظور القسري.",
        "Create a photorealistic forced-perspective photograph in which [SUBJECT] appears to hold a monumental [LANDMARK] between two fingers. Precisely align the foreground hand close to the camera with the distant landmark on the same visual axis; use a low camera angle and deliberate camera-to-subject spacing so the scale illusion reads instantly while anatomy and architecture remain undistorted. Shoot on a 35mm lens at f/8 for enough depth to keep both the hand and landmark convincingly sharp. Use warm late-afternoon side light, a soft fill bounce, realistic contact shadows, atmospheric depth, natural skin texture, and restrained editorial color grading. Keep the horizon level, preserve believable perspective convergence, and avoid extra fingers, duplicate subjects, text, logos, and watermarks.",
    ),
    (
        "photo-manipulation-double-exposure",
        "دمج تعريض مزدوج بين البورتريه والغابة",
        "بورتريه تحريري يدمج صورة الشخصية مع غابة ضبابية بتعريض مزدوج وطبقات ضوئية متوازنة.",
        "Create a refined surreal double-exposure portrait of [SUBJECT], blending a misty old-growth forest into the silhouette and facial tones without obscuring recognizable features. Photograph the portrait on an 85mm lens at f/2 with a clean three-quarter profile, then composite the forest canopy and trunks as a controlled secondary exposure contained within the silhouette; retain natural skin highlights and smooth tonal transitions. Use a large soft key light at 45 degrees, subtle cool rim light, and deep but detailed shadows. Balance both exposures, preserve fine hair edges, add restrained atmospheric haze, and use a cinematic forest-green and neutral-skin palette. No hard cutout halos, extra faces, text, logos, or watermarks.",
    ),
    (
        "photo-manipulation-tilt-shift",
        "تأثير تيلت شيفت للعالم المصغر",
        "منظر مدينة واقعي بتأثير تيلت شيفت يوحي بأنه مجسم ديوراما مصغر مصنوع يدوياً.",
        "Photograph a photorealistic miniature-diorama illusion of [CITY OR SCENE] from an elevated oblique viewpoint. Use a 35mm lens with a controlled tilt-shift focus plane: keep a narrow horizontal band of miniature buildings crisp while foreground and distant background fall into smooth optical blur. Favor a high three-quarter camera angle, precise scale cues, tiny believable people and vehicles, and realistic model-making detail rather than toy-like exaggeration. Light the scene with soft overcast daylight and a gentle warm directional accent, preserving contact shadows and consistent reflections. Apply subtle saturation and natural color, maintain architectural geometry, and avoid artificial blur halos, text, logos, and watermarks.",
    ),
    (
        "photo-manipulation-levitation",
        "بورتريه سريالي لشخصية تحلّق",
        "بورتريه سينمائي لشخص يطفو فوق أرضية الاستوديو مع ظلال وإضاءة واتزان فيزيائي مقنع.",
        "Create a cinematic photorealistic levitation portrait of [SUBJECT] floating calmly above a dark studio floor, with clothing and hair responding subtly to gravity and air movement. Frame a clean full-body composition on an 85mm lens at f/2.8 from a slightly low three-quarter angle; keep the subject's center of mass believable and leave visible negative space beneath the feet. Use a broad diffused key light from camera left, a cool rim light behind the shoulders, and a faint floor bounce; add a soft, correctly offset floor shadow and restrained atmospheric haze to anchor the scene. Preserve natural anatomy, fabric detail, and realistic motion cues. Hide all support rigs and remove extra limbs, text, logos, and watermarks.",
    ),
)
REDDIT_FEEDS = {
    "reddit-freebies": ("r/Freebies", "https://www.reddit.com/r/Freebies/new/.rss?limit=100"),
    "reddit-ai": ("r/ArtificialInteligence", "https://www.reddit.com/r/ArtificialInteligence/new/.rss?limit=100"),
    "reddit-openai": ("r/OpenAI", "https://www.reddit.com/r/OpenAI/new/.rss?limit=100"),
}
FACTVERSE_FEEDS = {
    "factverse-futurology": ("r/Futurology", "https://www.reddit.com/r/Futurology/new/.rss?limit=100"),
    "factverse-sciencedaily": ("ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml"),
    "factverse-singularity-hub": ("Singularity Hub", "https://singularityhub.com/feed/"),
}
GITHUB_DEALS_URL = "https://raw.githubusercontent.com/cheahjs/free-llm-api-resources/main/README.md"
DEAL_KEYWORDS = re.compile(
    r"free[\s-]+(?:trial|credits?|tokens?|month|plan|tier|api)|(?:trial|credits?|tokens?)[\s-]+free|"
    r"\$\s?\d+\s?(?:usd\s?)?(?:free\s?)?(?:credits?|credit)|promo(?:tional)?\s+code|coupon|discount",
    re.IGNORECASE,
)
PROVIDER_NAMES = (
    "Runway", "Kling", "ElevenLabs", "Leonardo", "OpenAI", "Cursor", "Anthropic", "Claude",
    "Google", "Gemini", "Perplexity", "Mistral", "Groq", "Cohere", "Hugging Face",
    "Replicate", "Pika", "Luma", "Suno", "Udio", "Midjourney", "Canva", "DeepInfra",
    "Together AI", "Cerebras", "OpenRouter", "Cloudflare", "Fireworks", "SambaNova",
    "SiliconFlow", "NVIDIA", "GitHub Copilot",
)
CREATOR_TOOL_PATTERN = re.compile(r"\b(?:runway|kling(?:\s+ai)?|pika|elevenlabs|leonardo(?:\s+ai)?|suno)\b", re.IGNORECASE)
PROMO_CODE_PATTERN = re.compile(r"\b(?:promo(?:tional)?\s+code|coupon\s+code|code)\s*[:=-]?\s*([A-Z0-9][A-Z0-9_-]{3,19})\b", re.IGNORECASE)
FACTVERSE_SOURCE_IDS = frozenset(FACTVERSE_FEEDS)

CREATOR_TOOL_OFFERS = (
    ("runway-free-tier", "Runway", "Runway Free", "https://runwayml.com/pricing", "video-generation"),
    ("kling-free-tier", "Kling AI", "Kling AI Free", "https://klingai.com/membership", "video-generation"),
    ("pika-free-tier", "Pika", "Pika Free", "https://pika.art/pricing", "video-generation"),
    ("elevenlabs-free-tier", "ElevenLabs", "ElevenLabs Free", "https://elevenlabs.io/pricing", "voice-generation"),
    ("leonardo-free-tier", "Leonardo AI", "Leonardo AI Free", "https://leonardo.ai/pricing", "image-generation"),
    ("suno-free-tier", "Suno", "Suno Free", "https://suno.com/pricing", "music-generation"),
)
OFFER_DETAILS = {
    "runway-free-tier": (
        "تتوفر صفحة التسجيل الرسمية، لكن صفحة الأسعار التي أمكن التحقق منها لا تعرض حصة مجانية ثابتة أو رصيداً متجدداً؛ "
        "تحقق من الرصيد الظاهر في حسابك قبل بدء التوليد.",
        "لم تُنشر حصة مجانية ثابتة قابلة للتحقق في صفحة الأسعار العامة؛ الرصيد الفعلي يظهر في الحساب.",
    ),
    "kling-free-tier": (
        "تعرض صفحة العضوية الخطط عبر واجهة ديناميكية ولا تؤكد حصة يومية ثابتة قابلة للتحقق في النص العام؛ "
        "تظهر الأرصدة والإتاحة الحالية بعد تسجيل الدخول.",
        "لا توجد حصة يومية ثابتة منشورة يمكن تأكيدها من الصفحة العامة؛ افحص رصيد الحساب.",
    ),
    "pika-free-tier": (
        "صفحة الأسعار الحالية تعرض تعبئة مدفوعة بسعر 60 رصيداً لكل دولار ولا تعرض حصة مجانية شهرية متجددة؛ "
        "تحقق من أي رصيد تسجيل جديد داخل الحساب قبل الاستخدام.",
        "صفحة الأسعار تعرض 60 رصيد تعبئة لكل دولار؛ لا تعرض حصة مجانية شهرية ثابتة.",
    ),
    "elevenlabs-free-tier": (
        "الخطة المجانية تشمل 10,000 رصيد شهرياً وميزات الصوت الأساسية؛ الترخيص التجاري غير مشمول بالخطة المجانية.",
        "10,000 رصيد شهرياً؛ الاستخدام التجاري غير مشمول بالخطة المجانية.",
    ),
    "leonardo-free-tier": (
        "صفحة الأسعار الرسمية لا تُظهر في العرض العام حصة يومية ثابتة قابلة للتحقق؛ تختلف الأرصدة باختلاف الحساب والميزة.",
        "لم تُنشر حصة يومية ثابتة قابلة للتحقق؛ راجع رصيد الحساب وصفحة الخطة.",
    ),
    "suno-free-tier": (
        "الخطة المجانية وإعادة تعبئة الرصيد قد تختلف حسب الحساب؛ صفحة الأسعار العامة لا تعرض حصة يومية ثابتة قابلة للتحقق. "
        "الاستخدام التجاري يتطلب خطة تسمح بذلك.",
        "لم تُنشر حصة يومية ثابتة قابلة للتحقق في صفحة الأسعار العامة؛ راجع الرصيد داخل الحساب.",
    ),
    "chatgpt-free-tier": (
        "تتضمن الخطة المجانية وصولاً محدوداً إلى النماذج والميزات. حد الرسائل يتغير بحسب النموذج والضغط "
        "ويُعرض داخل الحساب؛ لا تعلن OpenAI حصة يومية ثابتة لجميع المستخدمين.",
        "حدود رسائل ديناميكية بحسب النموذج والضغط؛ لا يوجد رقم يومي موحد معلن.",
    ),
    "claude-free-tier": (
        "الخطة المجانية محدودة الاستخدام وتُعاد الحصة ضمن نافذة جلسة متحركة من خمس ساعات؛ "
        "لا يوجد عدد رسائل ثابت لأن الاستهلاك يتغير بحسب طول المحادثة والنموذج والميزات.",
        "إعادة ضبط ضمن نافذة متحركة كل 5 ساعات؛ لا يوجد عدد رسائل موحد.",
    ),
    "perplexity-free-tier": (
        "الخطة المجانية توفر البحث الأساسي دون اشتراك؛ ميزات Pro Search لها حصة محدودة تتغير حسب الخطة "
        "والحساب، ويعرض التطبيق الرصيد الحالي.",
        "البحث الأساسي متاح مجاناً؛ حصة Pro Search محدودة وديناميكية.",
    ),
    "groq-free-tier": (
        "واجهة Groq API تتيح مستوى مطور مجاني بحدود طلبات ورموز يومية تختلف حسب النموذج وتتغير بمرور الوقت؛ "
        "تعرض لوحة Limits الحصة الدقيقة لكل نموذج بعد تسجيل الدخول.",
        "مستوى مطور مجاني؛ حدود RPM وRPD ورموز مختلفة لكل نموذج وتُعرض مباشرة في لوحة الحساب.",
    ),
}
OFFICIAL_TOOL_OFFERS = (
    ("chatgpt-free-tier", "OpenAI", "ChatGPT Free", "https://chatgpt.com/", "chat-assistant"),
    ("claude-free-tier", "Claude", "Claude Free", "https://claude.ai/", "chat-assistant"),
    ("perplexity-free-tier", "Perplexity", "Perplexity Free", "https://www.perplexity.ai/", "research"),
    ("groq-free-tier", "Groq", "Groq Developer Free", "https://console.groq.com/keys", "ai-api"),
)
OFFERS = [
    {
        "id": offer_id,
        "title": title,
        "description": f"خطة مجانية لصناع المحتوى عبر {provider}؛ تختلف الأرصدة والحدود حسب المنطقة وشروط المزود الحالية.",
        "url": url,
        "deal_url": url,
        "provider": provider,
        "deal_type": "free plan",
        "promo_code": None,
        "category_id": "free-perks",
        "source_type": "official",
        "free_status": "FREE_TIER",
        "free_limit": "راجع صفحة الأسعار الرسمية لمعرفة الرصيد والحدود الحالية.",
        "requires_account": True,
        "requires_payment_card": None,
        "tags": [provider.lower().replace(" ", "-"), "creator-tools", media_type, "official"],
        "is_active": True,
    }
    for offer_id, provider, title, url, media_type in CREATOR_TOOL_OFFERS
] + [
    {
        "id": offer_id,
        "title": title,
        "description": OFFER_DETAILS[offer_id][0],
        "url": url,
        "deal_url": url,
        "provider": provider,
        "deal_type": "free plan",
        "promo_code": None,
        "category_id": "free-perks",
        "source_type": "official",
        "free_status": "FREE_TIER",
        "free_limit": OFFER_DETAILS[offer_id][1],
        "requires_account": True,
        "requires_payment_card": None,
        "tags": [provider.lower().replace(" ", "-"), "free-tier", media_type, "official"],
        "is_active": True,
    }
    for offer_id, provider, title, url, media_type in OFFICIAL_TOOL_OFFERS
]
for offer in OFFERS:
    if offer["id"] in OFFER_DETAILS:
        offer["description"], offer["free_limit"] = OFFER_DETAILS[offer["id"]]


def fetch_json(
    url: str,
    user_agent: str = "MersadAI-PromptFeed/1.0",
    headers: dict | None = None,
) -> dict:
    request_headers = {"User-Agent": user_agent, "Accept": "application/json"}
    if headers:
        request_headers.update(headers)
    request = Request(url, headers=request_headers)
    with urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected HTTP status {response.status} from {url}")
        return json.load(response)


def fetch_text(url: str, accept: str = "text/plain, application/atom+xml, application/rss+xml") -> str:
    request = Request(url, headers={"User-Agent": "MersadAI-DealsBot/1.0 (public feed)", "Accept": accept})
    with urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected HTTP status {response.status} from {url}")
        return response.read().decode("utf-8", errors="replace")


def _plain_text(value: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", value))).strip()


def _published_timestamp(value: str, fallback: int) -> int:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        try:
            parsed = email.utils.parsedate_to_datetime(value)
        except (TypeError, ValueError):
            return fallback
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp() * 1000)


def _provider_name(text: str) -> str:
    for provider in PROVIDER_NAMES:
        if re.search(rf"\b{re.escape(provider)}\b", text, re.IGNORECASE):
            return provider
    return "Community deal"


def _is_creator_tool_deal(item: dict) -> bool:
    searchable = " ".join(str(item.get(key) or "") for key in ("title", "description", "provider", "deal_type", "free_limit"))
    return bool(CREATOR_TOOL_PATTERN.search(searchable) and DEAL_KEYWORDS.search(searchable))


def _deal_type(text: str) -> str:
    if re.search(r"promo|coupon|discount", text, re.IGNORECASE):
        return "discount code"
    if re.search(r"trial|month free", text, re.IGNORECASE):
        return "free trial"
    if re.search(r"credit|token", text, re.IGNORECASE):
        return "free credits"
    return "free plan"


def _deal_record(
    *,
    title: str,
    description: str,
    deal_url: str,
    source_id: str,
    source_name: str,
    source_url: str,
    published_at: int,
    now_ms: int,
) -> dict:
    combined_text = f"{title} {description}"
    provider = _provider_name(combined_text)
    canonical = urlparse(deal_url)
    identity = f"{provider.lower()}|{canonical.netloc.lower()}{canonical.path.rstrip('/')}"
    deal_id = "community-" + hashlib.sha256(identity.encode("utf-8")).hexdigest()[:20]
    age_ms = now_ms - published_at
    return {
        "id": deal_id,
        "title": title[:240],
        "description": description[:700] or f"عرض رُصد في {source_name}.",
        "provider": provider,
        "deal_type": _deal_type(combined_text),
        "promo_code": (PROMO_CODE_PATTERN.search(combined_text).group(1) if PROMO_CODE_PATTERN.search(combined_text) else None),
        "deal_url": deal_url,
        "url": deal_url,
        "source_url": source_url,
        "source_id": source_id,
        "source_name": source_name,
        "source_type": "community",
        "category_id": DEAL_CATEGORY_ID,
        "category_name": DEAL_CATEGORY_NAME,
        "published_at": published_at,
        "verified_date": datetime.fromtimestamp(now_ms / 1000, timezone.utc).date().isoformat(),
        "is_active": 0 <= age_ms <= ACTIVE_DEAL_DAYS * 24 * 60 * 60 * 1000,
        "free_status": "FREE_CREDIT" if re.search(r"credit|token", combined_text, re.IGNORECASE) else "TEMPORARY_OFFER",
        "tags": ["ai-deal", "community", source_id],
    }


def parse_rss_deals(xml_body: str, source_id: str, source_name: str, now_ms: int) -> list[dict]:
    root = ET.fromstring(xml_body)
    deals = []
    for entry in root.iter():
        if entry.tag.rsplit("}", 1)[-1].lower() not in {"entry", "item"}:
            continue
        fields = {}
        entry_links = []
        for child in entry.iter():
            key = child.tag.rsplit("}", 1)[-1].lower()
            if key == "link":
                link = child.attrib.get("href") or (child.text or "").strip()
                if link:
                    entry_links.append(link)
            elif key in {"title", "summary", "description", "content", "published", "updated", "pubdate", "id", "guid"}:
                fields[key] = " ".join(filter(None, (fields.get(key), " ".join(child.itertext())))).strip()
        title = _plain_text(fields.get("title", ""))
        description = _plain_text(" ".join(fields.get(key, "") for key in ("summary", "description", "content")))
        searchable = f"{title} {description}"
        if not title or not DEAL_KEYWORDS.search(searchable) or not CREATOR_TOOL_PATTERN.search(searchable):
            continue
        post_url = next((url for url in entry_links if url.startswith("http")), "")
        entry_markup = html.unescape(ET.tostring(entry, encoding="unicode"))
        external_links = re.findall(r'''href=["'](https?://[^"']+)["']''', entry_markup)
        deal_url = next(
            (
                html.unescape(url)
                for url in external_links
                if not any(domain in urlparse(url).netloc.lower() for domain in ("reddit.com", "redd.it"))
            ),
            post_url,
        )
        if not deal_url:
            continue
        published = next((fields[key] for key in ("published", "updated", "pubdate") if fields.get(key)), "")
        published_at = _published_timestamp(published, now_ms)
        deals.append(
            _deal_record(
                title=title,
                description=description,
                deal_url=deal_url,
                source_id=source_id,
                source_name=source_name,
                source_url=post_url or deal_url,
                published_at=published_at,
                now_ms=now_ms,
            ),
        )
    return deals


def parse_science_rss(xml_body: str, source_id: str, source_name: str, now_ms: int) -> list[dict]:
    if source_id not in FACTVERSE_SOURCE_IDS:
        raise ValueError(f"Unknown FactVerse source: {source_id}")
    root = ET.fromstring(xml_body)
    articles = []
    for entry in root.iter():
        if entry.tag.rsplit("}", 1)[-1].lower() not in {"entry", "item"}:
            continue
        fields = {}
        entry_links = []
        for child in entry.iter():
            key = child.tag.rsplit("}", 1)[-1].lower()
            if key == "link":
                rel = child.attrib.get("rel", "alternate").lower()
                link = child.attrib.get("href") or (child.text or "").strip()
                if link and rel == "alternate":
                    entry_links.append(link)
            elif key in {"title", "summary", "description", "content", "published", "updated", "pubdate", "id", "guid"}:
                fields[key] = " ".join(filter(None, (fields.get(key), " ".join(child.itertext())))).strip()
        title = _plain_text(fields.get("title", ""))
        if not title:
            continue
        url = next((link for link in entry_links if link.startswith("https://")), "")
        if not url:
            continue
        description = _plain_text(" ".join(fields.get(key, "") for key in ("summary", "description", "content")))
        published = next((fields[key] for key in ("published", "updated", "pubdate") if fields.get(key)), "")
        published_at = _published_timestamp(published, now_ms)
        guid = fields.get("guid") or fields.get("id") or url
        external_id = f"{source_id}-{hashlib.sha256(f'{source_id}|{guid}|{url}'.encode('utf-8')).hexdigest()[:20]}"
        articles.append(
            {
                "id": external_id,
                "title": title[:240],
                "description": description[:1200],
                "url": url,
                "source_id": source_id,
                "source_name": source_name,
                "source_url": url,
                "category_id": FACTVERSE_CATEGORY_ID,
                "language": "en",
                "published_at": published_at,
                "tags": ["FactVerse", "science", "future", "AI"],
            },
        )
    return articles


def discover_factverse_articles(now_ms: int) -> tuple[list[dict], int]:
    discovered = []
    successful_sources = 0
    for source_id, (source_name, url) in FACTVERSE_FEEDS.items():
        try:
            discovered.extend(
                parse_science_rss(
                    fetch_text(url, "application/atom+xml, application/rss+xml, application/xml"),
                    source_id,
                    source_name,
                    now_ms,
                ),
            )
            successful_sources += 1
        except (HTTPError, URLError, TimeoutError, ET.ParseError, RuntimeError) as error:
            print(f"Warning: could not read FactVerse source {source_name}: {error}", file=sys.stderr)
    unique = {article["id"]: article for article in discovered}
    return list(unique.values()), successful_sources


def parse_lexica_illusion_prompts(payload: dict, query: str, now_ms: int) -> list[dict]:
    query_details = next((details for details in PHOTO_MANIPULATION_QUERIES if details[0] == query), None)
    if query_details is None:
        raise ValueError(f"Unknown photo-manipulation query: {query}")
    _, title, description = query_details
    results = payload.get("images", [])
    if not isinstance(results, list):
        return []
    prompts = []
    for result in results:
        if not isinstance(result, dict):
            continue
        prompt_text = str(result.get("prompt") or "").strip()
        if not prompt_text or len(prompt_text) > 12000:
            continue
        external_id = str(result.get("id") or hashlib.sha256(prompt_text.encode("utf-8")).hexdigest()[:20])
        prompts.append(
            _photo_manipulation_prompt(
                item_id=f"lexica-illusion-{external_id}",
                title=title,
                description=description,
                prompt=prompt_text,
                url="https://lexica.art/?q=" + urlencode({"q": query})[2:],
                source_tag="Lexica",
                published_at=now_ms,
            ),
        )
    return prompts


def parse_reddit_illusion_prompts(payload: dict, now_ms: int) -> list[dict]:
    posts = payload.get("data", {}).get("children", [])
    if not isinstance(posts, list):
        return []
    prompts = []
    for post_entry in posts:
        post = post_entry.get("data") if isinstance(post_entry, dict) else None
        if not isinstance(post, dict):
            continue
        prompt_text = str(post.get("selftext") or "").strip()
        if not prompt_text or prompt_text in {"[deleted]", "[removed]"} or len(prompt_text) > 12000:
            continue
        if not re.search(r"illusion|surreal|perspective|double[\s-]?exposure|tilt[\s-]?shift|levitat", prompt_text, re.IGNORECASE):
            continue
        post_id = str(post.get("id") or hashlib.sha256(prompt_text.encode("utf-8")).hexdigest()[:20])
        title = _plain_text(str(post.get("title") or "Prompt illusion surrealism"))
        permalink = str(post.get("permalink") or "")
        published_at = int(float(post.get("created_utc", now_ms / 1000)) * 1000)
        prompts.append(
            _photo_manipulation_prompt(
                item_id=f"reddit-illusion-{post_id}",
                title=f"إلهام خداع بصري من Reddit: {title}",
                description="برومبت مجتمعي مستوحى من منشور Reddit؛ افتح رابط المنشور لمراجعة السياق الأصلي.",
                prompt=prompt_text,
                url=urljoin("https://www.reddit.com", permalink) if permalink else "https://www.reddit.com/search/?q=flair%3APrompt%20illusion%20surrealism",
                source_tag="Reddit",
                published_at=published_at,
            ),
        )
    return prompts


def _photo_manipulation_prompt(
    *,
    item_id: str,
    title: str,
    description: str,
    prompt: str,
    url: str,
    source_tag: str,
    published_at: int,
) -> dict:
    return {
        "id": item_id,
        "title": title,
        "description": description,
        "prompt_type": "image-generation",
        "category_id": PHOTO_MANIPULATION_CATEGORY_ID,
        "category_name": PHOTO_MANIPULATION_CATEGORY_NAME,
        "prompt": prompt,
        "language": "en",
        "tags": [PHOTO_MANIPULATION_CATEGORY_NAME, "خداع بصري / تلاعب", "optical-illusion", "photo-manipulation", source_tag],
        "url": url,
        "published_at": published_at,
    }


def discover_photo_manipulation_prompts(now_ms: int) -> tuple[list[dict], int]:
    discovered = []
    successful_sources = 0
    for query, _, _ in PHOTO_MANIPULATION_QUERIES:
        try:
            query_url = "https://lexica.art/api/v1/search?" + urlencode({"q": query})
            discovered.extend(
                parse_lexica_illusion_prompts(
                    fetch_json(
                        query_url,
                        headers={
                            "Referer": "https://lexica.art/",
                            "User-Agent": "Mozilla/5.0 (compatible; MersadAI/1.0; +https://github.com/mhyb4243-lgtm/mersad-ai-android)",
                        },
                    ),
                    query,
                    now_ms,
                ),
            )
            successful_sources += 1
        except (HTTPError, URLError, TimeoutError, RuntimeError, ValueError) as error:
            print(f"Warning: could not read Lexica photo-manipulation search {query!r}: {error}", file=sys.stderr)
    reddit_url = "https://www.reddit.com/search.json?" + urlencode(
        {"q": "flair:Prompt illusion surrealism", "sort": "new", "limit": 50},
    )
    try:
        discovered.extend(
            parse_reddit_illusion_prompts(fetch_json(reddit_url, user_agent="MersadAI/1.0"), now_ms),
        )
        successful_sources += 1
    except (HTTPError, URLError, TimeoutError, RuntimeError, ValueError) as error:
        print(f"Warning: could not read Reddit illusion search: {error}", file=sys.stderr)
    unique = {prompt["id"]: prompt for prompt in discovered}
    return list(unique.values()), successful_sources


def build_photo_manipulation_section(
    previous_sections: list[dict],
    discovered: list[dict],
    now_ms: int,
) -> dict:
    existing = next(
        (section for section in previous_sections if section.get("id") == PHOTO_MANIPULATION_SECTION_ID),
        {},
    )
    cutoff = now_ms - PHOTO_MANIPULATION_RETENTION_DAYS * 24 * 60 * 60 * 1000
    prompts = {
        prompt["id"]: prompt
        for prompt in existing.get("items", [])
        if prompt.get("id")
        and (
            str(prompt["id"]).startswith("photo-manipulation-")
            or int(prompt.get("published_at", 0)) >= cutoff
        )
    }
    prompts.update({prompt["id"]: prompt for prompt in discovered if prompt.get("id")})
    for item_id, title, description, prompt_text in PHOTO_MANIPULATION_TEMPLATES:
        previous = prompts.get(item_id, {})
        prompts[item_id] = _photo_manipulation_prompt(
            item_id=item_id,
            title=title,
            description=description,
            prompt=prompt_text,
            url="https://lexica.art/",
            source_tag="MersadAI",
            published_at=int(previous.get("published_at", now_ms)),
        )
    ordered = sorted(prompts.values(), key=lambda prompt: int(prompt.get("published_at", 0)), reverse=True)
    return {
        "id": PHOTO_MANIPULATION_SECTION_ID,
        "title": PHOTO_MANIPULATION_SECTION_TITLE,
        "items": ordered[:PHOTO_MANIPULATION_MAX_ITEMS],
    }


def build_factverse_section(
    previous_sections: list[dict],
    discovered: list[dict],
    now_ms: int,
) -> dict:
    existing = next((section for section in previous_sections if section.get("id") == FACTVERSE_SECTION_ID), {})
    cutoff = now_ms - FACTVERSE_RETENTION_DAYS * 24 * 60 * 60 * 1000
    articles = {
        article["id"]: enrich_factverse_article(article)
        for article in existing.get("items", [])
        if article.get("id") and int(article.get("published_at", 0)) >= cutoff
    }
    articles.update({article["id"]: enrich_factverse_article(article) for article in discovered})
    ordered = sorted(articles.values(), key=lambda article: int(article.get("published_at", 0)), reverse=True)
    return {"id": FACTVERSE_SECTION_ID, "title": FACTVERSE_SECTION_TITLE, "items": ordered[:FACTVERSE_MAX_ITEMS]}


def enrich_factverse_article(article: dict) -> dict:
    title = _plain_text(str(article.get("title") or "Science discovery"))
    summary = _plain_text(str(article.get("description") or title))
    visual_prompt = article.get("visual_prompt") or (
        f'Create a high-contrast sci-tech split-screen editorial image about "{title}". '
        f'LEFT PANEL: accurately visualize the reported scientific mechanism: "{summary}". '
        "RIGHT PANEL: show a plausible, grounded future impact of the same discovery. "
        "Use a precise vertical split with one shared subject bridging both sides, midnight-navy shadows, "
        "electric-cyan and amber highlights, soft cinematic lighting, crisp rim light, controlled volumetric haze, "
        "photoreal materials, 35mm lens perspective, premium science-magazine art direction. "
        "No text, logos, watermark, or unsupported scientific claims."
    )
    reels_script = article.get("reels_script") or (
        f'Scene 1 (0-10s): Hook & Visual — Open on a striking close-up of "{title}", '
        "then reveal the central visual contrast with a controlled push-in. Voiceover: "
        '"What if this discovery changes how we understand the world?"\n'
        f'Scene 2 (10-20s): Science Breakdown — Explain the reported finding plainly: "{summary}". '
        "Use one evidence-led visualization and a steady camera. Voiceover: "
        '"Here is the science, and what researchers have actually found."\n'
        "Scene 3 (20-30s): Future Impact & Outro — Pull back to a grounded view of a possible application. "
        'End on a clean hero frame. Voiceover: "The next chapter is still being written."\n'
        'End card: "FactVerse • Explore The Future"'
    )
    return {**article, "visual_prompt": visual_prompt, "reels_script": reels_script}


def parse_github_deals(markdown: str, now_ms: int) -> list[dict]:
    deals = []
    for line in markdown.splitlines():
        clean_line = _plain_text(line)
        if not DEAL_KEYWORDS.search(clean_line) and not re.search(r"free.{0,30}api|api.{0,30}free", clean_line, re.IGNORECASE):
            continue
        if not CREATOR_TOOL_PATTERN.search(clean_line):
            continue
        links = re.findall(r"\[([^\]]+)\]\((https?://[^)\s]+)\)", line)
        for label, url in links:
            if urlparse(url).netloc.lower().endswith("github.com"):
                continue
            title = f"{label.strip()} free API access"
            deals.append(
                _deal_record(
                    title=title,
                    description=clean_line,
                    deal_url=url,
                    source_id="github-free-llm-api-resources",
                    source_name="GitHub: free-llm-api-resources",
                    source_url=GITHUB_DEALS_URL,
                    published_at=now_ms,
                    now_ms=now_ms,
                ),
            )
            break
    return deals[:100]


def discover_community_deals(now_ms: int) -> tuple[list[dict], int]:
    discovered = []
    successful_sources = 0
    for source_id, (source_name, url) in REDDIT_FEEDS.items():
        try:
            discovered.extend(parse_rss_deals(fetch_text(url, "application/atom+xml, application/rss+xml"), source_id, source_name, now_ms))
            successful_sources += 1
        except (HTTPError, URLError, TimeoutError, ET.ParseError, RuntimeError) as error:
            print(f"Warning: could not read {source_name} RSS: {error}", file=sys.stderr)
    try:
        discovered.extend(parse_github_deals(fetch_text(GITHUB_DEALS_URL), now_ms))
        successful_sources += 1
    except (HTTPError, URLError, TimeoutError, RuntimeError) as error:
        print(f"Warning: could not read GitHub community offers: {error}", file=sys.stderr)
    unique = {deal["id"]: deal for deal in discovered}
    return list(unique.values()), successful_sources


def fetch_prompts(existing: list[dict], now_ms: int) -> list[dict]:
    metadata_query = urlencode({"dataset": DATASET, "config": "default", "split": "train", "offset": 0, "length": 1})
    metadata = fetch_json(f"{DATASET_ROWS_URL}?{metadata_query}")
    total = int(metadata.get("num_rows_total", 0))
    offset = max(0, total - PAGE_SIZE)
    rows_query = urlencode({"dataset": DATASET, "config": "default", "split": "train", "offset": offset, "length": PAGE_SIZE})
    rows = fetch_json(f"{DATASET_ROWS_URL}?{rows_query}").get("rows", [])

    old_by_id = {item.get("id"): item for item in existing if item.get("id")}
    refreshed = []
    for record in rows:
        row = record.get("row") or {}
        title = str(row.get("act") or "").strip()
        prompt = str(row.get("prompt") or "").strip()
        row_index = record.get("row_idx")
        if not title or not prompt or row_index is None or len(prompt) > 12000:
            continue
        item_id = f"prompts-chat-{row_index}"
        old_item = old_by_id.get(item_id, {})
        refreshed.append(
            {
                "id": item_id,
                "title": title[:240],
                "description": "Prompt from the public prompts.chat dataset.",
                "prompt_type": str(row.get("type") or "text-generation"),
                "category_id": "ai-prompts",
                "category_name": "أوامر توليد من prompts.chat",
                "prompt": prompt,
                "language": "en",
                "tags": ["prompts.chat", "CC0-1.0"],
                "url": "https://prompts.chat/",
                "published_at": int(old_item.get("published_at", now_ms)),
                "license": "CC0-1.0",
            }
        )

    refreshed_by_id = {item["id"]: item for item in refreshed}
    for item in existing:
        item_id = item.get("id")
        if item_id and item_id not in refreshed_by_id:
            refreshed_by_id[item_id] = item
    ordered = sorted(refreshed_by_id.values(), key=lambda item: int(item.get("published_at", 0)), reverse=True)
    return ordered


def verify_offer(url: str) -> bool:
    request = Request(url, headers={"User-Agent": "MersadAI-PromptFeed/1.0", "Range": "bytes=0-1023"})
    with urlopen(request, timeout=20) as response:
        response.read(1024)
        return 200 <= response.status < 400


def build_deals(
    previous_sections: list[dict],
    discovered: list[dict],
    now_ms: int,
) -> tuple[dict, int]:
    existing = next((section for section in previous_sections if section.get("id") == SECTION_ID), {})
    old_items = {item.get("id"): item for item in existing.get("items", []) if item.get("id")}
    items = []
    verified_count = 0
    verified_date = datetime.fromtimestamp(now_ms / 1000, timezone.utc).date().isoformat()
    for offer in OFFERS:
        previous = old_items.get(offer["id"], {})
        try:
            if not verify_offer(offer["deal_url"]):
                raise RuntimeError("official page returned a non-success status")
            verified_at = now_ms
            verified_count += 1
        except (HTTPError, URLError, TimeoutError, RuntimeError) as error:
            print(f"Warning: could not verify {offer['id']}: {error}", file=sys.stderr)
            verified_at = previous.get("verified_at")
        item = {
            **offer,
            "published_at": previous.get("published_at", now_ms),
            "verified_date": verified_date if verified_at is not None else previous.get("verified_date"),
            "source_url": offer["deal_url"],
            "source_name": "Official provider",
        }
        if verified_at is not None:
            item["verified_at"] = verified_at
        items.append(item)

    discovered_by_id = {deal["id"]: deal for deal in discovered}
    for deal_id, deal in discovered_by_id.items():
        if not _is_creator_tool_deal(deal):
            continue
        previous = old_items.get(deal_id, {})
        deal = {
            **deal,
            "published_at": previous.get("published_at", deal["published_at"]),
            "verified_date": verified_date,
        }
        items.append(deal)

    for deal_id, previous in old_items.items():
        if deal_id in discovered_by_id or previous.get("source_type") != "community":
            continue
        if not _is_creator_tool_deal(previous):
            continue
        published_at = int(previous.get("published_at", 0))
        is_active = 0 <= now_ms - published_at <= ACTIVE_DEAL_DAYS * 24 * 60 * 60 * 1000
        items.append({**previous, "is_active": is_active})

    unique_items = {item["id"]: item for item in items}
    return {"id": SECTION_ID, "title": SECTION_TITLE, "items": list(unique_items.values())}, verified_count


def validate_feed(feed: dict) -> None:
    schema_version = feed.get("schema_version")
    if schema_version not in {2, 3}:
        raise ValueError(f"unsupported feed schema version: {schema_version}")
    if not isinstance(feed.get("prompts"), list):
        raise ValueError("feed must contain a prompts array")
    sections = feed.get("sections")
    if not isinstance(sections, list):
        raise ValueError("feed must contain a sections array")
    deal_section = next((section for section in sections if section.get("id") == SECTION_ID), None)
    if not deal_section or deal_section.get("title") != SECTION_TITLE:
        raise ValueError("AI deals section is missing or has an unexpected title")
    for section in sections:
        if not isinstance(section.get("items"), list):
            raise ValueError(f"section {section.get('id')} must contain an items array")
    photo_manipulation_section = next(
        (section for section in sections if section.get("id") == PHOTO_MANIPULATION_SECTION_ID),
        None,
    )
    if photo_manipulation_section:
        if photo_manipulation_section.get("title") != PHOTO_MANIPULATION_SECTION_TITLE:
            raise ValueError("Photo manipulation section has an unexpected title")
        for prompt in photo_manipulation_section["items"]:
            if not all(prompt.get(key) for key in ("id", "title", "description", "prompt", "url")):
                raise ValueError("each photo-manipulation prompt must include its title, description, prompt, and URL")
            if prompt.get("category_id") != PHOTO_MANIPULATION_CATEGORY_ID:
                raise ValueError(f"photo-manipulation prompt {prompt['id']} must use the visual-tricks category")
            if PHOTO_MANIPULATION_CATEGORY_NAME not in prompt.get("tags", []) or "خداع بصري / تلاعب" not in prompt.get("tags", []):
                raise ValueError(f"photo-manipulation prompt {prompt['id']} is missing its photography or illusion tags")
    factverse_section = next((section for section in sections if section.get("id") == FACTVERSE_SECTION_ID), None)
    if factverse_section:
        if factverse_section.get("title") != FACTVERSE_SECTION_TITLE:
            raise ValueError("FactVerse section has an unexpected title")
        for article in factverse_section["items"]:
            if not all(article.get(key) for key in ("id", "title", "url", "source_id", "source_name", "published_at")):
                raise ValueError("each FactVerse article must include its title, URL, source, and publication date")
            if article["source_id"] not in FACTVERSE_SOURCE_IDS:
                raise ValueError(f"unknown FactVerse source {article['source_id']}")
            if article.get("category_id") != FACTVERSE_CATEGORY_ID or article.get("language") != "en":
                raise ValueError(f"FactVerse article {article['id']} must use the English FactVerse category")
            if not article["url"].startswith("https://"):
                raise ValueError(f"FactVerse article {article['id']} must use HTTPS")
            if not article.get("visual_prompt") or not article.get("reels_script"):
                raise ValueError(f"FactVerse article {article['id']} must include visual and 30-second reel prompts")
            for scene in ("Scene 1 (0-10s):", "Scene 2 (10-20s):", "Scene 3 (20-30s):"):
                if scene not in article["reels_script"]:
                    raise ValueError(f"FactVerse article {article['id']} is missing {scene}")
            if "FactVerse • Explore The Future" not in article["reels_script"]:
                raise ValueError(f"FactVerse article {article['id']} is missing the fixed signature")
    for prompt in feed["prompts"]:
        if not prompt.get("id") or not prompt.get("title") or not prompt.get("prompt"):
            raise ValueError("each prompt must contain id, title, and prompt")
    timestamps = [int(item.get("published_at", 0)) for item in feed["prompts"]]
    if timestamps != sorted(timestamps, reverse=True):
        raise ValueError("prompts must be ordered newest to oldest")
    for deal in deal_section["items"]:
        required_fields = (
            ("id", "title", "provider", "deal_type", "deal_url", "verified_date")
            if deal.get("source_type") == "community"
            else ("id", "title", "url")
        )
        if not all(deal.get(key) for key in required_fields):
            raise ValueError(f"each deal must contain {', '.join(required_fields)}")
        if "is_active" in deal and not isinstance(deal["is_active"], bool):
            raise ValueError(f"deal {deal['id']} must have a boolean is_active field")
        deal_url = deal.get("deal_url", deal["url"])
        if not str(deal_url).startswith("https://"):
            raise ValueError(f"deal {deal['id']} must use an HTTPS deal_url")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--validate-only", action="store_true", help="validate the current feed without network access")
    args = parser.parse_args()

    feed = json.loads(FEED_PATH.read_text(encoding="utf-8"))
    for section in feed.get("sections", []):
        if section.get("id") == FACTVERSE_SECTION_ID:
            section["items"] = [enrich_factverse_article(article) for article in section.get("items", [])]
    validate_feed(feed)
    if args.validate_only:
        print("Feed validation passed.")
        return

    now_ms = int(time.time() * 1000)
    prompts = fetch_prompts(feed["prompts"], now_ms)
    community_deals, successful_sources = discover_community_deals(now_ms)
    factverse_articles, successful_factverse_sources = discover_factverse_articles(now_ms)
    photo_manipulation_prompts, successful_photo_sources = discover_photo_manipulation_prompts(now_ms)
    if successful_sources == 0:
        raise RuntimeError("No community deal source could be fetched; feed was not updated")
    deal_section, verified_count = build_deals(feed.get("sections", []), community_deals, now_ms)
    factverse_section = build_factverse_section(feed.get("sections", []), factverse_articles, now_ms)
    photo_manipulation_section = build_photo_manipulation_section(
        feed.get("sections", []),
        photo_manipulation_prompts,
        now_ms,
    )
    sections = [
        section for section in feed["sections"]
        if section.get("id") not in {SECTION_ID, FACTVERSE_SECTION_ID, PHOTO_MANIPULATION_SECTION_ID}
    ]
    sections.extend((deal_section, factverse_section, photo_manipulation_section))
    updated = {**feed, "schema_version": 3, "prompts": prompts, "sections": sections}
    validate_feed(updated)
    FEED_PATH.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Updated {len(prompts)} prompts, {len(deal_section['items'])} deals and "
        f"{len(factverse_section['items'])} FactVerse articles from {successful_factverse_sources} science sources, "
        f"and {len(photo_manipulation_section['items'])} photo-manipulation prompts from "
        f"{successful_photo_sources} live sources; "
        f"verified {verified_count} official pages.",
    )


if __name__ == "__main__":
    main()
