"""Chunked Agmarknet CSV -> bounded Android assets. Never geocodes implicitly."""
import argparse
import csv
import hashlib
import json
import re
import sqlite3
import tempfile
import shutil
from contextlib import closing
from functools import lru_cache
from datetime import date, timedelta
from pathlib import Path
import pandas as pd
from geocode_mandis import valid, geocode

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'app/src/main/assets/data'
DISTRICTS = ['Bagalkot','Ballari','Belagavi','Bengaluru Rural','Bengaluru Urban','Bidar','Chamarajanagar','Chikkaballapur','Chikkamagaluru','Chitradurga','Dakshina Kannada','Davanagere','Dharwad','Gadag','Hassan','Haveri','Kalaburagi','Kodagu','Kolar','Koppal','Mandya','Mysuru','Raichur','Bengaluru South','Shivamogga','Tumakuru','Udupi','Uttara Kannada','Vijayapura','Vijayanagara','Yadgir']
ALIASES = {
 'date':['report_date','arrival_date','date','observation_date'], 'state':['state_name','state'],
 'district':['district_name','district'], 'district_id':['district_code','district_id'],
 'market':['market_center','market','market_name','mandi'], 'market_id':['market_code','market_id','mandi_id'],
 'lat':['latitude','lat'], 'lng':['longitude','lng','lon'], 'commodity':['commodity','commodity_name'],
 'group':['commodity_type','group','category'], 'variety':['variety'], 'arrivals':['arrivals_tonnes','arrivals_in_tonnes'],
 'min':['min_price','minimum_price'], 'max':['max_price','maximum_price'], 'modal':['modal_price','modal'],
 'unit':['price_unit','unit']}

def slug(text):
    text=str(text).strip().lower()
    value=re.sub(r'[^a-z0-9]+','-',text).strip('-')
    return value or ('u-'+hashlib.sha256(text.encode()).hexdigest()[:16] if text else '')
def number(text):
    try:
        value = float(str(text).replace(',',''))
        return value if pd.notna(value) and abs(value) != float('inf') else None
    except (ValueError, TypeError): return None
@lru_cache(maxsize=256)
def unit_factor(unit):
    unit = re.sub(r'[^a-z0-9]','',str(unit).lower())
    if unit in ('rsquintal','inrquintal','quintal','rsqtl','qtl','rupeesquintal','perquintal','rsperquintal','inrperquintal','rupeesperquintal'): return 1
    if unit in ('rskg','inrkg','kg','rupeeskg'): return 100
    if unit in ('rstonne','inrtonne','tonne','tonnes','rsmt'): return .1
    return None  # Unknown/missing units are rejected, never guessed.
@lru_cache(maxsize=10000)
def parse_date(text):
    text = str(text).strip()
    for fmt in ('%Y-%m-%d','%d/%m/%Y','%d-%m-%Y','%d-%b-%Y','%d %b %Y'):
        try: return pd.to_datetime(text, format=fmt).date().isoformat()
        except (ValueError, TypeError): pass
    return None
def write_json(path, value):
    temporary=path.with_suffix(path.suffix+'.tmp')
    temporary.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    temporary.replace(path)
def read_csv(path):
    with path.open(encoding='utf-8-sig') as stream: return list(csv.DictReader(stream))

