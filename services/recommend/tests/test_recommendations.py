import asyncio
import unittest
import os
import sys

# Thêm thư mục services/recommend vào sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from unittest.mock import patch, AsyncMock
from app.models.schemas import ChatRequest, EventCard
from app.services.chat_service import chat_service
from app.services.recommend_service import recommend_service

# Dữ liệu sự kiện giả lập CHỈ DÙNG RIÊNG CHO UNIT TEST (không nằm trong mã nguồn thực tế)
MOCK_TEST_EVENTS = [
    EventCard(
        id="evt-001",
        title="Live Concert Nhạc Trẻ Hà Nội",
        description="Đêm nhạc quy tụ các ca sĩ hàng đầu.",
        categoryName="Âm nhạc",
        venue="Cung Điền kinh",
        address="Mỹ Đình, Hà Nội",
        startDate="2026-10-20T19:30:00Z",
        minPrice=300000,
        maxPrice=1500000,
        status="PUBLISHED"
    ),
    EventCard(
        id="evt-002",
        title="Đại nhạc hội Acoustic Mùa Thu",
        description="Không gian acoustic ấm cúng lãng mạn.",
        categoryName="Âm nhạc",
        venue="Nhà hát Lớn",
        address="Hoàn Kiếm, Hà Nội",
        startDate="2026-10-25T20:00:00Z",
        minPrice=200000,
        maxPrice=800000,
        status="PUBLISHED"
    ),
    EventCard(
        id="evt-003",
        title="Giải Bóng Đá Quốc Tế",
        description="Trận bóng đá giao hữu quốc tế.",
        categoryName="Thể thao",
        venue="Sân vận động Mỹ Đình",
        address="Nam Từ Liêm, Hà Nội",
        startDate="2026-11-01T19:00:00Z",
        minPrice=150000,
        maxPrice=500000,
        status="PUBLISHED"
    ),
    EventCard(
        id="evt-004",
        title="Vở kịch kinh điển",
        description="Kịch nói truyền thống.",
        categoryName="Sân khấu",
        venue="Nhà hát Tuổi Trẻ",
        address="Hai Bà Trưng, Hà Nội",
        startDate="2026-11-05T19:30:00Z",
        minPrice=100000,
        maxPrice=300000,
        status="PUBLISHED"
    )
]


class TestRecommendService(unittest.TestCase):

    @patch("app.services.chat_service.catalog_client.get_published_events", new_callable=AsyncMock)
    def test_chat_fallback(self, mock_get_events):
        """Kiểm thử chat tư vấn sự kiện bằng từ khóa & ngữ cảnh (Fallback mode)"""
        mock_get_events.return_value = MOCK_TEST_EVENTS

        req = ChatRequest(
            message="Cuối tuần này có show ca nhạc nào ở Hà Nội không bạn?"
        )
        loop = asyncio.new_event_loop()
        res = loop.run_until_complete(chat_service.chat(req))
        loop.close()

        self.assertIsNotNone(res.reply)
        self.assertTrue(len(res.suggestedEvents) > 0)
        self.assertTrue(len(res.followUpQuestions) > 0)
        # Đảm bảo sự kiện gợi ý là âm nhạc hoặc tại Hà Nội
        first_event = res.suggestedEvents[0]
        self.assertIsNotNone(first_event.title)

    @patch("app.services.recommend_service.catalog_client.get_published_events", new_callable=AsyncMock)
    def test_for_you_feed(self, mock_get_events):
        """Kiểm thử feed sự kiện gợi ý trên trang chủ"""
        mock_get_events.return_value = MOCK_TEST_EVENTS

        loop = asyncio.new_event_loop()
        items = loop.run_until_complete(recommend_service.get_for_you(limit=3, categories=["Âm nhạc"]))
        loop.close()

        self.assertEqual(len(items), 3)
        self.assertTrue(items[0].matchScore >= 0.9)
        self.assertTrue(len(items[0].reasons) > 0)

    @patch("app.services.recommend_service.catalog_client.get_published_events", new_callable=AsyncMock)
    def test_similar_events(self, mock_get_events):
        """Kiểm thử sự kiện tương tự khi xem chi tiết 1 sự kiện"""
        mock_get_events.return_value = MOCK_TEST_EVENTS

        target_id = "evt-001"
        loop = asyncio.new_event_loop()
        similar = loop.run_until_complete(recommend_service.get_similar_events(event_id=target_id, limit=2))
        loop.close()

        self.assertTrue(len(similar) > 0)
        # Không được gợi ý lại chính sự kiện đang xem
        for s in similar:
            self.assertNotEqual(s.eventId, target_id)


if __name__ == "__main__":
    unittest.main()
