# Spark → Crop Compass analytics contract (v1)

All files are optional UTF-8 JSON in `app/src/main/assets/analytics/`. Missing files hide their cards. Invalid, mismatched-version/source, non-finite, negative, unknown-ID or incorrectly-unit-labelled data is rejected with a readable message. No analytics values are generated as fallback.

Every document has this envelope:

```json
{
  "type": "object",
  "required": ["schemaVersion", "dataSource", "dataVersion", "generatedAt", "periodStart", "periodEnd", "unit", "records"],
  "properties": {
    "schemaVersion": {"const": 1},
    "dataSource": {"enum": ["real", "fixture"]},
    "dataVersion": {"type": "string", "description": "Must match assets/data/metadata.json"},
    "generatedAt": {"type": "string", "format": "date-time"},
    "periodStart": {"type": "string", "format": "date"},
    "periodEnd": {"type": "string", "format": "date"},
    "unit": {"enum": ["INR_QUINTAL", "TONNES", "INR"]},
    "records": {"type": "array", "items": {"type": "object"}}
  }
}
```

Dates are ISO `YYYY-MM-DD`; start must not follow end. IDs must come from the exported catalogs. `generatedAt` is an ISO UTC instant. Counts are integers. All numeric values must be finite and non-negative. Do not include farmer reports in any input aggregate. Keep varieties separate for price statistics; `variety` may be null if the source omits it. Every price-statistics row covers the envelope period and is labelled as an aggregate, not an individual sale quote.

## mandi_volatility.json

Unit: `INR_QUINTAL`. Each record requires:

```json
{"type":"object","required":["mandiId","commodityId","observationCount","meanModalPrice","stddevPrice"],"properties":{"mandiId":{"type":"string"},"commodityId":{"type":"string"},"variety":{"type":["string","null"]},"observationCount":{"type":"integer","minimum":2},"meanModalPrice":{"type":"number","exclusiveMinimum":0},"stddevPrice":{"type":"number","minimum":0}}}
```

Use the sample standard deviation of observed daily modal prices. Do not fill missing days. Compute one row per mandi/commodity/compatible variety.

## district_price_dispersion.json

Unit: `INR_QUINTAL`. Each record requires:

```json
{"type":"object","required":["districtId","commodityId","mandiCount","minModalPrice","medianModalPrice","maxModalPrice"],"properties":{"districtId":{"type":"string"},"commodityId":{"type":"string"},"variety":{"type":["string","null"]},"mandiCount":{"type":"integer","minimum":1},"minModalPrice":{"type":"number","exclusiveMinimum":0},"medianModalPrice":{"type":"number","exclusiveMinimum":0},"maxModalPrice":{"type":"number","exclusiveMinimum":0}}}
```

Use the latest observation per mandi within the stated comparable period; never compare incompatible varieties or units. Min ≤ median ≤ max. State the exact period used, rather than labelling it current.

## top_commodity_by_district.json

Unit: `TONNES`. Each record requires:

```json
{"type":"object","required":["districtId","commodityId","arrivalsTonnes","observationCount"],"properties":{"districtId":{"type":"string"},"commodityId":{"type":"string"},"arrivalsTonnes":{"type":"number","minimum":0},"observationCount":{"type":"integer","minimum":1}}}
```

Choose the commodity with the greatest sum of supplied arrivals in each district. Exclude unknown arrivals, report actual observation counts, and use commodity ID to break ties deterministically.

## category_value_totals.json

Unit: `INR`. Each record requires:

```json
{"type":"object","required":["category","estimatedValueInr","recordsUsed"],"properties":{"category":{"type":"string","minLength":1},"estimatedValueInr":{"type":"number","minimum":0},"recordsUsed":{"type":"integer","minimum":1}}}
```

This is an **estimated trade value**, not actual recorded revenue: sum `modalPrice × arrivalsTonnes × 10` for valid observations in the period. One tonne is ten quintals. Exclude observations with missing arrivals; count only rows used. Do not deduplicate by summing varieties that duplicate the same arrival measurement; resolve this upstream and document the policy with the Spark output.
