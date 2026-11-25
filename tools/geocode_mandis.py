"""Explicit, cached Nominatim geocoding; no requests without --user-agent."""
import argparse
import json
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]

def valid(lat, lng):
    try:
        return 11.5 <= float(lat) <= 18.5 and 74 <= float(lng) <= 78.6
    except (ValueError, TypeError):
        return False

def geocode(markets, districts, user_agent, cache_path=None):
    if not user_agent or len(user_agent) < 12:
        raise ValueError('Supply a descriptive --user-agent with contact information.')
    cache_path = cache_path or ROOT / 'tools/geocode_cache.json'
    cache = json.loads(cache_path.read_text()) if cache_path.exists() else {}
    last_request = 0.0
    def query(text):
        nonlocal last_request
        if text not in cache:
            time.sleep(max(0, 1.05 - (time.monotonic() - last_request)))
            url = 'https://nominatim.openstreetmap.org/search?' + urlencode({'q': text, 'format': 'json', 'limit': 1, 'countrycodes': 'in'})
            last_request = time.monotonic()
            with urlopen(Request(url, headers={'User-Agent': user_agent}), timeout=30) as response:
                results = json.load(response)
            cache[text] = [float(results[0]['lat']), float(results[0]['lon'])] if results else None
            cache_path.write_text(json.dumps(cache, indent=2))
        value = cache[text]
        return value if value and valid(*value) else None
    by_id = {d['id']: d['nameEn'] for d in districts}
    for market in markets:
        if valid(market.get('lat'), market.get('lng')):
            continue
        district = by_id[market['districtId']]
        point = query(f"{market['nameEn']}, {district}, Karnataka, India")
        confidence = 'EXACT'
        if not point:
            point = query(f'{district} district, Karnataka, India')
            confidence = 'DISTRICT'
        if point:
            market.update(lat=point[0], lng=point[1], geoConfidence=confidence)
    return markets

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--user-agent', required=True)
    args = parser.parse_args()
    assets = ROOT / 'app/src/main/assets/data'
    from build_karnataka_data import build
    days=json.loads((assets/'metadata.json').read_text()).get('historyDays',365) if (assets/'metadata.json').exists() else 365
    build(ROOT/'data/raw',assets,days,use_geocode=True,user_agent=args.user_agent)
