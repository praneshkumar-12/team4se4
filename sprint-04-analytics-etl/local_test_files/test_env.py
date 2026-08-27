from dotenv import load_dotenv
import os

load_dotenv()

key = os.getenv("FAUXNANCE_API_KEY")

print("Key exists:", key is not None)
print("Key length:", len(key) if key else 0)