# Cartesia Voice & Cloning Telegram Bot (Render Ready)
# -------------------------------------------------------------
# Features:
# 1. /start: Welcome greeting with Interactive Language selection keyboard
# 2. Cartesia Sonic AI Text-to-Speech (TTS)
# 3. Cartesia Voice Cloning from voice notes/audio clips
# 4. In-memory per-user settings (Language, Voice ID, Voice Name)
# 5. Render 24/7 background worker execution
# -------------------------------------------------------------

import os
import io
import json
import logging
import asyncio
from typing import Dict, Any

import httpx
from telegram import (
    Update,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
)
from telegram.constants import ChatAction, ParseMode
from telegram.ext import (
    Application,
    CommandHandler,
    CallbackQueryHandler,
    MessageHandler,
    ContextTypes,
    filters,
)

# ---------------- Logging ----------------
logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    level=logging.INFO
)
logger = logging.getLogger(__name__)

# ---------------- Environment Variables ----------------
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
CARTESIA_API_KEY = os.getenv("CARTESIA_API_KEY", "sk_car_x62gquQgEdVchAVtPCxcue")

CARTESIA_BASE_URL = "https://api.cartesia.ai"
CARTESIA_VERSION = "2024-06-10"

# Supported Languages for Sonic
LANGUAGES = {
    "en": {"name": "English 🇺🇸", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "es": {"name": "Spanish 🇪🇸", "default_voice": "846d35e9-dc05-4526-ba13-34c47fb6f6fe"},
    "fr": {"name": "French 🇫🇷", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "de": {"name": "German 🇩🇪", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "ja": {"name": "Japanese 🇯🇵", "default_voice": "2b568345-1d48-4047-b25f-7baccf842eb0"},
    "pt": {"name": "Portuguese 🇧🇷", "default_voice": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "zh": {"name": "Chinese 🇨🇳", "default_voice": "2b568345-1d48-4047-b25f-7baccf842eb0"},
}

# Preset Curated Cartesia Voices
PRESET_VOICES = {
    "barbershop": {"name": "Barbershop Man 🎙️", "id": "a0e99841-438c-4a64-b679-ae501e7d6091"},
    "calm_lady": {"name": "Calm Lady 🌸", "id": "846d35e9-dc05-4526-ba13-34c47fb6f6fe"},
    "storyteller": {"name": "Storyteller 📖", "id": "2b568345-1d48-4047-b25f-7baccf842eb0"},
    "friendly": {"name": "Friendly Assistant ⚡", "id": "69267136-1bdc-4106-96a6-1c024d3f9aa9"},
}

# ---------------- User State ----------------
def get_user_state(context: ContextTypes.DEFAULT_TYPE) -> Dict[str, Any]:
    if "settings" not in context.user_data:
        context.user_data["settings"] = {
            "language": "en",
            "voice_id": "a0e99841-438c-4a64-b679-ae501e7d6091",
            "voice_name": "Barbershop Man 🎙️",
            "awaiting_clone": False,
        }
    return context.user_data["settings"]

# ---------------- Inline Keyboards ----------------
def get_language_keyboard() -> InlineKeyboardMarkup:
    keyboard = []
    row = []
    for code, data in LANGUAGES.items():
        row.append(InlineKeyboardButton(data["name"], callback_data=f"lang_{code}"))
        if len(row) == 2:
            keyboard.append(row)
            row = []
    if row:
        keyboard.append(row)
    return InlineKeyboardMarkup(keyboard)

def get_main_menu_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([
        [
            InlineKeyboardButton("🎙️ Select Voice", callback_data="menu_voices"),
            InlineKeyboardButton("🌐 Change Language", callback_data="menu_lang"),
        ],
        [
            InlineKeyboardButton("🧬 Clone a Voice", callback_data="menu_clone"),
            InlineKeyboardButton("ℹ️ Active Config", callback_data="menu_status"),
        ]
    ])

def get_voice_selection_keyboard() -> InlineKeyboardMarkup:
    keyboard = []
    for key, voice in PRESET_VOICES.items():
        keyboard.append([InlineKeyboardButton(voice["name"], callback_data=f"voice_{voice['id']}_{key}")])
    keyboard.append([InlineKeyboardButton("🔙 Back to Main Menu", callback_data="menu_main")])
    return InlineKeyboardMarkup(keyboard)

# ---------------- Cartesia API Calls ----------------
async def cartesia_tts(text: str, voice_id: str, language: str) -> bytes:
    """Generate audio bytes via Cartesia Sonic TTS."""
    headers = {
        "X-API-Key": CARTESIA_API_KEY,
        "Cartesia-Version": CARTESIA_VERSION,
        "Content-Type": "application/json",
    }
    payload = {
        "model_id": "sonic",
        "transcript": text,
        "voice": {
            "mode": "id",
            "id": voice_id,
        },
        "output_format": {
            "container": "wav",
            "encoding": "pcm_s16le",
            "sample_rate": 44100,
        },
        "language": language,
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(
            f"{CARTESIA_BASE_URL}/tts/bytes",
            headers=headers,
            json=payload,
        )
        if response.status_code != 200:
            logger.error(f"Cartesia TTS failed ({response.status_code}): {response.text}")
            raise RuntimeError(f"Cartesia TTS failed: {response.text}")
        return response.content

async def cartesia_clone_voice(audio_bytes: bytes, voice_name: str) -> Dict[str, Any]:
    """Clone a voice using Cartesia /voices/clone/clip endpoint."""
    headers = {
        "X-API-Key": CARTESIA_API_KEY,
        "Cartesia-Version": CARTESIA_VERSION,
    }
    files = {
        "clip": ("sample.wav", audio_bytes, "audio/wav")
    }
    data = {
        "name": voice_name,
        "description": "Cloned via Cartesia Telegram Bot",
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(
            f"{CARTESIA_BASE_URL}/voices/clone/clip",
            headers=headers,
            data=data,
            files=files,
        )
        if response.status_code not in (200, 201):
            logger.error(f"Cartesia Clone failed ({response.status_code}): {response.text}")
            raise RuntimeError(f"Cartesia Clone error: {response.text}")
        return response.json()

# ---------------- Handlers ----------------
async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Start command displays welcome greeting and requests language choice."""
    user = update.effective_user
    welcome_text = (
        f"👋 **Hello {user.first_name}!**\n\n"
        "Welcome to the **Cartesia Sonic Voice Bot**! 🎙️\n"
        "I convert any text message into realistic speech and can also **clone your voice** from audio clips.\n\n"
        "👉 **Step 1:** Please select your preferred language below:"
    )
    await update.message.reply_text(
        welcome_text,
        reply_markup=get_language_keyboard(),
        parse_mode=ParseMode.MARKDOWN
    )

async def button_callback_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles all inline button selections."""
    query = update.callback_query
    await query.answer()

    data = query.data
    settings = get_user_state(context)

    if data.startswith("lang_"):
        lang_code = data.replace("lang_", "")
        if lang_code in LANGUAGES:
            settings["language"] = lang_code
            lang_name = LANGUAGES[lang_code]["name"]
            response_text = (
                f"✅ **Language set to:** {lang_name}\n\n"
                "You can now:\n"
                "• Send any text message to generate speech immediately 💬\n"
                "• Pick a different voice 🎙️\n"
                "• Clone your own voice 🧬"
            )
            await query.edit_message_text(
                response_text,
                reply_markup=get_main_menu_keyboard(),
                parse_mode=ParseMode.MARKDOWN
            )

    elif data == "menu_main":
        await query.edit_message_text(
            "🎛️ **Main Menu:**\nSelect an option or type any message to convert to voice:",
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_lang":
        await query.edit_message_text(
            "🌐 **Select your preferred language:**",
            reply_markup=get_language_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_voices":
        await query.edit_message_text(
            "🎙️ **Choose a Cartesia Voice:**",
            reply_markup=get_voice_selection_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data.startswith("voice_"):
        parts = data.split("_")
        voice_id = parts[1]
        voice_key = parts[2] if len(parts) > 2 else "custom"
        voice_name = PRESET_VOICES.get(voice_key, {}).get("name", "Custom Voice")

        settings["voice_id"] = voice_id
        settings["voice_name"] = voice_name

        await query.edit_message_text(
            f"✅ **Voice configured to:** {voice_name}\n\n"
            "Now send any text message and I will speak it for you!",
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_status":
        lang_display = LANGUAGES.get(settings['language'], {}).get('name', settings['language'])
        status_text = (
            "⚙️ **Active Configuration:**\n\n"
            f"🌐 **Language:** {lang_display}\n"
            f"🎙️ **Voice:** {settings['voice_name']}\n"
            f"🔑 **Voice ID:** `{settings['voice_id']}`\n\n"
            "💬 *Send me any text message to generate voice!*"
        )
        await query.edit_message_text(
            status_text,
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

    elif data == "menu_clone":
        settings["awaiting_clone"] = True
        clone_prompt = (
            "🧬 **Voice Cloning Mode**\n\n"
            "1. Record a **Voice Note** (5-15 seconds) with your microphone, OR upload a clean `.wav`/`.mp3` audio.\n"
            "2. Send it directly here.\n"
            "3. Cartesia AI will clone it and set it as your active voice!"
        )
        await query.edit_message_text(
            clone_prompt,
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("❌ Cancel", callback_data="menu_main")
            ]]),
            parse_mode=ParseMode.MARKDOWN
        )

async def text_message_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Takes user text and synthesizes speech using Cartesia Sonic."""
    text = update.message.text.strip()
    if not text:
        return

    settings = get_user_state(context)
    voice_id = settings["voice_id"]
    language = settings["language"]

    await update.message.chat.send_action(ChatAction.RECORD_VOICE)
    status_msg = await update.message.reply_text("🔊 *Synthesizing voice with Cartesia Sonic...*", parse_mode=ParseMode.MARKDOWN)

    try:
        audio_bytes = await cartesia_tts(text=text, voice_id=voice_id, language=language)
        await status_msg.delete()

        audio_file = io.BytesIO(audio_bytes)
        audio_file.name = "speech.wav"

        caption = f"🎙️ *Voice:* {settings['voice_name']} ({language.upper()})"

        await update.message.reply_voice(
            voice=audio_file,
            caption=caption,
            parse_mode=ParseMode.MARKDOWN,
            reply_markup=get_main_menu_keyboard()
        )
    except Exception as e:
        logger.error(f"Error generating voice: {e}")
        await status_msg.edit_text(f"❌ **Failed to generate audio:** `{str(e)}`", parse_mode=ParseMode.MARKDOWN)

async def voice_or_audio_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    """Handles audio or voice notes sent by the user to clone their voice."""
    settings = get_user_state(context)

    await update.message.chat.send_action(ChatAction.TYPING)
    status_msg = await update.message.reply_text("📥 *Receiving audio clip...*", parse_mode=ParseMode.MARKDOWN)

    try:
        if update.message.voice:
            file_obj = await update.message.voice.get_file()
        elif update.message.audio:
            file_obj = await update.message.audio.get_file()
        else:
            await status_msg.edit_text("❌ Please send a valid voice note or audio file.")
            return

        audio_bytes = await file_obj.download_as_bytearray()
        await status_msg.edit_text("🧬 *Cloning voice with Cartesia AI...*", parse_mode=ParseMode.MARKDOWN)

        user_name = update.effective_user.first_name or "User"
        voice_name = f"{user_name}'s Clone"

        clone_result = await cartesia_clone_voice(
            audio_bytes=bytes(audio_bytes),
            voice_name=voice_name
        )

        cloned_voice_id = clone_result.get("id") or clone_result.get("voice_id")
        if not cloned_voice_id:
            raise ValueError(f"No voice ID returned from Cartesia: {clone_result}")

        settings["voice_id"] = cloned_voice_id
        settings["voice_name"] = f"🧬 {voice_name}"
        settings["awaiting_clone"] = False

        success_text = (
            "🎉 **Voice Cloned Successfully!**\n\n"
            f"• **Voice Name:** {voice_name}\n"
            f"• **Voice ID:** `{cloned_voice_id}`\n\n"
            "This voice is now your **active voice**! Send me any text to hear it speak in your cloned voice."
        )
        await status_msg.edit_text(
            success_text,
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )
    except Exception as e:
        logger.error(f"Cloning error: {e}")
        await status_msg.edit_text(
            f"❌ **Failed to clone voice:** `{str(e)}`\n\nMake sure the audio has at least 5 seconds of clear speech.",
            reply_markup=get_main_menu_keyboard(),
            parse_mode=ParseMode.MARKDOWN
        )

# ---------------- Application Entrypoint ----------------
def main() -> None:
    if not TELEGRAM_BOT_TOKEN:
        logger.error("TELEGRAM_BOT_TOKEN environment variable is not set!")
        print("CRITICAL ERROR: Please set TELEGRAM_BOT_TOKEN environment variable.")
        return

    application = Application.builder().token(TELEGRAM_BOT_TOKEN).build()

    application.add_handler(CommandHandler("start", start_command))
    application.add_handler(CommandHandler("menu", start_command))
    application.add_handler(CallbackQueryHandler(button_callback_handler))
    application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, text_message_handler))
    application.add_handler(MessageHandler(filters.VOICE | filters.AUDIO, voice_or_audio_handler))

    logger.info("Cartesia Telegram Bot started successfully! Listening for messages...")
    application.run_polling(drop_pending_updates=True)

if __name__ == "__main__":
    main()
