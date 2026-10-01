from typing import List, Optional
from fastapi import APIRouter, Query

from app.models.schemas import (
    ApiResponse,
    ChatRequest,
    ChatResponseData,
    RecommendationItem
)
from app.services.chat_service import chat_service
from app.services.recommend_service import recommend_service

router = APIRouter(prefix="/api/recommendations", tags=["AI Recommendations & Chatbot"])


@router.post("/chat", response_model=ApiResponse[ChatResponseData])
async def chat_with_assistant(request: ChatRequest):
    """
    Trợ lý AI tư vấn sự kiện thông minh (OpenAI ChatGPT + RAG).
    Nhận câu hỏi ngôn ngữ tự nhiên từ người dùng và trả về lời khuyên cùng danh sách vé phù hợp.
    """
    result = await chat_service.chat(request)
    return ApiResponse(
        status=0,
        message="Thành công",
        data=result
    )


@router.get("/events/for-you", response_model=ApiResponse[List[RecommendationItem]])
async def get_for_you_recommendations(
    limit: int = Query(10, ge=1, le=50, description="Số lượng sự kiện gợi ý"),
    userId: Optional[str] = Query(None, description="ID người dùng để cá nhân hóa"),
    categories: Optional[List[str]] = Query(None, description="Danh mục yêu thích")
):
    """
    Gợi ý sự kiện cá nhân hóa hiển thị trên Trang Chủ (For You Feed).
    """
    items = await recommend_service.get_for_you(
        limit=limit,
        user_id=userId,
        categories=categories
    )
    return ApiResponse(
        status=0,
        message="Thành công",
        data=items
    )


@router.get("/events/{eventId}/similar", response_model=ApiResponse[List[RecommendationItem]])
async def get_similar_events(
    eventId: str,
    limit: int = Query(5, ge=1, le=20, description="Số lượng sự kiện tương tự cần lấy")
):
    """
    Gợi ý sự kiện tương tự (Similar Events) sử dụng OpenAI Embeddings & Cosine Similarity.
    Hiển thị ở chân trang chi tiết sự kiện.
    """
    items = await recommend_service.get_similar_events(event_id=eventId, limit=limit)
    return ApiResponse(
        status=0,
        message="Thành công",
        data=items
    )
