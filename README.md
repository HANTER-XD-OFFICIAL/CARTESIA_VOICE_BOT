# Cartesia Voice Bot (100% Kotlin Android App)

A 100% Kotlin and Jetpack Compose native Android application. It provides an authentic Telegram Bot chat experience with Cartesia Sonic Text-to-Speech (TTS) and Voice Cloning, built natively without Python or external backend scripts.

## 📱 Features

- **100% Pure Kotlin & Jetpack Compose**: Pure native Android project.
- **Telegram Bot Chat Experience**:
  - Interactive bot conversation flow (starts with language selection chips/buttons)
  - Inline bot buttons for quick actions (Voice Selection, Voice Cloning, Help)
  - Telegram-themed message bubbles with timestamps and read receipts
- **Cartesia Sonic TTS**:
  - Direct integration with Cartesia Sonic API (`sk_car_x62gquQgEdVchAVtPCxcue`)
  - Multi-language support (English, Spanish, French, German, Japanese, Portuguese, Chinese)
  - Voice selection among top expressive voices
- **Voice Cloning Studio**:
  - Record audio samples directly inside the app with the microphone
  - Clone voice in seconds using Cartesia Voice Cloning endpoint
  - Instantly switch to your newly cloned voice
- **Integrated Audio Player**:
  - Waveform visualizer
  - Playback speed control (1.0x, 1.25x, 1.5x, 2.0x)
  - Progress scrubbing and seeking

---

## 🚀 Building the APK via GitHub Actions

This repository includes a pre-configured GitHub Actions workflow in `.github/workflows/android-build.yml`.

1. Push this repository to your GitHub account:
   ```bash
   git init
   git add .
   git commit -m "Pure Kotlin Android App"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
   git push -u origin main
   ```
2. Navigate to the **Actions** tab on your GitHub repository.
3. Click on the **Build & Release Android APK** workflow (or click **Run workflow**).
4. When the build completes, download the **`cartesia-voice-bot-debug-apk`** artifact to get your ready-to-install `.apk` file!