def fixture(target, end=None):
    """ALL numbers/market identities/coordinates here are synthetic, including arrivals."""
    target.mkdir(parents=True,exist_ok=True)
    fields = ['report_date','state_name','district_name','district_code','market_center','market_code','latitude','longitude','commodity','variety','min_price','max_price','modal_price','price_unit','arrivals_tonnes']
    markets = [('Kolar',13.12,78.05),('Kolar',13.30,78.15),('Mandya',12.60,76.90),('Mandya',12.80,77.05),('Mysuru',12.30,76.65),('Mysuru',12.40,76.80)]
    with (target/'synthetic_test_prices.csv').open('w',newline='',encoding='utf-8') as stream:
        writer = csv.DictWriter(stream,fieldnames=fields); writer.writeheader()
        end = end or date.today()  # Reproducible for an explicitly supplied snapshot date.
        for i,(district,lat,lng) in enumerate(markets):
            for c,crop in enumerate(['Tomato','Onion','Potato','Maize','Paddy']):
                for day in range(60):
                    if day % 13 == 3: continue  # Missing observations remain missing.
                    modal = 1100 + c*220 + i*95 + day*3 + ((day+i)%7)*15
                    writer.writerow(dict(report_date=(end-timedelta(days=59-day)).isoformat(),state_name='Karnataka',district_name=district,district_code=slug(district),market_center=f'TEST Market {i+1} (synthetic)',market_code=f'test-{i+1}',latitude=lat,longitude=lng,commodity=crop,variety='TEST Standard',min_price=modal-100,max_price=modal+120,modal_price=modal,price_unit='Rs/Quintal',arrivals_tonnes=10+(day+i)%12))
    write_json(target/'README.json',{'warning':'Entire fixture is synthetic: prices, arrivals, market identities and coordinates. It is NOT Agmarknet data.'})

