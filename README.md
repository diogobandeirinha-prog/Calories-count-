# Calories Count

An Android app that estimates the **calories** and **macronutrients
(protein, carbs, fats)** of a meal — from a **photo** *or* a **spoken
description** — lets you fine-tune the numbers, and tracks your **daily,
weekly and monthly** totals.

## How it works

### 📷 Photo (vision) logging
1. **Snap or pick a photo** of your meal (Camera or Gallery).
2. The photo goes to **Claude's vision model**, which identifies each distinct
   **ingredient**, estimates its **weight in grams**, and returns precise
   **calories, protein, carbs and fats** per item plus meal totals.

### 🎙️ Voice logging
1. Tap **“Speak a meal”** and say something like
   *“I had 150 grams of grilled chicken breast and a cup of cooked white rice.”*
2. Android's speech recognizer transcribes it; **Claude parses the transcript**
   into structured entries — resolving household measures (“a cup”) to grams and
   estimating calories + macros for each food.

### Review & track
3. Either way, you **review and edit** the breakdown before saving — AI
   estimates are approximate, so adjusting the numbers gives you precise totals.
4. The **Stats** tab sums everything for **Today** and breaks it down by
   **day, week and month**, with progress bars against your daily goals.

## Claude API design

Both the photo and voice paths hit Anthropic's Messages API (`/v1/messages`)
with:

- **Model `claude-opus-4-8`** — the most capable model, for best
  portion/macro accuracy (configurable in Settings).
- **Adaptive thinking** (`thinking: {type: "adaptive"}`) + **`effort: "high"`** —
  the model reasons about portions before answering.
- **Structured outputs** (`output_config.format` with a JSON schema) — the
  response is guaranteed-valid JSON matching the app's nutrition model, so there
  is no brittle text parsing.

> Prompt caching is intentionally **not** used: the system prompt is well under
> the model's ~4 K-token minimum cacheable prefix, so a cache breakpoint would
> never actually engage.

## Setup

The app needs a Claude API key (the analysis runs against Anthropic's API):

1. Create a key at <https://console.anthropic.com>.
2. Open the app → **Settings** → paste the key and (optionally) change the model
   or your daily calorie/protein goals → **Save settings**.

The key is stored **only on your device** (Jetpack DataStore) and is sent only
to Anthropic when analysing a photo.

## Building

Open the project in **Android Studio** (Ladybug or newer) and run the `app`
configuration on a device/emulator, or from the command line:

```bash
./gradlew assembleDebug
```

Requirements:

- Android Studio with the Android SDK (compileSdk 35)
- JDK 17
- minSdk 26 (Android 8.0)

> Note: this project was generated in a sandbox without access to Google's
> Android Maven repository or the Android SDK, so the APK build could not be
> compiled/verified there. Build it once in Android Studio (which has the SDK)
> to generate the Gradle/AGP caches.

## Architecture

Single-module app, MVVM, 100% Jetpack Compose (Material 3).

| Layer | Pieces |
|-------|--------|
| UI | `ui/AppRoot.kt` (bottom-nav scaffold), `ui/screens/*` (Capture, History, Settings), `ui/components/*` |
| ViewModel | `CaptureViewModel`, `StatsViewModel`, `SettingsViewModel` |
| Data | Room (`data/db/*`), DataStore (`data/prefs/*`), Claude API (`data/remote/*`), `FoodRepository` |
| Stats | `data/repository/NutritionStats.kt` buckets entries into day/week/month in the device time zone |
| Util | `util/ImageUtils.kt` — downscale, EXIF-rotate, JPEG + base64 encode |

### Tech

- Kotlin 2.0, Jetpack Compose + Material 3
- Room (KSP) for the meal log
- DataStore Preferences for settings
- OkHttp + kotlinx.serialization for the Anthropic Messages API
- Coil for image loading
- `java.time` for date bucketing (minSdk 26)

## Privacy

- Meal data and the API key live only on the device (local DB + DataStore).
- Photos are downscaled and sent to Anthropic solely to produce the estimate.
- Voice is transcribed on-device by Android's speech recognizer; only the
  resulting **text** is sent to Anthropic — never the audio.
