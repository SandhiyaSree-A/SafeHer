# SafeHer 🛡️

> **A women's safety navigation app that recommends the safest routes using real-time lighting, crowd density, traffic, pedestrian data, and SOS hotspot intelligence.**

> ⚠️ **Ethical Disclaimer:** All risk scores are awareness estimates for prototype/demo purposes only and are *not* a guarantee of real-world safety or crime prediction.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Setup & Installation](#setup--installation)
  - [1. Android App](#1-android-app)
  - [2. Lighting Backend (FastAPI)](#2-lighting-backend-fastapi)
  - [3. Firebase Cloud Functions](#3-firebase-cloud-functions)
  - [4. Database Setup (Supabase / PostgreSQL)](#4-database-setup-supabase--postgresql)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [Database Schema](#database-schema)
- [Firestore Security Rules](#firestore-security-rules)
- [Running Tests](#running-tests)
- [Troubleshooting](#troubleshooting)

---

## Overview

SafeHer is an Android application built with Jetpack Compose that helps users — especially women — navigate safely. It analyses multiple routes and scores each one based on:

- 🌙 **Nighttime lighting** (NASA Black Marble satellite data)
- 👥 **Human presence & crowd density** (WorldPop / OSM data)
- 🚗 **Traffic conditions** (OSRM routing)
- 🚶 **Pedestrian infrastructure** (OpenStreetMap)
- 🆘 **SOS hotspot proximity** (historical SOS alert aggregates stored in PostgreSQL/Firestore)

Users can trigger an SOS alert, monitor active journeys, store emergency contacts, and view a real-time safety map. Admins can monitor all active SOS alerts from a dedicated admin panel.

---

## Architecture

```
┌─────────────────────────────────────────┐
│            Android App (Kotlin)          │
│   Jetpack Compose · Firebase Auth        │
│   Google Maps · Room DB · WorkManager   │
└────────────────┬────────────────────────┘
                 │ REST (HTTP)
                 ▼
┌─────────────────────────────────────────┐
│     Lighting Backend (FastAPI / Python)  │
│   Route analysis · OSRM · NASA tiles    │
│   SOS event ingestion · Route feedback  │
│   Supabase PostgreSQL + PostGIS         │
└────────────────┬────────────────────────┘
                 │
        ┌────────┴────────┐
        ▼                 ▼
┌──────────────┐  ┌───────────────────────┐
│  Supabase DB │  │  Firebase (Firestore)  │
│  (PostGIS)   │  │  Auth · SOS Alerts    │
│  sos_events  │  │  Journeys · Hotspots  │
└──────────────┘  └──────────┬────────────┘
                             │ Firestore Trigger
                             ▼
                  ┌──────────────────────┐
                  │  Firebase Cloud Fn   │
                  │  scoreRoute (Flask)  │
                  │  aggregate_sos_      │
                  │  hotspot (trigger)   │
                  └──────────────────────┘
```

---

## Features

### Android App

| Feature | Description |
|---|---|
| **Authentication** | Email/password login & registration via Firebase Auth |
| **Safe Route Map** | Google Maps showing multiple scored routes colour-coded by safety |
| **Route Scoring** | Composite safety score from lighting, traffic, crowd, pedestrian & SOS factors |
| **SOS Alert** | One-tap floating SOS button; sends alert to Firestore & backend with location |
| **Journey Monitoring** | Foreground service monitors route deviation and alerts if user goes off-route |
| **Emergency Contacts** | Add/manage contacts to be notified via SMS during SOS |
| **Traffic Chatbot** | Knowledge-base powered chatbot for real-time traffic Q&A |
| **Admin Dashboard** | `security_room_admin` role can view, acknowledge, and resolve all SOS alerts on a map |
| **Offline Mode** | Room DB + WorkManager queue syncs route feedback and saved routes when offline |
| **Route Rating** | Users can rate a route safe/unsafe with reasons after completing a journey |

### Lighting Backend

| Endpoint | Method | Description |
|---|---|---|
| `/` | GET | Health check |
| `/geocode?query=` | GET | Geocode a destination using Nominatim/OSM |
| `/analyze-route` | POST | Analyse multiple OSRM routes with safety scores |
| `/sos-events` | POST | Ingest SOS alert into PostgreSQL (privacy-hashed) |
| `/route-feedback` | POST | Save user feedback for a route |
| `/safest-routes` | POST | Persist the chosen safest route |

### Firebase Cloud Functions

| Function | Trigger | Description |
|---|---|---|
| `scoreRoute` | HTTP POST | ML-based route risk scoring (joblib model) |
| `aggregate_sos_hotspot` | Firestore `onCreate` on `sos_alerts` | Aggregates SOS events into privacy-safe hotspot cells |

---

## Project Structure

```
SafeHer/
├── app/                          # Android application (Kotlin / Jetpack Compose)
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/safeher/app/
│   │       ├── MainActivity.kt
│   │       ├── data/
│   │       │   ├── model/        # Data classes (User, Journey, SosAlert, RouteOption…)
│   │       │   ├── offline/      # Room DB, DAOs, WorkManager sync worker
│   │       │   └── repository/   # Auth, SOS, Route, Contact, Location repositories
│   │       ├── service/
│   │       │   ├── SosForegroundService.kt      # Location-tracked SOS service
│   │       │   └── JourneyMonitoringService.kt  # Route deviation detection
│   │       ├── ui/
│   │       │   ├── admin/        # Admin home screen & multi-map alert view
│   │       │   ├── auth/         # Login / registration screens
│   │       │   ├── chatbot/      # Traffic chatbot screen & ViewModel
│   │       │   ├── home/         # Main home screen & ViewModel
│   │       │   ├── journey/      # Journey tab, deviation dialog, route rating
│   │       │   ├── map/          # SafeHerMap composable & offline map card
│   │       │   ├── navigation/   # NavGraph & Screen sealed class
│   │       │   ├── offline/      # Offline banner
│   │       │   ├── profile/      # Emergency contacts section & ProfileViewModel
│   │       │   ├── sos/          # SOS floating button, active alert banner & SosViewModel
│   │       │   ├── theme/        # Color, Typography, Theme
│   │       │   └── user/         # User home screen
│   │       └── util/             # PhoneUtils, RouteCache
│   ├── build.gradle.kts
│   └── google-services.json      # Firebase project config (do NOT commit secrets)
│
├── lighting_backend/             # Python FastAPI backend
│   ├── app/
│   │   ├── main.py               # FastAPI app + all endpoints
│   │   ├── config.py             # Environment config loader
│   │   ├── api/
│   │   │   └── lighting.py       # Lighting API router
│   │   ├── models/               # Pydantic models
│   │   ├── providers/            # Data provider integrations
│   │   └── services/             # Route scoring service modules
│   │       ├── routing_service.py
│   │       ├── route_safety_score_service.py
│   │       ├── lighting_service.py
│   │       ├── sos_hotspot_service.py
│   │       ├── safe_route_service.py
│   │       └── … (18 service files total)
│   ├── db/
│   │   ├── schema.sql            # Full PostgreSQL + PostGIS schema
│   │   ├── seed_chennai.sql      # Demo seed data for Chennai
│   │   └── apply_schema.py       # Schema + seed runner script
│   ├── requirements.txt
│   ├── .env                      # Secret config (do NOT commit)
│   └── README.md
│
├── functions/                    # Firebase Cloud Functions (Python / Flask)
│   ├── main.py                   # scoreRoute HTTP fn + aggregate_sos_hotspot trigger
│   ├── feature_calculator.py     # ML feature engineering for route segments
│   ├── requirements.txt
│   └── best_model.joblib         # Pre-trained joblib risk classification model
│
├── firestore.rules               # Firestore security rules
├── build.gradle.kts              # Root Gradle build
├── settings.gradle.kts           # Module settings
├── gradle.properties             # JVM args, Java home
├── local.properties              # SDK path & API keys (do NOT commit)
└── README.md
```

---

## Tech Stack

### Android App

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation |
| Auth | Firebase Authentication |
| Database (cloud) | Firebase Firestore |
| Database (local) | Room (SQLite) |
| Background sync | WorkManager |
| Maps | Google Maps SDK + Maps Compose |
| Location | Google Play Services Location |
| Build | Gradle (Kotlin DSL) |

### Backend

| Layer | Technology |
|---|---|
| Framework | FastAPI (Python) |
| Database | Supabase PostgreSQL + PostGIS |
| Routing | OSRM (Open Source Routing Machine) |
| Satellite data | NASA Black Marble (nighttime lights) |
| Population data | WorldPop API |
| OSM queries | Overpass API |
| Connection pool | psycopg3 + psycopg_pool |

### Cloud Functions

| Layer | Technology |
|---|---|
| Framework | Flask (Python) |
| ML model | scikit-learn pipeline (joblib) hosted on Hugging Face |
| Trigger | Firebase Firestore `onCreate` |

---

## Prerequisites

### Android
- **Android Studio** (Hedgehog or later)
- **JDK 21** (Android Studio bundled JBR recommended)
- **Android SDK** API 24–34
- A **Firebase project** with Authentication and Firestore enabled
- A **Google Maps API key**

### Backend
- **Python 3.10+**
- **pip**
- A **Supabase** project (or any PostgreSQL 14+ instance with PostGIS)
- (Optional) NASA Earthdata account for downloading Black Marble tiles

---

## Setup & Installation

### 1. Android App

#### a. Clone the repo
```bash
git clone https://github.com/<your-org>/SafeHer.git
cd SafeHer
```

#### b. Configure API keys
Create / edit `local.properties` in the project root:
```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
MAPTILER_API_KEY=YOUR_MAPTILER_API_KEY
```

#### c. Add Firebase config
Download `google-services.json` from your Firebase project console and place it at:
```
app/google-services.json
```

#### d. Configure Java home (Windows)
In `gradle.properties`, ensure the path points to your installed JDK:
```properties
org.gradle.java.home=C\:/Program Files/Android/Android Studio/jbr
```

#### e. Open in Android Studio & Sync
- Open the project root in Android Studio
- Click **File → Sync Project with Gradle Files**
- Run on emulator or physical device (API 24+)

---

### 2. Lighting Backend (FastAPI)

#### a. Navigate to the backend directory
```bash
cd lighting_backend
```

#### b. Create a virtual environment
```bash
python -m venv venv
# Windows
venv\Scripts\activate
# macOS/Linux
source venv/bin/activate
```

#### c. Install dependencies
```bash
pip install -r requirements.txt
pip install fastapi uvicorn python-dotenv requests psycopg[binary] psycopg_pool h5py numpy scipy
```

#### d. Configure `.env`
Create `lighting_backend/.env`:
```env
DATABASE_URL=postgresql://user:password@host:5432/dbname
SOS_HASH_SALT=your_random_secret_salt_here
SOS_SOURCE=postgres
EARTHDATA_TOKEN=your_nasa_earthdata_token
```

#### e. Run the server
```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```
API docs: `http://localhost:8000/docs`

---

### 3. Firebase Cloud Functions

#### a. Install the Firebase CLI
```bash
npm install -g firebase-tools
firebase login
```

#### b. Install Python dependencies
```bash
cd functions
pip install -r requirements.txt
```

#### c. Deploy
```bash
firebase deploy --only functions
```

---

### 4. Database Setup (Supabase / PostgreSQL)

#### a. Apply schema only
```bash
cd lighting_backend
python db/apply_schema.py
```

#### b. Apply schema with Chennai seed data
```bash
python db/apply_schema.py --seed
```

This creates:

| Table / View | Purpose |
|---|---|
| `sos_events` | Raw SOS incidents with PostGIS geometry & hashed user IDs |
| `sos_hotspots` | View of aggregated ~150 m grid hotspot cells |
| `route_trust_scores` | Per-factor scores per route analysis (used for historical blending) |
| `route_feedback` | User-submitted route safety ratings |
| `safest_routes` | Routes saved as safest upon arrival |
| `saved_offline_routes` | Frequently-safe routes eligible for offline caching |

---

## Environment Variables

### `lighting_backend/.env`

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | ✅ | PostgreSQL connection string (Supabase/PostGIS) |
| `SOS_HASH_SALT` | ✅ | HMAC-SHA256 salt for anonymising user IDs in SOS events |
| `SOS_SOURCE` | ❌ | `postgres` (default) or `firestore` |
| `EARTHDATA_TOKEN` | ❌ | NASA Earthdata bearer token for Black Marble tile downloads |
| `WORLDPOP_API_KEY` | ❌ | WorldPop API key for population density data |
| `OVERPASS_BASE_URL` | ❌ | Custom Overpass API endpoint (defaults to public instance) |

### `local.properties` (Android — do NOT commit)

| Variable | Required | Description |
|---|---|---|
| `sdk.dir` | ✅ | Path to your Android SDK |
| `MAPS_API_KEY` | ✅ | Google Maps / Places API key |
| `MAPTILER_API_KEY` | ❌ | MapTiler API key for additional map styles |

---

## API Reference

### `POST /analyze-route`

**Request:**
```json
{
  "source_lat": 13.0827,
  "source_lon": 80.2707,
  "destination_lat": 13.0569,
  "destination_lon": 80.2425,
  "mode": "driving",
  "user_id": "optional-uid",
  "session_id": "optional-journey-id",
  "destination_address": "T. Nagar, Chennai"
}
```

**Response:**
```json
{
  "status": "success",
  "total_routes": 3,
  "safest_route": {
    "route_id": "route_0",
    "distance_km": 4.2,
    "duration_minutes": 12.5,
    "coordinates": [[13.08, 80.27]],
    "safety": {
      "lighting_safety_score": 78.4,
      "average_light_score": 65.2,
      "dark_points": 2,
      "dark_stretches": 1
    }
  },
  "all_routes": [
    {
      "route_id": "route_0",
      "composite_score": 72.1,
      "traffic_score": 80.0,
      "human_presence_score": 65.0,
      "sos_hotspot_score": 90.0,
      "historical_trust": {
        "history_count": 14,
        "avg_composite_score": 70.5
      },
      "disclaimer": "Risk-awareness estimate for prototype/demo purposes only..."
    }
  ]
}
```

### `POST /sos-events`
```json
{
  "alertId": "abc-123",
  "userId": "firebase-uid",
  "lat": 13.0827,
  "lng": 80.2707,
  "timestamp": 1728900000000
}
```

### `POST /route-feedback`
```json
{
  "journey_id": "journey-uuid",
  "user_id": "firebase-uid",
  "route_id": "route_0",
  "was_safe": true,
  "reasons": "Poor lighting;No footpath",
  "transport_mode": "walking"
}
```

### `GET /geocode?query=T.Nagar Chennai`
```json
{
  "status": "success",
  "latitude": 13.0416,
  "longitude": 80.2339,
  "display_name": "T. Nagar, Chennai, Tamil Nadu, India"
}
```

---

## Database Schema

See [`lighting_backend/db/schema.sql`](lighting_backend/db/schema.sql) for the full DDL.

Key design decisions:
- **PostGIS** `GEOGRAPHY(Point,4326)` is used for all location fields, enabling spatial indexing with `GIST` and distance queries with `ST_DWithin`.
- **User privacy**: SOS events store only a `user_hash` (HMAC-SHA256), never the raw `userId`.
- **Anti-spam deduplication**: The same hashed user within a 150 m cell and 24-hour window counts only once towards hotspot counts.
- **Historical trust scores**: Every `/analyze-route` call persists per-factor scores; future calls to the same corridor blend in historical averages for improved accuracy.

---

## Firestore Security Rules

| Collection | Read | Write |
|---|---|---|
| `users/{userId}` | Public | Authenticated owner or Admin |
| `users/{userId}/emergency_contacts` | Owner or Admin | Owner or Admin |
| `sos_alerts/{alertId}` | Owner or Admin | Owner (create); Owner/Admin (update) |
| `journeys/{journeyId}` | Owner or Admin | Owner or Admin |
| `sos_hotspots/{cellId}` | Any signed-in user | Server only (Cloud Function) |
| `safest_routes/{routeId}` | Public | Any authenticated user |
| `route_feedback/{feedbackId}` | Authenticated | Any authenticated user |

See [`firestore.rules`](firestore.rules) for the complete ruleset.

---

## Running Tests

### Backend
```bash
cd lighting_backend

python app/test_sos_hotspot_service.py
python app/test_safe_routes.py
python app/test_routing.py
python app/test_complete_route.py
python app/test_complete_lighting.py
```

### Android (instrumented tests)
```bash
./gradlew connectedAndroidTest
```

---

## Troubleshooting

### `org.gradle.java.home` is invalid
Point to the Android Studio bundled JDK in `gradle.properties`:
```properties
org.gradle.java.home=C\:/Program Files/Android/Android Studio/jbr
```

### Gradle sync fails with duplicated content
If `gradle.properties` has repeated content, overwrite it with a clean version (see `gradle.properties`).

### Backend: `psycopg_pool` connection error
Ensure `DATABASE_URL` uses the Supabase pooler endpoint and that the PostGIS extension is enabled on your database.

### NASA tile download fails
Set `EARTHDATA_TOKEN` with a valid token from [urs.earthdata.nasa.gov](https://urs.earthdata.nasa.gov). Without it, the backend falls back to estimated lighting scores.

### SOS alerts not appearing in Admin Dashboard
Make sure the admin user's Firestore document at `users/{uid}` has the field:
```json
{ "role": "security_room_admin" }
```

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push to the branch: `git push origin feature/my-feature`
5. Open a Pull Request

---

## License

This project is built for hackathon/demo purposes. All risk scores are estimates only and must not be used as a substitute for professional safety advice.