def build(raw, out, days=365, use_geocode=False, user_agent=None, fixture_end_date=None):
    raw, out = Path(raw), Path(out); out.mkdir(parents=True,exist_ok=True)
    files = sorted(raw.glob('*.csv'))
    source = 'real' if files else 'fixture'
    if files and any('synthetic_test' in f.stem for f in files):
        if not all('synthetic_test' in f.stem for f in files): raise ValueError('Do not mix synthetic fixture files and real CSV files.')
        source = 'fixture'
    if not files:
        fixture(ROOT/'tools/fixtures',fixture_end_date); files = [ROOT/'tools/fixtures/synthetic_test_prices.csv']
    names = dict((r['english'],r['kannada'] or None) for r in read_csv(ROOT/'tools/names_kn.csv'))
    aliases = read_csv(ROOT/'tools/alias_map.csv')
    district_aliases = {r['alias'].casefold():r['canonical_name'] for r in read_csv(ROOT/'tools/district_aliases.csv')}
    manual = {r['market_code']:r for r in read_csv(ROOT/'tools/manual_coords.csv')}
    districts = {slug(d):dict(id=slug(d),nameEn=d,nameKn=names.get(d)) for d in DISTRICTS}
    markets, commodities, issues = {}, {}, set()
    total = ka = rejected = 0
    with tempfile.TemporaryDirectory() as temp, closing(sqlite3.connect(str(Path(temp)/'staging.sqlite'))) as db:
        db.execute('CREATE TABLE prices (id TEXT PRIMARY KEY, mandiId TEXT, commodityId TEXT, variety TEXT, date TEXT, minPrice REAL, maxPrice REAL, modalPrice REAL, arrivalsTonnes REAL)')
        for file in files:
            for chunk in pd.read_csv(file,chunksize=50000,dtype=str,keep_default_na=False,encoding='utf-8-sig'):
                columns = {re.sub(r'[^a-z0-9]+','_',c.strip().lower()).strip('_'):c for c in chunk.columns}
                found = {key: next((columns[a] for a in values if a in columns),None) for key,values in ALIASES.items()}
                print(f'{file.name}: columns found: { {k:v for k,v in found.items() if v} }')
                required = ['date','state','district','market','commodity','modal','unit']
                missing = [k for k in required if not found[k]]
                if missing: raise ValueError(f'Missing required columns: {missing}. Price units are required; add price_unit rather than assume units.')
                total += len(chunk)
                chunk = chunk[chunk[found['state']].str.strip().str.casefold().eq('karnataka')]
                ka += len(chunk)
                batch=[]
                for row in chunk.to_dict('records'):
                    get=lambda key: str(row.get(found.get(key), '')).strip()
                    modal,factor,when = number(get('modal')),unit_factor(get('unit')),parse_date(get('date'))
                    if modal is None or modal <= 0 or factor is None or when is None or when > date.today().isoformat() or any(not get(k) for k in ['district','market','commodity']):
                        rejected+=1; continue
                    district_input=district_aliases.get(get('district').casefold(),get('district'))
                    district = next((d for d in DISTRICTS if d.casefold()==district_input.casefold()),district_input.title())
                    did = slug(district); districts.setdefault(did,dict(id=did,nameEn=district,nameKn=names.get(district)))
                    mid = get('market_id') or f'uncoded-{did}-{slug(get("market"))}'
                    if source=='real' and mid.startswith('test-'): raise ValueError('Synthetic fixture market IDs cannot be labelled real. Keep fixture files separate from real CSVs.')
                    market = get('market').strip()
                    for alias in aliases:
                        if (alias['market_code'] and alias['market_code']==mid) or (alias['alias'] and alias['alias'].casefold()==market.casefold()): market=alias['canonical_name'] or market
                    crop = get('commodity').title(); cid=slug(crop)
                    commodities[cid]=dict(id=cid,nameEn=crop,nameKn=names.get(crop),group=get('group') or None)
                    lat,lng = number(get('lat')),number(get('lng'))
                    if mid in manual:
                        lat,lng=number(manual[mid]['lat']),number(manual[mid]['lng'])
                    if not valid(lat,lng):
                        if lat is not None or lng is not None: issues.add(f'{mid}: rejected coordinates {lat}, {lng}')
                        lat=lng=None
                    existing=markets.get(mid)
                    if existing and existing['districtId']!=did: raise ValueError(f'Market code {mid} belongs to conflicting districts; resolve in raw data.')
                    if not existing: markets[mid]=dict(id=mid,nameEn=market,nameKn=names.get(market),districtId=did,lat=lat,lng=lng,geoConfidence='DISTRICT' if source=='fixture' or lat is None else 'EXACT',lastReportDate=when)
                    else:
                        existing['lastReportDate']=max(existing['lastReportDate'],when)
                        if existing['lat'] is None and lat is not None: existing.update(lat=lat,lng=lng,geoConfidence='DISTRICT' if source=='fixture' else 'EXACT')
                    mn,mx = number(get('min')),number(get('max'))
                    mn=mn*factor if mn is not None and mn>0 else None
                    mx=mx*factor if mx is not None and mx>0 else None
                    if mn is not None and mn>modal*factor: mn=None
                    if mx is not None and mx<modal*factor: mx=None
                    variety=get('variety') or None
                    ident=hashlib.sha256(f'{mid}|{cid}|{variety}|{when}'.encode()).hexdigest()[:24]
                    arrivals=number(get('arrivals'))
                    batch.append((ident,mid,cid,variety,when,mn,mx,modal*factor,arrivals if arrivals is not None and arrivals>=0 else None))
                db.executemany('INSERT OR REPLACE INTO prices VALUES (?,?,?,?,?,?,?,?,?)',batch); db.commit()
        latest=db.execute('SELECT MAX(date) FROM prices').fetchone()[0]
        if latest is None: raise ValueError('No valid Karnataka prices. Existing Android assets have not been replaced.')
        ds=list(districts.values()); ms=list(markets.values()); cs=list(commodities.values())
        if use_geocode and source=='real': ms=geocode(ms,ds,user_agent)
        headers=[d[0] for d in db.execute('SELECT * FROM prices LIMIT 0').description]
        windows=list(dict.fromkeys([days]+[n for n in [365,180,90] if n<days]))
        catalog_bytes=sum(len(json.dumps(value,ensure_ascii=False,indent=2).encode('utf-8')) for value in [ds,ms,cs])
        budget=min(29*1024*1024,30*1024*1024-catalog_bytes-4096)
        if budget<=0: raise ValueError('Catalogs alone exceed the asset budget; narrow the input.')
        candidate=Path(temp)/'prices.csv'
        for window in windows:
            since=(date.fromisoformat(latest)-timedelta(days=window-1)).isoformat()
            with candidate.open('w',newline='',encoding='utf-8') as stream:
                writer=csv.writer(stream); writer.writerow(headers)
                cursor=db.execute('SELECT * FROM prices WHERE date>=? ORDER BY date,mandiId,commodityId',(since,))
                while rows:=cursor.fetchmany(10000):
                    writer.writerows(rows)
                    if stream.tell()>budget: break
            if candidate.stat().st_size < budget: break
        else: raise ValueError('Even 90-day history exceeds 29 MiB. Narrow the raw commodity selection; do not ship oversized assets.')
        count=db.execute('SELECT COUNT(*) FROM prices WHERE date>=?',(since,)).fetchone()[0]
        first=db.execute('SELECT MIN(date) FROM prices WHERE date>=?',(since,)).fetchone()[0]
        write_json(out/'districts.json',ds); write_json(out/'mandis.json',ms); write_json(out/'commodities.json',cs)
        shutil.copyfile(candidate,out/'prices.csv.tmp');(out/'prices.csv.tmp').replace(out/'prices.csv')
        content=source.encode()+(out/'prices.csv').read_bytes()+(out/'mandis.json').read_bytes()+(out/'commodities.json').read_bytes()+(out/'districts.json').read_bytes()
        metadata=dict(dataVersion=hashlib.sha256(content).hexdigest()[:20],dataSource=source,source='SYNTHETIC TEST FIXTURE' if source=='fixture' else 'Agmarknet CSV supplied by user',firstDate=first,lastDate=latest,recordCount=count,historyDays=window,generatedAt=date.today().isoformat(),unit='INR_QUINTAL')
        write_json(out/'metadata.json',metadata)
        stale=[m['nameEn'] for m in ms if (date.today()-date.fromisoformat(m['lastReportDate'])).days>30]
        no_geo=[m['nameEn'] for m in ms if m['lat'] is None or m['geoConfidence']!='EXACT']
        missing_kn=[x['nameEn'] for x in ds+ms+cs if not x.get('nameKn')]
        duplicates={}
        for m in ms: duplicates.setdefault(slug(m['nameEn']),[]).append(m['id'])
        dup=[f'{k}: {v}' for k,v in duplicates.items() if len(v)>1]
        report=f'# Data quality report\n\nSource: **{source}**. '+('ALL fixture prices, arrivals, markets and coordinates are synthetic.\n' if source=='fixture' else 'No rows generated by this pipeline.\n')
        report+=f'\nRows read: {total}; Karnataka rows: {ka}; rejected invalid price/date/unit/identity rows: {rejected}.\n\nBundled observations: {count}; date range: {first} to {latest}; window: {window} days.\nDistricts: {len(ds)} (31 reference districts plus source aliases); mandis: {len(ms)}; commodities: {len(cs)}.\nBundled data bytes: {sum(p.stat().st_size for p in out.glob("*"))}.\n'
        for title,items in [('No report in last 30 days',stale),('Missing/approximate coordinates',no_geo),('Rejected coordinates',sorted(set(issues))),('Possible duplicate market names',dup),('Missing Kannada names',missing_kn)]: report+=f'\n## {title}\n\n'+('\n'.join('- '+str(x) for x in items) or 'None')+'\n'
        report_path = ROOT/'tools/data_quality_report.md' if out.resolve()==OUT.resolve() else out/'data_quality_report.md'
        report_path.write_text(report,encoding='utf-8')
        print(json.dumps(metadata,indent=2)); return metadata

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--raw',type=Path,default=ROOT/'data/raw'); parser.add_argument('--out',type=Path,default=OUT)
    parser.add_argument('--days',type=int,default=365); parser.add_argument('--geocode',action='store_true'); parser.add_argument('--user-agent')
    parser.add_argument('--fixture-end-date',type=date.fromisoformat,help='Fixture snapshot date, defaults to today; ignored for real CSV input.')
    args=parser.parse_args(); build(args.raw,args.out,args.days,args.geocode,args.user_agent,args.fixture_end_date)
