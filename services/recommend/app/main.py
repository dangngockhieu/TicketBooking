import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import recommendations

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info(f"Starting {settings.APP_NAME} on port {settings.PORT}...")
    if settings.OPENAI_API_KEY:
        logger.info(f"OpenAI API Key detected! Model: {settings.OPENAI_MODEL}")
    else:
        logger.warning("OPENAI_API_KEY is not set. Service will run in smart rule-based fallback mode.")
    yield
    logger.info("Shutting down Recommend Service...")


app = FastAPI(
    title="TicketBooking AI Recommendation & Chatbot Service",
    description="Microservice cung cấp API gợi ý sự kiện thông minh và Trợ lý ảo tư vấn vé bằng OpenAI (ChatGPT + Embeddings)",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/v3/api-docs",
    lifespan=lifespan
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include Routers
app.include_router(recommendations.router)


@app.get("/", tags=["Health"])
async def root():
    return {
        "service": settings.APP_NAME,
        "status": "UP",
        "openai_configured": bool(settings.OPENAI_API_KEY),
        "docs": "/docs"
    }


@app.get("/actuator/health", tags=["Health"])
async def actuator_health():
    """Spring Boot Actuator compatible healthcheck endpoint"""
    return {"status": "UP"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.PORT, reload=settings.DEBUG)
