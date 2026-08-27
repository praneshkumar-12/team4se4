# src/analytics_etl/extract.py

import json
import logging
import os
import time
from pathlib import Path

import requests
from dotenv import load_dotenv


BASE_URL = "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1"

MAX_RETRIES = 3
REQUEST_TIMEOUT = 10

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)


class RateLimitError(RuntimeError):
    """Raised when the API returns HTTP 429."""

    pass


class ClientError(RuntimeError):
    """Raised when the API returns a non-429 HTTP 4xx error."""

    pass


class NetworkError(RuntimeError):
    """Raised when connection/timeout errors remain after retries."""

    pass


def check_health():
    """Check whether the Fauxnance API is alive."""

    url = f"{BASE_URL}/health"

    try:
        response = requests.get(
            url,
            timeout=REQUEST_TIMEOUT,
        )

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
        ) from error

    except ValueError as error:
        raise RuntimeError(
            "Fauxnance health check returned invalid JSON"
        ) from error


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
            timeout=REQUEST_TIMEOUT,
        )

        response.raise_for_status()

        data = response.json()

        # No logging headers because they contain the API key.
        log.info("API usage: %s", data)

        return data

    except requests.RequestException as error:
        raise RuntimeError(
            f"Could not check API usage: {error}"
        ) from error

    except ValueError as error:
        raise RuntimeError(
            "Fauxnance usage endpoint returned invalid JSON"
        ) from error


def extract_candles(symbol, cache_dir=".cache"):
    """Get raw candle data from Fauxnance or from the local cache."""

    # ---------------------------------------------------------
    # Load API key
    # ---------------------------------------------------------
    load_dotenv()

    api_key = os.getenv("FAUXNANCE_API_KEY")

    if not api_key:
        raise ValueError(
            "FAUXNANCE_API_KEY is not set"
        )

    # ---------------------------------------------------------
    # Create cache directory
    # ---------------------------------------------------------
    cache_dir = Path(cache_dir)
    cache_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    cache_file = cache_dir / f"{symbol}_all.json"

    # ---------------------------------------------------------
    # Use cached data if it already exists
    # ---------------------------------------------------------
    if cache_file.exists():
        log.info(
            "Using cached data for symbol=%s",
            symbol,
        )

        try:
            with open(cache_file, "r") as f:
                return json.load(f)

        except (OSError, ValueError) as error:
            log.warning(
                "BAD_PAYLOAD symbol=%s source=cache "
                "reason=%s",
                symbol,
                error,
            )

            # If the cache is bad, continue and get fresh
            # data from the API.
            log.info(
                "Ignoring invalid cache for symbol=%s",
                symbol,
            )

    # ---------------------------------------------------------
    # Check API availability
    # ---------------------------------------------------------
    check_health()

    # ---------------------------------------------------------
    # Check API quota before downloading
    # ---------------------------------------------------------
    usage = check_usage(api_key)

    log.info(
        "API usage checked for symbol=%s",
        symbol,
    )

    # ---------------------------------------------------------
    # API request details
    # ---------------------------------------------------------
    url = f"{BASE_URL}/candles/{symbol}"

    headers = {
        "X-API-Key": api_key
    }

    # ---------------------------------------------------------
    # Request with retry handling
    # ---------------------------------------------------------
    for attempt in range(1, MAX_RETRIES + 1):

        try:
            response = requests.get(
                url,
                headers=headers,
                timeout=REQUEST_TIMEOUT,
            )

        # =====================================================
        # FAILURE MODE 3
        # Connection error / timeout
        # =====================================================
        except (
            requests.ConnectionError,
            requests.Timeout,
        ) as error:

            if attempt == MAX_RETRIES:
                log.error(
                    "NETWORK_ERROR symbol=%s "
                    "attempts=%s error=%s",
                    symbol,
                    attempt,
                    error,
                )

                raise NetworkError(
                    f"Could not connect to Fauxnance "
                    f"for {symbol} after {MAX_RETRIES} attempts"
                ) from error

            # 1 second after first failure
            # 2 seconds after second failure
            wait = 2 ** (attempt - 1)

            log.warning(
                "NETWORK_ERROR symbol=%s "
                "attempt=%s retry_in=%s error=%s",
                symbol,
                attempt,
                wait,
                error,
            )

            time.sleep(wait)

            continue

        # =====================================================
        # FAILURE MODE 1
        # HTTP 429 - Rate limit
        # =====================================================
        if response.status_code == 429:

            retry_after = response.headers.get(
                "Retry-After"
            )

            log.error(
                "RATE_LIMITED symbol=%s retry_after=%s",
                symbol,
                retry_after,
            )

            raise RateLimitError(
                "Fauxnance API rate limit reached. "
                f"Retry-After: {retry_after}. "
                "Stopping pipeline."
            )

        # =====================================================
        # FAILURE MODE 2
        # Other HTTP 4xx
        # =====================================================
        if 400 <= response.status_code < 500:

            # It is safe to log the response body, but do not
            # log request headers because they contain the API key.
            message = response.text

            log.error(
                "CLIENT_ERROR symbol=%s "
                "status=%s message=%s",
                symbol,
                response.status_code,
                message,
            )

            raise ClientError(
                f"Fauxnance request failed for {symbol}: "
                f"HTTP {response.status_code}: {message}"
            )

        # =====================================================
        # Other HTTP errors, such as 500 or 503
        # =====================================================
        try:
            response.raise_for_status()

        except requests.HTTPError as error:
            log.error(
                "HTTP_ERROR symbol=%s status=%s error=%s",
                symbol,
                response.status_code,
                error,
            )

            raise RuntimeError(
                f"Fauxnance server error for {symbol}: "
                f"HTTP {response.status_code}"
            ) from error

        # =====================================================
        # SUCCESS: HTTP 200
        # =====================================================
        try:
            data = response.json()

        except ValueError as error:
            # HTTP worked, but the response is not valid JSON.
            log.error(
                "BAD_PAYLOAD symbol=%s reason=invalid_json",
                symbol,
            )

            raise ValueError(
                f"Fauxnance returned invalid JSON for {symbol}"
            ) from error

        # -----------------------------------------------------
        # Return raw payload.
        #
        # The transform layer should validate the actual
        # candle structure and decide whether to:
        #
        #   1. Drop the bad record
        #   2. Quarantine the bad record
        #   3. Raise an error
        # -----------------------------------------------------

        try:
            with open(cache_file, "w") as f:
                json.dump(
                    data,
                    f,
                    indent=2,
                )

        except OSError as error:
            log.warning(
                "CACHE_WRITE_ERROR symbol=%s error=%s",
                symbol,
                error,
            )

        log.info(
            "Downloaded data for symbol=%s",
            symbol,
        )

        return data

    # This should only be reached if the retry loop exits
    # unexpectedly.
    raise NetworkError(
        f"Request failed after {MAX_RETRIES} attempts "
        f"for {symbol}"
    )