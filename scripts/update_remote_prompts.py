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
MAX_PROMPTS = 300
PAGE_SIZE = 100
ACTIVE_DEAL_DAYS = 30
DEAL_CATEGORY_ID = "ai-deals"
DEAL_CATEGORY_NAME = "🎁 عروض واشتراكات مجانية (AI Deals & Trials)"
FACTVERSE_SECTION_ID = "factverse-science"
FACTVERSE_SECTION_TITLE = "🌌 FactVerse (Science, Future & AI)"
FACTVERSE_CATEGORY_ID = "factverse"
FACTVERSE_MAX_ITEMS = 70
FACTVERSE_RETENTION_DAYS = 180
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
]


def fetch_json(url: str) -> dict:
    request = Request(url, headers={"User-Agent": "MersadAI-PromptFeed/1.0", "Accept": "application/json"})
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


def build_factverse_section(
    previous_sections: list[dict],
    discovered: list[dict],
    now_ms: int,
) -> dict:
    existing = next((section for section in previous_sections if section.get("id") == FACTVERSE_SECTION_ID), {})
    cutoff = now_ms - FACTVERSE_RETENTION_DAYS * 24 * 60 * 60 * 1000
    articles = {
        article["id"]: article
        for article in existing.get("items", [])
        if article.get("id") and int(article.get("published_at", 0)) >= cutoff
    }
    articles.update({article["id"]: article for article in discovered})
    ordered = sorted(articles.values(), key=lambda article: int(article.get("published_at", 0)), reverse=True)
    return {"id": FACTVERSE_SECTION_ID, "title": FACTVERSE_SECTION_TITLE, "items": ordered[:FACTVERSE_MAX_ITEMS]}


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
        if item_id and item_id not in refreshed_by_id and not item_id.startswith("prompts-chat-"):
            refreshed_by_id[item_id] = item
    ordered = sorted(refreshed_by_id.values(), key=lambda item: int(item.get("published_at", 0)), reverse=True)
    return ordered[:MAX_PROMPTS]


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
            if not previous:
                continue
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
    validate_feed(feed)
    if args.validate_only:
        print("Feed validation passed.")
        return

    now_ms = int(time.time() * 1000)
    prompts = fetch_prompts(feed["prompts"], now_ms)
    community_deals, successful_sources = discover_community_deals(now_ms)
    factverse_articles, successful_factverse_sources = discover_factverse_articles(now_ms)
    if successful_sources == 0:
        raise RuntimeError("No community deal source could be fetched; feed was not updated")
    deal_section, verified_count = build_deals(feed.get("sections", []), community_deals, now_ms)
    factverse_section = build_factverse_section(feed.get("sections", []), factverse_articles, now_ms)
    sections = [
        section for section in feed["sections"]
        if section.get("id") not in {SECTION_ID, FACTVERSE_SECTION_ID}
    ]
    sections.extend((deal_section, factverse_section))
    updated = {**feed, "schema_version": 3, "prompts": prompts, "sections": sections}
    validate_feed(updated)
    FEED_PATH.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Updated {len(prompts)} prompts, {len(deal_section['items'])} deals and "
        f"{len(factverse_section['items'])} FactVerse articles from {successful_factverse_sources} science sources; "
        f"verified {verified_count} official pages.",
    )


if __name__ == "__main__":
    main()
