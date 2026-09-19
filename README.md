# Cartesia Voice Studio & Telegram Bot

This repository contains:
1. **Android App**: Native Android app written in Kotlin & Jetpack Compose for Cartesia Voice Generation & Voice Cloning.
2. **Telegram Bot**: Python-based Telegram bot powered by Cartesia Sonic AI, ready to deploy 24/7 on Render.

---

## 📱 1. Android App (Kotlin + Jetpack Compose)

### Features:
- 🌐 Multi-language selection (English, Spanish, French, German, Japanese, Chinese, Portuguese)
- 🎙️ Text-to-Speech generation powered by Cartesia Sonic API (`sk_car_x62gquQgEdVchAVtPCxcue`)
- 🧬 Voice Cloning with built-in voice recorder & sample creator
- 🎧 Telegram-styled interactive chat UI with animated audio waves, speed controls (1x, 1.25x, 1.5x, 2x), and seeking

### Build with Android Studio:
1. Open this folder in **Android Studio**.
2. Sync Gradle and build the app.
3. Run on an Android device or emulator.

### Build APK via GitHub Actions (Automatic):
- Whenever you push to `main` or `master`, GitHub Actions will automatically compile the Android APK.
- Go to the **Actions** tab on your GitHub repository.
- Click on the latest workflow run -> download the generated **`cartesia-voice-bot-debug-apk`** directly to your phone!
- You can also run it manually anytime from GitHub Actions using the **Run workflow** button.

---

## 🤖 2. Telegram Bot (Deploy to Render)

The Telegram bot is located in `telegram_bot/`.

### Deployment to Render in 3 Steps:
1. Push this repository to **GitHub**.
2. Go to **[dashboard.render.com](https://dashboard.render.com)** -> **New** -> **Blueprint**.
3. Select this GitHub repository.
4. Set your `TELEGRAM_BOT_TOKEN` (get it from [@BotFather](https://t.me/BotFather)).
5. Click **Apply**. Render will automatically launch the worker!

### Local Run:
```bash
cd telegram_bot
pip install -r requirements.txt
export TELEGRAM_BOT_TOKEN="your_telegram_bot_token"
export CARTESIA_API_KEY="sk_car_x62gquQgEdVchAVtPCxcue"
python bot.py
```
