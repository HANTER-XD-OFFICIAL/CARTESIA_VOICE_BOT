# 🤖 Cartesia Voice & Cloning Telegram Bot (Render Deployment)

A full-featured Telegram Bot integrating the **Cartesia Sonic AI** Voice API (`sk_car_x62gquQgEdVchAVtPCxcue`) for real-time Text-to-Speech (TTS) and Voice Cloning, pre-configured for 24/7 deployment on **Render**.

---

## 🌟 Features

1. **Interactive Language Selection**: Users are prompted on `/start` with inline buttons to select their language (English, Spanish, French, German, Japanese, Portuguese, Chinese).
2. **Instant Text-to-Speech**: Any regular text sent to the bot is instantly converted to a realistic audio voice note via Cartesia Sonic.
3. **Voice Cloning**: Send any voice note or audio clip (5–15 seconds) to clone the voice and immediately start generating speech with the newly cloned voice!
4. **Voice Switcher**: Choose from curated expressive voices (Barbershop Man, Calm Lady, Storyteller, Friendly Assistant).
5. **Render Ready**: Includes `render.yaml`, `Dockerfile`, and `requirements.txt` for 1-click 24/7 background worker deployment.

---

## 🚀 How to Deploy on Render (Step-by-Step)

### Step 1: Create your Telegram Bot Token
1. Open Telegram and search for **[@BotFather](https://t.me/BotFather)**.
2. Send `/start` and then `/newbot`.
3. Give your bot a name and username (e.g. `cartesia_sonic_voice_bot`).
4. Copy the **HTTP API Token** provided by BotFather (e.g. `7123456789:AAHk...`).

### Step 2: Push this Repository to GitHub
Push this codebase to your GitHub account:
```bash
git init
git add .
git commit -m "Add Telegram Bot with Cartesia Sonic & Render Blueprint"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

### Step 3: Deploy to Render
1. Go to **[dashboard.render.com](https://dashboard.render.com)** and sign in.
2. Click **New +** (top right) -> select **Blueprint**.
3. Connect your GitHub repository.
4. Render will detect `render.yaml` automatically.
5. In the prompt for `TELEGRAM_BOT_TOKEN`, paste your token from BotFather.
6. Click **Apply**.

Render will build the dependencies and run the worker 24/7!

---

## 💻 Local Testing

```bash
pip install -r requirements.txt
export TELEGRAM_BOT_TOKEN="your_bot_token_from_botfather"
export CARTESIA_API_KEY="sk_car_x62gquQgEdVchAVtPCxcue"
python bot.py
```
