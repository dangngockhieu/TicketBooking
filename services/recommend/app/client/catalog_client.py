import logging
import time
from typing import List, Optional
import httpx

from app.config import settings
from app.models.schemas import EventCard

logger = logging.getLogger(__name__)

class CatalogClient:
    def __init__(self):
        self._cache: List[EventCard] = []
        self._last_fetch: float = 0.0

    async def get_published_events(self, force_refresh: bool = False) -> List[EventCard]:
        """
        Lấy danh sách sự kiện đã xuất bản (PUBLISHED) từ Catalog Service.
        Chỉ lấy dữ liệu thực tế từ hệ thống, không tạo sự kiện giả lập bên ngoài.
        """
        now = time.time()
        if not force_refresh and self._cache and (now - self._last_fetch < settings.CACHE_TTL_SECONDS):
            return self._cache

        url = f"{settings.CATALOG_SERVICE_URL}/api/events"
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                resp = await client.get(url, params={"page": 0, "size": 100, "status": "PUBLISHED"})
                if resp.status_code == 200:
                    payload = resp.json()
                    raw_items = payload.get("data", {}).get("content", [])
                    events = []
                    for item in raw_items:
                        # Trích xuất giá vé nhỏ nhất và lớn nhất từ ticketClasses
                        classes = item.get("ticketClasses", [])
                        prices = [tc.get("price", 0) for tc in classes if "price" in tc]
                        min_p = min(prices) if prices else 0
                        max_p = max(prices) if prices else 0

                        events.append(EventCard(
                            id=item.get("id"),
                            title=item.get("title", ""),
                            description=item.get("description", ""),
                            categoryName=item.get("category", {}).get("name", "Khác"),
                            venue=item.get("venueName", ""),
                            address=item.get("address", ""),
                            startDate=item.get("startDate", ""),
                            endDate=item.get("endDate", ""),
                            bannerUrl=item.get("bannerUrl", ""),
                            minPrice=min_p,
                            maxPrice=max_p,
                            status=item.get("status", "PUBLISHED")
                        ))
                    self._cache = events
                    self._last_fetch = now
                    logger.info(f"Đã đồng bộ {len(events)} sự kiện thực tế từ Catalog Service")
                    return events
        except Exception as e:
            logger.warning(f"Không thể kết nối Catalog Service ({e}). Giữ cache cũ hoặc trả về rỗng.")

        # Nếu lỗi và có cache trước đó thì trả cache, ngược lại trả về rỗng
        return self._cache


catalog_client = CatalogClient()
