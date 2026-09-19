# 🚀 Render & GitHub Deployment Guide (ফুল প্রজেক্ট সেটআপ গাইড)

এই গাইডে দেখানো হয়েছে কীভাবে আপনার পুরো প্রজেক্টটি GitHub-এ রেখে **Render** দিয়ে ব্যাকগ্রাউন্ডে টেলিগ্রাম বট ২৪ ঘণ্টা চালাবেন এবং **GitHub Actions** দিয়ে সরাসরি অ্যান্ড্রয়েড অ্যাপ (APK) ডাউনলোড করবেন।

---

## 🏗️ পুরো আর্কিটেকচার কীভাবে কাজ করবে?

1. **GitHub Repository**: আপনার মূল কোডবেজ থাকবে GitHub-এ (Android অ্যাপ + Telegram বট)।
2. **GitHub Actions (অ্যান্ড্রয়েড অ্যাপ বিল্ডার)**: আপনি কোড পুশ করলেই GitHub সম্পূর্ণ স্বয়ংক্রিয়ভাবে APK বিল্ড করে দেবে, যা ফোনে ইন্সটল করা যাবে।
3. **Render (টেলিগ্রাম বট হোস্ট)**: রেন্ডারের ক্লাউডে পাইথন বটটি ২৪/৭ ব্যাকগ্রাউন্ড সার্ভিস (Worker) হিসেবে চলবে। টেলিগ্রামে যে কেউ মেসেজ পাঠালে কার্টেসিয়া এআই দিয়ে ভয়েস পাঠিয়ে দেবে।

---

## 📋 ধাপ ১: Telegram Bot Token সংগ্রহ করা

১. আপনার টেলিগ্রাম অ্যাপে গিয়ে সার্চ করুন **`@BotFather`**।  
২. `/start` লিখে পাঠান এবং তারপর `/newbot` কমান্ড দিন।  
৩. আপনার বটের একটি নাম এবং একটি ইউজারনেম (যা শেষে `bot` থাকতে হবে, যেমন: `cartesia_voice_clone_bot`) দিন।  
৪. BotFather আপনাকে একটি **HTTP API Token** দেবে (যেমন: `7123456789:AAHk...`)। এটি কপি করে সংরক্ষণ করুন।

---

## 📋 ধাপ ২: কোড GitHub-এ আপলোড (Push) করা

১. [github.com](https://github.com)-এ গিয়ে একটি নতুন রিপোজিটরি (New Repository) তৈরি করুন (যেমন: `cartesia-voice-studio`)।  
২. আপনার টার্মিনাল বা কম্পিউটারে নিচের কমান্ডগুলো রান করে সম্পূর্ণ প্রজেক্ট পুশ করুন:

```bash
git init
git add .
git commit -m "Initial commit of Android App and Telegram Bot"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

*(নোট: আপনি চাইলে AI Studio-র উপরের ডানপাশের সেটিংস/মেনু থেকে সরাসরি **Push to GitHub** বা **Export ZIP** অপশন ব্যবহার করতে পারেন।)*

---

## 📋 ধাপ ৩: Render-এ টেলিগ্রাম বট ডেপ্লয় করা (১ ক্লিকে Blueprint দিয়ে)

আমরা প্রজেক্টের রুটে ইতিমধ্যে **`render.yaml`** ফাইল তৈরি করে রেখেছি, তাই রেন্ডারে কোনো জটিল কনফিগারেশন করতে হবে না।

১. ব্রাউজারে যান: **[https://dashboard.render.com](https://dashboard.render.com)**  
২. আপনার GitHub অ্যাকাউন্ট দিয়ে লগইন করুন (ফ্রি টায়ার যথেষ্ট)।  
৩. ড্যাশবোর্ডের ডানপাশে **New +** বাটনে ক্লিক করুন।  
৪. অপশনগুলো থেকে **Blueprint** সিলেক্ট করুন।  
৫. আপনার GitHub রিপোজিটরিটি সিলেক্ট করে **Connect** দিন।  
৬. রেন্ডার আপনার `render.yaml` ফাইলটি স্বয়ংক্রিয়ভাবে পড়ে ফেলবে এবং একটি এনভায়রনমেন্ট ভ্যারিয়েবল চাইবে:
   - **`TELEGRAM_BOT_TOKEN`**: এখানে ধাপ ১-এ BotFather থেকে পাওয়া টোকেনটি পেস্ট করুন।
   - *(নোট: `CARTESIA_API_KEY` স্বয়ংক্রিয়ভাবে `render.yaml` থেকে সেট হয়ে যাবে)*
৭. নিচে **Apply** বাটনে ক্লিক করুন।

🎉 **ব্যাস!** রেন্ডার কয়েক সেকেন্ডের মধ্যে লাইব্রেরি ইনস্টল করে আপনার বট ২৪ ঘণ্টার জন্য চালু করে দেবে। এবার টেলিগ্রামে আপনার বটের কাছে গিয়ে `/start` লিখে টেস্ট করুন!

---

## 📋 ধাপ ৪: Android APK ডাউনলোড করা (GitHub Actions থেকে)

আমরা প্রজেক্টে **`/.github/workflows/android-build.yml`** যোগ করেছি:

১. আপনার GitHub রিপোজিটরির পেজে যান।  
২. উপরে **Actions** ট্যাবে ক্লিক করুন।  
৩. বামপাশে **"Build & Release Android APK"** দেখতে পাবেন।  
৪. আপনার প্রতিটি `git push`-এর সাথে সাথে এটি স্বয়ংক্রিয়ভাবে APK বিল্ড করে ফেলে। আপনি চাইলে ডানপাশে **"Run workflow"** ক্লিক করে এখনই ম্যানুয়ালি বিল্ড চালু করতে পারেন।  
৫. বিল্ড শেষ হলে (সবুজ টিক চিহ্ন আসলে) রানটিতে ক্লিক করুন।  
৬. পেজের নিচের দিকে **Artifacts** সেকশনে **`cartesia-voice-bot-debug-apk`** নামের জিপ ফাইল পাবেন। এটি ডাউনলোড করে আনজিপ করলেই পাবেন অ্যান্ড্রয়েড ইন্সটলার `.apk` ফাইল!

---

## 🔧 локаল টেস্ট বা ট্রাবলশুটিং (Local Testing)

যদি আপনি আপনার কম্পিউটারে টেলিগ্রাম বটটি রান করতে চান:

```bash
cd telegram_bot
pip install -r requirements.txt
export TELEGRAM_BOT_TOKEN="আপনার_টেলিগ্রাম_টোকেন"
export CARTESIA_API_KEY="sk_car_x62gquQgEdVchAVtPCxcue"
python bot.py
```
Windows PowerShell-এ:
```powershell
cd telegram_bot
pip install -r requirements.txt
$env:TELEGRAM_BOT_TOKEN="আপনার_টেলিগ্রাম_টোকেন"
$env:CARTESIA_API_KEY="sk_car_x62gquQgEdVchAVtPCxcue"
python bot.py
```
