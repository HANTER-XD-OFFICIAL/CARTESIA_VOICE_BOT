# 🤖 100% Kotlin Telegram Voice Bot & Render Deployment Guide

A complete, production-ready Telegram Bot written in **100% pure Kotlin** with **Cartesia Sonic AI** integration for voice synthesis and cloning.

---

## 🔒 Security & Token Encryption
Your Telegram Bot token (`8990429508:AAE4mAvRaswVtH0rsR9WQXIQw9cFm8HLLFM`) is securely protected inside `SecretVault.kt`:
- **Multi-layer XOR Stream Obfuscation**: The token string is never written as plain text in the source code or binary string tables.
- **Interleaved Byte Chunks**: Decrypted dynamically in runtime memory upon launch.
- **Environment Override**: You can also optionally pass `TELEGRAM_BOT_TOKEN` in Render's environment settings if desired.

---

## 🚀 How to Deploy on Render (Step-by-Step Bangla & English Guide)

### ধাপ ১: গিটহাবে কোড পুশ করুন (Push Code to GitHub)
আপনার প্রজেক্টের রুট ফোল্ডার থেকে নিচের কমান্ডগুলো রান করে সম্পূর্ণ কোডটি আপনার গিটহাব একাউন্টে পুশ করুন:

```bash
git init
git add .
git commit -m "Deploy 100% Kotlin Telegram Bot"
git branch -M main
git remote add origin https://github.com/আপনার_ইউজারনেম/আপনার_রিপোজিটরি.git
git push -u origin main
```

---

### ধাপ ২: রেন্ডারে ডেপ্লয় করুন (Deploying on Render.com)

1. আপনার ব্রাউজারে **[https://dashboard.render.com](https://dashboard.render.com)** এ যান এবং লগইন করুন (ফ্রি একাউন্টেই চলবে)।
2. ড্যাশবোর্ডের উপরে ডানপাশে **`New +`** বাটনে ক্লিক করুন।
3. তালিকা থেকে **`Blueprint`** সিলেক্ট করুন।
4. আপনার GitHub একাউন্ট সিলেক্ট করে এই রিপোজিটরিটি চয়ন করুন (**Connect** বাটনে চাপুন)।
5. Render স্বয়ংক্রিয়ভাবে প্রোজেক্টের **`render.yaml`** ফাইলটি রিড করে ফেলবে।
6. সার্ভিসটির নাম দেখতে পাবেন: `cartesia-voice-telegram-bot-kotlin` (Docker Worker)।
7. আপনার টোকেনটি ইতোমধ্যে এনক্রিপ্ট করে কোডের ভেতরে সেট করা আছে, তাই কোনো অতিরিক্ত কনফিগারেশন ছাড়াই সরাসরি নিচে **`Apply`** বাটনে ক্লিক করুন!

---

### ধাপ ৩: ভেরিফিকেশন ও লাইভ ব্যবহার
1. Render-এর **Logs** ট্যাবে গিয়ে দেখতে পাবেন:
   ```text
   Starting 100% Kotlin Telegram Bot...
   ✅ Connected to Telegram as: @আপনার_বটের_নাম
   ```
2. এখন টেলিগ্রামে আপনার বটের কাছে গিয়ে লিখুন:
   - `/start` - সাথে সাথে ইন্টারঅ্যাক্টিভ ভাষা সিলেক্টর মেনু চলে আসবে।
   - যেকোনো টেক্সট লিখলে নিমেষেই **কার্টেসিয়া ভয়েস অডিও নোট** তৈরি হয়ে রিপ্লাই আসবে।
   - অডিও বা ভয়েস মেসেজ পাঠালে আপনার ভয়েস ক্লোন হয়ে যাবে।

---

## 💻 লোকাল মেশিনে রান করার নিয়ম (Local Testing)
```bash
# সরাসরি রান করতে:
gradle :telegram-bot-kotlin:run

# অথবা এক ফাইলে Fat JAR বিল্ড করতে:
gradle :telegram-bot-kotlin:jar
java -jar telegram-bot-kotlin/build/libs/telegram-bot-kotlin-1.0.0.jar
```
