import time
from typing import Generic, List, Optional, TypeVar
from pydantic import BaseModel, Field

T = TypeVar("T")


class EventCard(BaseModel):
    id: str
    title: str
    description: Optional[str] = None
    categoryName: Optional[str] = None
    venue: Optional[str] = None
    address: Optional[str] = None
    startDate: Optional[str] = None
    endDate: Optional[str] = None
    bannerUrl: Optional[str] = None
    minPrice: Optional[float] = None
    maxPrice: Optional[float] = None
    status: Optional[str] = "PUBLISHED"


class ChatMessage(BaseModel):
    role: str  # "user" | "assistant" | "system"
    content: str


class ChatRequest(BaseModel):
    message: str = Field(..., description="Câu hỏi hoặc yêu cầu tư vấn của người dùng")
    conversationId: Optional[str] = Field(None, description="ID hội thoại để duy trì ngữ cảnh")
    userLocation: Optional[str] = Field(None, description="Địa điểm hiện tại của người dùng (VD: Hà Nội, TP.HCM)")
    history: Optional[List[ChatMessage]] = Field(default_factory=list, description="Lịch sử chat gần đây")


class ChatResponseData(BaseModel):
    reply: str
    suggestedEvents: List[EventCard] = Field(default_factory=list)
    followUpQuestions: List[str] = Field(default_factory=list)


class RecommendationItem(BaseModel):
    eventId: str
    title: str
    categoryName: Optional[str] = None
    venue: Optional[str] = None
    bannerUrl: Optional[str] = None
    minPrice: Optional[float] = None
    maxPrice: Optional[float] = None
    matchScore: float = Field(..., ge=0.0, le=1.0)
    reasons: List[str] = Field(default_factory=list)


class ApiResponse(BaseModel, Generic[T]):
    status: int = 0
    message: str = "Thành công"
    data: Optional[T] = None
    responseTime: int = Field(default_factory=lambda: int(time.time() * 1000))
