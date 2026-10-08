# SafeHer Lighting Backend

This backend scores routes based on lighting, human presence, activity density, traffic, pedestrian density, and SOS hotspots.

## Setup

1. **Install Python dependencies:**
   ```bash
   cd lighting_backend
   pip install -r requirements.txt
   ```

2. **Configure Environment Variables:**
   Create a `.env` file in the `lighting_backend` directory (do not commit this file) with the following variables:
   ```env
   DATABASE_URL="postgresql://user:pass@host:5432/db"  # Supabase Postgres with PostGIS
   SOS_HASH_SALT="your_secret_salt_here"               # Used for HMAC-SHA256 user hashing
   SOS_SOURCE="postgres"                               # (Optional) 'postgres' or 'firestore'
   ```

3. **Apply Database Schema & Seed Data:**
   The backend now uses Postgres with PostGIS for the SOS hotspots instead of Firestore.
   To initialize the database schema (`sos_events` table and `sos_hotspots` view) and seed demo data in Chennai:
   ```bash
   python db/apply_schema.py --seed
   ```

4. **Run the Server:**
   ```bash
   python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

5. **Run Tests:**
   ```bash
   python app/test_sos_hotspot_service.py
   ```
