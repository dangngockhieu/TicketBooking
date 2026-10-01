import logging
import math
from typing import List, Optional

try:
    from openai import AsyncOpenAI
except ImportError:
    AsyncOpenAI = None

try:
    import numpy as np
except ImportError:
    np = None

from app.config import settings
from app.client.catalog_client import catalog_client
from app.models.schemas import EventCard, RecommendationItem

logger = logging.getLogger(__name__)


def cosine_similarity(a: List[float], b: List[float]) -> float:
    if np is not None:
        arr_a = np.array(a)
        arr_b = np.array(b)
        norm_a = np.linalg.norm(arr_a)
        norm_b = np.linalg.norm(arr_b)
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return float(np.dot(arr_a, arr_b) / (norm_a * norm_b))
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(x * x for x in b))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


class RecommendService:
    def __init__(self):
        self.client: Optional[AsyncOpenAI] = None
        if settings.OPENAI_API_KEY and AsyncOpenAI is not None:
            self.client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)
        self._embedding_cache = {}

    async def get_for_you(
        self,
        limit: int = 10,
        user_id: Optional[str] = None,
        categories: Optional[List[str]] = None
    ) -> List[RecommendationItem]:
        """Gợi ý sự kiện cá nhân hóa trên trang chủ (For You Feed)"""
        events = await catalog_client.get_published_events()
        if not events:
            return []

        results = []
        for ev in events[:limit]:
            # Tính điểm matchScore mô phỏng dựa trên danh mục & độ hot
            reasons = []
            score = 0.85
            if categories and ev.categoryName in categories:
                score = 0.96
                reasons.append(f"Phù hợp với sở thích thể loại {ev.categoryName}")
            else:
                reasons.append(f"Sự kiện {ev.categoryName} đang được nhiều khán giả quan tâm")

            reasons.append("Dựa trên xu hướng mua vé của cộng đồng")

            results.append(RecommendationItem(
                eventId=ev.id,
                title=ev.title,
                categoryName=ev.categoryName,
                venue=ev.venue,
                bannerUrl=ev.bannerUrl,
                minPrice=ev.minPrice,
                maxPrice=ev.maxPrice,
                matchScore=round(score, 2),
                reasons=reasons
            ))

        # Sắp xếp theo matchScore giảm dần
        results.sort(key=lambda x: x.matchScore, reverse=True)
        return results[:limit]

    async def get_similar_events(self, event_id: str, limit: int = 5) -> List[RecommendationItem]:
        """Gợi ý các sự kiện tương tự khi đang xem chi tiết 1 sự kiện"""
        events = await catalog_client.get_published_events()
        target_event = next((e for e in events if e.id == event_id), None)
        if not target_event:
            return []

        candidate_events = [e for e in events if e.id != event_id]
        if not candidate_events:
            return []

        # 1. Nếu có OpenAI, tính Cosine Similarity dựa trên Text Embedding
        if self.client:
            try:
                return await self._get_similar_by_openai_embeddings(target_event, candidate_events, limit)
            except Exception as e:
                logger.error(f"Lỗi khi tính OpenAI Embeddings: {e}. Dùng thuật toán so khớp thể loại.")

        # 2. Fallback dựa trên category và keyword
        return self._get_similar_fallback(target_event, candidate_events, limit)

    async def _get_embedding(self, text: str) -> List[float]:
        if text in self._embedding_cache:
            return self._embedding_cache[text]

        resp = await self.client.embeddings.create(
            model=settings.OPENAI_EMBEDDING_MODEL,
            input=text
        )
        vec = resp.data[0].embedding
        self._embedding_cache[text] = vec
        return vec

    async def _get_similar_by_openai_embeddings(
        self,
        target: EventCard,
        candidates: List[EventCard],
        limit: int
    ) -> List[RecommendationItem]:
        target_text = f"{target.title}. Thể loại: {target.categoryName}. {target.description}"
        target_vec = await self._get_embedding(target_text)

        scored = []
        for cand in candidates:
            cand_text = f"{cand.title}. Thể loại: {cand.categoryName}. {cand.description}"
            cand_vec = await self._get_embedding(cand_text)
            sim = cosine_similarity(target_vec, cand_vec)

            reasons = []
            if cand.categoryName == target.categoryName:
                reasons.append(f"Cùng thể loại {cand.categoryName}")
            reasons.append(f"Mức độ tương đồng nội dung: {int(sim * 100)}%")

            scored.append(RecommendationItem(
                eventId=cand.id,
                title=cand.title,
                categoryName=cand.categoryName,
                venue=cand.venue,
                bannerUrl=cand.bannerUrl,
                minPrice=cand.minPrice,
                maxPrice=cand.maxPrice,
                matchScore=round(float(sim), 2),
                reasons=reasons
            ))

        scored.sort(key=lambda x: x.matchScore, reverse=True)
        return scored[:limit]

    def _get_similar_fallback(
        self,
        target: EventCard,
        candidates: List[EventCard],
        limit: int
    ) -> List[RecommendationItem]:
        scored = []
        for cand in candidates:
            score = 0.5
            reasons = []
            if cand.categoryName == target.categoryName:
                score += 0.35
                reasons.append(f"Cùng thuộc danh mục {cand.categoryName}")
            if cand.venue and target.venue and cand.venue == target.venue:
                score += 0.1
                reasons.append(f"Cùng tổ chức tại {cand.venue}")

            reasons.append("Sự kiện nổi bật được khán giả cùng yêu thích")

            scored.append(RecommendationItem(
                eventId=cand.id,
                title=cand.title,
                categoryName=cand.categoryName,
                venue=cand.venue,
                bannerUrl=cand.bannerUrl,
                minPrice=cand.minPrice,
                maxPrice=cand.maxPrice,
                matchScore=round(min(score, 0.95), 2),
                reasons=reasons
            ))

        scored.sort(key=lambda x: x.matchScore, reverse=True)
        return scored[:limit]


recommend_service = RecommendService()
