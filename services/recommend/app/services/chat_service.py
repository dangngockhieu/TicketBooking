import json
import logging
from typing import List, Optional

try:
    from openai import AsyncOpenAI
except ImportError:
    AsyncOpenAI = None

from app.config import settings
from app.client.catalog_client import catalog_client
from app.models.schemas import ChatRequest, ChatResponseData, EventCard

logger = logging.getLogger(__name__)


class ChatService:
    def __init__(self):
        self.client: Optional[AsyncOpenAI] = None
        if settings.OPENAI_API_KEY and AsyncOpenAI is not None:
            self.client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)

    async def chat(self, request: ChatRequest) -> ChatResponseData:
        # 1. Lấy danh sách sự kiện hiện có từ Catalog Service
        events: List[EventCard] = await catalog_client.get_published_events()

        if not events:
            return ChatResponseData(
                reply="Xin chào! Hiện tại hệ thống chưa có sự kiện nào đang mở bán vé. Bạn vui lòng quay lại sau nhé!",
                suggestedEvents=[],
                followUpQuestions=[]
            )

        # 2. Nếu có OpenAI API Key, gọi GPT-4o-mini với RAG
        if self.client:
            try:
                return await self._chat_with_openai(request, events)
            except Exception as e:
                logger.error(f"Lỗi khi gọi OpenAI API: {e}. Chuyển sang xử lý dự phòng.")

        # 3. Fallback xử lý cục bộ khi chưa nhập API key hoặc gặp lỗi mạng (dựa trên sự kiện thực có trong hệ thống)
        return self._fallback_chat(request, events)

    async def _chat_with_openai(self, request: ChatRequest, events: List[EventCard]) -> ChatResponseData:
        # Chuẩn bị context các sự kiện tóm tắt cho Prompt RAG
        events_context = []
        for ev in events:
            price_text = f"từ {ev.minPrice:,.0f}đ đến {ev.maxPrice:,.0f}đ" if ev.minPrice and ev.maxPrice else "Chưa công bố"
            events_context.append({
                "id": ev.id,
                "title": ev.title,
                "category": ev.categoryName,
                "venue": ev.venue,
                "address": ev.address,
                "startDate": ev.startDate,
                "price": price_text,
                "description": (ev.description[:150] + "...") if ev.description else ""
            })

        system_prompt = f"""Bạn là Trợ lý AI tư vấn vé sự kiện thông minh của hệ thống TicketBooking.
Nhiệm vụ của bạn:
1. Thân thiện, nhiệt tình, tư vấn các sự kiện phù hợp nhất với yêu cầu, ngân sách, địa điểm và sở thích của người dùng.
2. Bạn CHỈ gợi ý các sự kiện có thật trong danh sách dữ liệu sau đây:
{json.dumps(events_context, ensure_ascii=False, indent=2)}

Quy định định dạng phản hồi:
Bạn PHẢI trả về định dạng JSON thuần tuý (JSON object) với cấu trúc sau:
{{
  "reply": "Nội dung trả lời tự nhiên bằng tiếng Việt, giải thích lý do vì sao sự kiện này hợp với yêu cầu của người dùng",
  "suggestedEventIds": ["id_cua_su_kien_1", "id_cua_su_kien_2"],
  "followUpQuestions": [
    "Câu hỏi gợi mở 1 để người dùng hỏi thêm (VD: Bạn muốn đi vào ngày nào?)",
    "Câu hỏi gợi mở 2 (VD: Ngân sách tối đa của bạn là bao nhiêu?)"
  ]
}}
Lưu ý: Không bao gồm markdown ```json ```, chỉ trả về chuỗi JSON hợp lệ.
"""

        messages = [{"role": "system", "content": system_prompt}]

        # Thêm lịch sử chat gần đây nếu có
        if request.history:
            for h in request.history[-4:]:
                messages.append({"role": h.role, "content": h.content})

        user_content = request.message
        if request.userLocation:
            user_content += f"\n(Vị trí của tôi: {request.userLocation})"

        messages.append({"role": "user", "content": user_content})

        response = await self.client.chat.completions.create(
            model=settings.OPENAI_MODEL,
            messages=messages,
            temperature=0.7,
            max_tokens=800,
            response_format={"type": "json_object"}
        )

        content = response.choices[0].message.content
        data = json.loads(content)

        suggested_ids = set(data.get("suggestedEventIds", []))
        matched_events = [ev for ev in events if ev.id in suggested_ids]

        return ChatResponseData(
            reply=data.get("reply", "Dưới đây là một số sự kiện gợi ý cho bạn:"),
            suggestedEvents=matched_events,
            followUpQuestions=data.get("followUpQuestions", [
                "Bạn muốn tìm sự kiện ở Hà Nội hay TP.HCM?",
                "Bạn muốn đi vào cuối tuần này hay tuần sau?"
            ])
        )

    def _fallback_chat(self, request: ChatRequest, events: List[EventCard]) -> ChatResponseData:
        """Thuật toán tìm kiếm theo từ khóa & ngân sách khi chưa có OpenAI API Key"""
        msg_lower = request.message.lower()
        matched = []

        # 1. Lọc theo từ khóa thể loại
        for ev in events:
            text_corpus = f"{ev.title} {ev.description} {ev.categoryName} {ev.venue} {ev.address}".lower()

            # Kiểm tra âm nhạc, concert, rock, acoustic
            if any(k in msg_lower for k in ["nhạc", "concert", "ca nhạc", "show", "hát", "rock", "indie", "acoustic"]):
                if ev.categoryName and "nhạc" in ev.categoryName.lower():
                    matched.append(ev)
                    continue

            # Kiểm tra thể thao, bóng đá
            if any(k in msg_lower for k in ["thể thao", "bóng đá", "trận", "chạy", "football"]):
                if ev.categoryName and "thể thao" in ev.categoryName.lower():
                    matched.append(ev)
                    continue

            # Kiểm tra kịch, nghệ thuật
            if any(k in msg_lower for k in ["kịch", "nghệ thuật", "diễn", "sân khấu"]):
                if ev.categoryName and ("kịch" in ev.categoryName.lower() or "nghệ thuật" in ev.categoryName.lower()):
                    matched.append(ev)
                    continue

            # Kiểm tra địa điểm (Hà Nội, TP.HCM)
            if "hà nội" in msg_lower and "hà nội" in text_corpus:
                matched.append(ev)
                continue
            if ("hcm" in msg_lower or "sài gòn" in msg_lower or "tp.hcm" in msg_lower) and ("tp. hcm" in text_corpus or "quận" in text_corpus):
                matched.append(ev)
                continue

        # Nếu không khớp cụ thể, lấy 2 sự kiện hot nhất
        if not matched:
            matched = events[:2]

        # Khử trùng lặp
        unique_events = []
        seen_ids = set()
        for ev in matched:
            if ev.id not in seen_ids:
                seen_ids.add(ev.id)
                unique_events.append(ev)

        reply = f"Chào bạn! Dựa trên yêu cầu của bạn, TicketBooking xin gợi ý {len(unique_events)} sự kiện nổi bật dưới đây đang mở bán vé. Bạn có thể nhấn vào từng sự kiện để xem chi tiết hạng vé nhé!"

        return ChatResponseData(
            reply=reply,
            suggestedEvents=unique_events[:3],
            followUpQuestions=[
                "Bạn muốn xem sự kiện tại Hà Nội hay TP. Hồ Chí Minh?",
                "Ngân sách dự kiến cho mỗi vé của bạn là khoảng bao nhiêu?"
            ]
        )


chat_service = ChatService()
