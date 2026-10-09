#!/usr/bin/env python3
"""Refresh the public prompt feed from Hugging Face and verify official free plans."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
FEED_PATH = ROOT / "remote_prompts.json"
DATASET = "fka/prompts.chat"
DATASET_ROWS_URL = "https://datasets-server.huggingface.co/rows"
SECTION_ID = "ai-deals-and-trials"
SECTION_TITLE = "عروض واشتراكات الذكاء الاصطناعي المجانية (AI Deals & Trials)"
MAX_PROMPTS = 300
PAGE_SIZE = 100

OFFERS = [
    {
        "id": "chatgpt-free",
        "title": "ChatGPT Free",
        "description": "خطة مجانية للاستخدام اليومي بميزات وحدود استخدام قد تتغير حسب البلد والسياسة الرسمية.",
        "url": "https://openai.com/chatgpt/pricing/",
        "provider": "OpenAI",
        "free_status": "FREE_TIER",
        "free_limit": "خطة مجانية؛ راجع صفحة الأسعار للحدود الحالية.",
        "requires_account": True,
        "requires_payment_card": False,
        "tags": ["chatgpt", "free-tier", "official"],
    },
    {
        "id": "gemini-api-free-tier",
        "title": "Gemini API Free Tier",
        "description": "طبقة مجانية لاستخدام Gemini API، وتختلف الحدود والتوفر باختلاف النموذج والمنطقة.",
        "url": "https://ai.google.dev/gemini-api/docs/pricing",
        "provider": "Google",
        "free_status": "FREE_TIER",
        "free_limit": "حدود مجانية حسب النموذج؛ راجع صفحة الأسعار الرسمية.",
        "requires_account": True,
        "requires_payment_card": False,
        "tags": ["gemini", "api", "free-tier", "official"],
    },
    {
        "id": "claude-free",
        "title": "Claude Free",
        "description": "خطة Claude المجانية مع حدود استخدام متغيرة وفق صفحة Anthropic الرسمية.",
        "url": "https://www.anthropic.com/claude",
        "provider": "Anthropic",
        "free_status": "FREE_TIER",
        "free_limit": "خطة مجانية بحدود استخدام؛ راجع تفاصيل الخطة الرسمية.",
        "requires_account": True,
        "requires_payment_card": False,
        "tags": ["claude", "free-tier", "official"],
    },
    {
        "id": "perplexity-free",
        "title": "Perplexity Free",
        "description": "خطة مجانية للبحث والإجابات، مع حدود وميزات تتغير حسب صفحة الأسعار الرسمية.",
        "url": "https://www.perplexity.ai/pricing",
        "provider": "Perplexity",
        "free_status": "FREE_TIER",
        "free_limit": "خطة مجانية؛ راجع صفحة الأسعار للحدود الحالية.",
        "requires_account": True,
        "requires_payment_card": False,
        "tags": ["perplexity", "free-tier", "official"],
    },
]


def fetch_json(url: str) -> dict:
    request = Request(url, headers={"User-Agent": "MersadAI-PromptFeed/1.0", "Accept": "application/json"})
    with urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected HTTP status {response.status} from {url}")
        return json.load(response)


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


def build_deals(previous_sections: list[dict], now_ms: int) -> tuple[list[dict], int]:
    existing = next((section for section in previous_sections if section.get("id") == SECTION_ID), {})
    old_items = {item.get("id"): item for item in existing.get("items", []) if item.get("id")}
    items = []
    verified_count = 0
    for offer in OFFERS:
        previous = old_items.get(offer["id"], {})
        try:
            if not verify_offer(offer["url"]):
                raise RuntimeError("official page returned a non-success status")
            verified_at = now_ms
            verified_count += 1
        except (HTTPError, URLError, TimeoutError, RuntimeError) as error:
            print(f"Warning: could not verify {offer['id']}: {error}", file=sys.stderr)
            if not previous:
                continue
            verified_at = previous.get("verified_at")
        item = {**offer, "published_at": previous.get("published_at", now_ms)}
        if verified_at is not None:
            item["verified_at"] = verified_at
        items.append(item)
    return [{"id": SECTION_ID, "title": SECTION_TITLE, "items": items}], verified_count


def validate_feed(feed: dict) -> None:
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
    for prompt in feed["prompts"]:
        if not prompt.get("id") or not prompt.get("title") or not prompt.get("prompt"):
            raise ValueError("each prompt must contain id, title, and prompt")
    timestamps = [int(item.get("published_at", 0)) for item in feed["prompts"]]
    if timestamps != sorted(timestamps, reverse=True):
        raise ValueError("prompts must be ordered newest to oldest")


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
    sections, verified_count = build_deals(feed.get("sections", []), now_ms)
    if verified_count == 0:
        raise RuntimeError("No official offer pages could be verified; feed was not updated")
    updated = {**feed, "schema_version": 2, "prompts": prompts, "sections": sections}
    validate_feed(updated)
    FEED_PATH.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {len(prompts)} prompts and verified {verified_count} official offer pages.")


if __name__ == "__main__":
    main()
