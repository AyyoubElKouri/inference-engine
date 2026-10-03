# Inference Engine — Database Schema (PostgreSQL)

V1 persists only `rules`. Attributes for the UI are derived
on the fly from `condition` + `conclusions` — no fact table.

## rules

```
rules(
    id BIGSERIAL PRIMARY KEY,
    rule_id TEXT UNIQUE NOT NULL,      -- e.g. R1
    name TEXT NOT NULL,
    condition JSONB NOT NULL,          -- recursive tree: all/any/not/xor/at_least/exactly/at_most
    conclusions JSONB NOT NULL,        -- array of {attribute, operator, value}
    priority INT DEFAULT 0,
    confidence DOUBLE PRECISION DEFAULT 1.0,
    explanation TEXT,
    version INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
)
```

## Derived attributes (no table)

`GET /api/v1/rules/attributes` scans all rules:

- collect every atomic `{attribute, value}` from `condition` (recursive) + `conclusions`
- infer `value_type` from JSON type: boolean → BOOLEAN, int → INTEGER, float → DOUBLE, else TEXT
- inputs = attributes in any `condition`; derived = attributes in any `conclusions`

Inference stays stateless: `POST /infer {facts:[...]}` builds an
in-memory `WorkingMemory`, runs `forwardChaining()`, returns result.
Nothing per-run is stored.
