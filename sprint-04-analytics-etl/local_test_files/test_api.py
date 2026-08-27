import os
import requests
from dotenv import load_dotenv

load_dotenv()

api_key = os.getenv("FAUXNANCE_API_KEY")

key = os.getenv("FAUXNANCE_API_KEY")


BASE_URL = "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1"

url = f"{BASE_URL}/candles/AAPL"

headers = {
    "X-Api-Key": api_key
}

response = requests.get(url, headers=headers)

print("Status:", response.status_code)
print("URL:", response.url)
print("Headers sent:", list(response.request.headers.keys()))
print("Response:", response.text[:500])