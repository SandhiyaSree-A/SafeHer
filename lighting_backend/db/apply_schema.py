import os
import sys
import argparse
import psycopg
from dotenv import load_dotenv

def apply_sql_file(conn, filepath):
    if not os.path.exists(filepath):
        print(f"Error: {filepath} not found.")
        sys.exit(1)
    with open(filepath, 'r') as f:
        sql = f.read()
    with conn.cursor() as cur:
        cur.execute(sql)
    conn.commit()
    print(f"Applied {filepath} successfully.")

def check_status(conn):
    with conn.cursor() as cur:
        # Check PostGIS
        cur.execute("SELECT extname FROM pg_extension WHERE extname = 'postgis';")
        ext = cur.fetchone()
        if ext:
            print("[OK] PostGIS extension is installed.")
        else:
            print("[FAIL] PostGIS extension is NOT installed.")

        # Check Table
        cur.execute("SELECT tablename FROM pg_tables WHERE tablename = 'sos_events';")
        tbl = cur.fetchone()
        if tbl:
            print("[OK] sos_events table exists.")
        else:
            print("[FAIL] sos_events table does NOT exist.")

        # Check View
        cur.execute("SELECT viewname FROM pg_views WHERE viewname = 'sos_hotspots';")
        vw = cur.fetchone()
        if vw:
            print("[OK] sos_hotspots view exists.")
        else:
            print("[FAIL] sos_hotspots view does NOT exist.")

def main():
    parser = argparse.ArgumentParser(description="Apply schema and optionally seed data.")
    parser.add_argument('--seed', action='store_true', help="Apply seed_chennai.sql after schema.sql")
    args = parser.parse_args()

    # Load from the parent directory's .env
    env_path = os.path.join(os.path.dirname(__file__), "..", ".env")
    load_dotenv(env_path)

    db_url = os.environ.get("DATABASE_URL")
    if not db_url:
        print("Error: DATABASE_URL not found in .env")
        sys.exit(1)

    print("Connecting to database...")
    try:
        with psycopg.connect(db_url) as conn:
            schema_path = os.path.join(os.path.dirname(__file__), "schema.sql")
            apply_sql_file(conn, schema_path)

            if args.seed:
                seed_path = os.path.join(os.path.dirname(__file__), "seed_chennai.sql")
                apply_sql_file(conn, seed_path)

            check_status(conn)
    except Exception as e:
        print(f"Database error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
