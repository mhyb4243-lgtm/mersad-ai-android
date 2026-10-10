import unittest
import json
from unittest.mock import patch

import update_remote_prompts as updater


class FactVerseFeedTests(unittest.TestCase):
    def test_committed_feed_contains_valid_english_factverse_templates(self):
        feed = json.loads(updater.FEED_PATH.read_text(encoding="utf-8"))
        updater.validate_feed(feed)
        prompts = {prompt["id"]: prompt for prompt in feed["prompts"]}
        factverse = next(section for section in feed["sections"] if section["id"] == updater.FACTVERSE_SECTION_ID)
        self.assertEqual(70, len(factverse["items"]))

        split_screen = prompts["factverse-split-screen-science-01"]
        video_reel = prompts["factverse-physics-reel-30s-01"]
        self.assertEqual("factverse", split_screen["category_id"])
        self.assertIn("Nature, MIT, ScienceDaily", split_screen["prompt"])
        self.assertEqual("factverse", video_reel["category_id"])
        self.assertEqual(30, video_reel["duration_seconds"])
        self.assertEqual(3, video_reel["prompt"].count("Lower-third overlay:"))
        self.assertIn('Lower-third overlay: "FactVerse • Explore The Future"', video_reel["prompt"])
        self.assertEqual(3, video_reel["prompt"].count("Voiceover (English):"))

    def test_live_deals_only_include_creator_tools(self):
        rss = """<rss><channel>
            <item><title>Runway free trial for creators</title><description>Try video tools free.</description><link>https://runwayml.com/pricing</link></item>
            <item><title>Free movie tickets</title><description>Free tickets with code STUDIO.</description><link>https://example.com/tickets</link></item>
        </channel></rss>"""

        deals = updater.parse_rss_deals(rss, "reddit-ai", "r/ArtificialIntelligence", 1000)

        self.assertEqual(["Runway"], [deal["provider"] for deal in deals])
        self.assertFalse(updater._is_creator_tool_deal({"title": "Free grocery coupon"}))
        self.assertEqual(
            {"Runway", "Kling AI", "Pika", "ElevenLabs", "Leonardo AI", "Suno"},
            {provider for _, provider, _, _, _ in updater.CREATOR_TOOL_OFFERS},
        )

    @patch("update_remote_prompts.verify_offer", side_effect=RuntimeError("temporarily unavailable"))
    def test_official_tool_cards_remain_visible_when_plan_page_cannot_be_verified(self, _verify):
        section, verified = updater.build_deals([], [], 1_797_000_000_000)

        self.assertEqual(0, verified)
        offers = {offer["id"]: offer for offer in section["items"]}
        self.assertTrue(set(updater.OFFER_DETAILS).issubset(offers))
        for offer_id in updater.OFFER_DETAILS:
            self.assertTrue(offers[offer_id]["url"].startswith("https://"))
            self.assertIsNone(offers[offer_id]["verified_date"])
            self.assertIn(updater.OFFER_DETAILS[offer_id][1], offers[offer_id]["free_limit"])

    def test_parses_sciencedaily_rss_as_english_https_article(self):
        rss = """<?xml version="1.0"?><rss><channel><item>
            <title>New result in quantum materials</title>
            <link>https://www.sciencedaily.com/releases/2026/10/example.htm</link>
            <description><![CDATA[<p>Researchers report a new result.</p>]]></description>
            <pubDate>Fri, 09 Oct 2026 10:00:00 +0000</pubDate>
            <guid>science-example-1</guid>
        </item></channel></rss>"""

        article = updater.parse_science_rss(
            rss,
            "factverse-sciencedaily",
            "ScienceDaily",
            1_797_000_000_000,
        )[0]

        self.assertEqual("New result in quantum materials", article["title"])
        self.assertEqual("Researchers report a new result.", article["description"])
        self.assertEqual("https://www.sciencedaily.com/releases/2026/10/example.htm", article["url"])
        self.assertEqual("factverse", article["category_id"])
        self.assertEqual("en", article["language"])
        self.assertEqual("factverse-sciencedaily", article["source_id"])
        enriched = updater.enrich_factverse_article(article)
        self.assertIn("LEFT PANEL", enriched["visual_prompt"])
        self.assertIn("Scene 1 (0-10s):", enriched["reels_script"])
        self.assertIn("Scene 2 (10-20s):", enriched["reels_script"])
        self.assertIn("Scene 3 (20-30s):", enriched["reels_script"])
        self.assertIn("FactVerse • Explore The Future", enriched["reels_script"])

    def test_rejects_unknown_sources_and_non_https_articles(self):
        with self.assertRaises(ValueError):
            updater.parse_science_rss("<rss/>", "other-source", "Other", 1)

        rss = """<rss><channel><item><title>Example</title><link>http://example.com/story</link></item></channel></rss>"""
        self.assertEqual(
            [],
            updater.parse_science_rss(rss, "factverse-sciencedaily", "ScienceDaily", 1),
        )

    def test_merges_fresh_articles_and_retains_recent_cached_articles(self):
        now_ms = 1_797_000_000_000
        old_article = {
            "id": "factverse-futurology-old",
            "title": "Recent cached story",
            "url": "https://www.reddit.com/r/Futurology/comments/example/",
            "source_id": "factverse-futurology",
            "source_name": "r/Futurology",
            "source_url": "https://www.reddit.com/r/Futurology/",
            "category_id": "factverse",
            "language": "en",
            "published_at": now_ms - 24 * 60 * 60 * 1000,
        }
        expired_article = {**old_article, "id": "expired", "published_at": 1}
        existing = [{"id": updater.FACTVERSE_SECTION_ID, "items": [old_article, expired_article]}]
        new_article = {**old_article, "id": "new-story", "published_at": now_ms}

        result = updater.build_factverse_section(existing, [new_article], now_ms)

        self.assertEqual(updater.FACTVERSE_SECTION_TITLE, result["title"])
        self.assertEqual(["new-story", old_article["id"]], [item["id"] for item in result["items"]])

    @patch("update_remote_prompts.fetch_json")
    def test_refresh_keeps_curated_factverse_prompts(self, fetch_json):
        fetch_json.side_effect = [
            {"num_rows_total": 1},
            {"rows": [{"row_idx": 7, "row": {"act": "Writing helper", "prompt": "Write a note."}}]},
        ]
        curated = {
            "id": "factverse-split-screen-science-01",
            "title": "FactVerse Split-Screen: Biology Meets the Future",
            "prompt": "English template",
            "category_id": "factverse",
        }

        result = updater.fetch_prompts([curated], 1_797_000_000_000)

        self.assertIn(curated, result)
        self.assertIn("prompts-chat-7", [item["id"] for item in result])

    @patch("update_remote_prompts.fetch_json")
    def test_refresh_never_drops_previously_saved_prompt_cards(self, fetch_json):
        fetch_json.side_effect = [
            {"num_rows_total": 1},
            {"rows": [{"row_idx": 7, "row": {"act": "Writing helper", "prompt": "Write a note."}}]},
        ]
        existing = [
            {"id": "prompts-chat-6", "title": "Older prompt", "prompt": "Keep this prompt.", "published_at": 1},
            {"id": "curated-prompt", "title": "Curated prompt", "prompt": "Keep this too.", "published_at": 2},
        ]

        result = updater.fetch_prompts(existing, 1_797_000_000_000)

        self.assertEqual(
            {"prompts-chat-6", "prompts-chat-7", "curated-prompt"},
            {item["id"] for item in result},
        )


if __name__ == "__main__":
    unittest.main()
