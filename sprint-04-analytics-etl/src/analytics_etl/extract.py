# src/analytics_etl/extract.py

import json
import logging
import os
import time
from pathlib import Path

import requests
from dotenv import load_dotenv

BASE_URL = "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1"

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

def check_health():
    """Check whether Fauxnance API is alive."""

    url = f"{BASE_URL}/health"

    try:
        response = requests.get(url, timeout=10)

        response.raise_for_status()

        data = response.json()

        if data.get("data", {}).get("status") != "ok":
            raise RuntimeError(
                f"Fauxnance unhealthy: {data}"
            )

        log.info("Fauxnance health: %s", data)

        return data

        

    except requests.RequestException as error:
        raise RuntimeError(
            f"Fauxnance API health check failed: {error}"
        )
        
def check_usage(api_key):
    """Check API quota usage."""

    url = f"{BASE_URL}/usage"

    headers = {
        "X-API-Key": api_key
    }

    try:
        response = requests.get(
            url,
            headers=headers,
            timeout=10
        )

        response.raise_for_status()

        data = response.json()

        log.info("API usage: %s", data)

        return data

    except requests.RequestException as error:
        raise RuntimeError(
            f"Could not check API usage: {error}"
        )  


def extract_candles(symbol, start=None, end=None, cache_dir=".cache"):
    """Get raw candle data from Fauxnance or from the local cache."""

    # Load the API key from .env
    load_dotenv()
    api_key = os.getenv("FAUXNANCE_API_KEY")

    if not api_key:
        raise ValueError("FAUXNANCE_API_KEY is not set")

    # Create cache directory
    cache_dir = Path(cache_dir)
    cache_dir.mkdir(exist_ok=True)

    cache_file = cache_dir / f"{symbol}_{start}_{end}.json"

    # Use cached data if it already exists
    if cache_file.exists():
        log.info("Using cached data for %s", symbol)

        with open(cache_file) as f:
            return json.load(f)

    # Check API availability first
    check_health()

    # Check quota before downloading
    usage = check_usage(api_key)
    log.info("Current usage: %s", usage)
    
    # API request
    url = f"{BASE_URL}/candles/{symbol}"
    headers = {"X-API-Key": api_key}
    params = {"start": start, "end": end}

    # Try the request up to 3 times for network problems
    for attempt in range(3):
        try:
            response = requests.get(
                url,
                headers=headers,
                params=params,
                timeout=10,
            )

            # Quota exhausted
            if response.status_code == 429:
                usage = check_usage(api_key)

                raise RuntimeError(
                    "Fauxnance API quota exhausted. Stopping."
                )

            # Bad request, bad key, or unknown symbol
            if response.status_code in (400, 401, 404):
                 raise RuntimeError(
                    f"Quota exhausted. Current usage: {usage}"
                 )

            response.raise_for_status()

            data = response.json()

            # Save raw response to cache
            with open(cache_file, "w") as f:
                json.dump(data, f, indent=2)

            log.info("Downloaded data for %s", symbol)

            return data

        except (requests.ConnectionError, requests.Timeout) as error:
            if attempt == 2:
                raise RuntimeError(
                    f"Could not connect to Fauxnance: {error}"
                )

            wait = 2 ** attempt
            log.warning(
                "Network error. Retrying in %s seconds...",
                wait,
            )
            time.sleep(wait)

    raise RuntimeError("Request failed")

