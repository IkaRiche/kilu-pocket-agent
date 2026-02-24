# BENCHMARKS

This repository includes **reproducible measurements** for:
- token efficiency (cloud LLM cost)
- step efficiency (how many steps/calls per task)
- stability (retries, p95 cost)
- caching savings (re-run cost)

We avoid “marketing benchmarks”. Everything here is runnable by anyone.

---

## Benchmark Modes

### Mode A — No-LLM (public, deterministic)
- Uses fixed plans (no model keys required)
- Measures:
  - steps executed
  - digest size
  - caching hit rates
  - runtime latency

### Mode B — Measured LLM usage (requires API keys OR local LLM endpoint)
- Uses provider usage accounting (`prompt_tokens`, `completion_tokens`)
- Measures:
  - total tokens per episode
  - calls per episode
  - retries and wasted token ratio

---

## Scenarios (v0.1)

Each scenario runs 3 times; we report median (p50) and p95.

1) S1 Static Extract
- Input: local HTML fixture (`fixtures/static.html`)
- Output: title + 3 facts + evidence hash

2) S2 DOM-heavy Extract
- Input: large synthetic HTML (`fixtures/domheavy.html`)
- Goal: ensure we do not flood the model with full HTML

3) S3 Cache Hit Re-run
- Run S1 twice with unchanged digest
- Goal: second run should be near-zero cloud tokens (or 1 short verify call)

4) S4 Cache Invalidate (Small Diff)
- Run S1 with one paragraph changed
- Goal: bounded recompute (diff summary only)

5) S5 Blocked / Paywall
- Input: `fixtures/blocked.html`
- Goal: early escalation with bounded cost (no runaway loops)

6) S6 Budget Bound
- Budget: max calls / max tokens / max steps
- Goal: stop/escalate when budget exceeded

---

## Metrics

Core:
- `llm_calls_total`
- `prompt_tokens_total`
- `completion_tokens_total`
- `total_tokens_total`
- `wall_time_ms`
- `retries_total`
- `success_rate`

Efficiency:
- `tokens_per_success`
- `tokens_per_kb_extracted`
- `wasted_tokens_ratio` = retry_tokens / total_tokens
- `cache_savings_ratio` = (run1_tokens - run2_tokens) / run1_tokens

---

## Reporting

Results are saved as:
- `bench/results/latest.json`
- `bench/results/latest.md`

CI runs Mode A on every merge (no secrets required).
Mode B is run manually (workflow dispatch) to avoid leaking secrets.

---

## Interpreting Results

We consider KiLu “token efficient” if:
- re-run cache hits reduce tokens by > 80%
- blocked scenarios do not exceed budgets (bounded retries)
- DOM-heavy pages do not cause linear token blow-up (digest stays compact)

---

## How to run

```bash
cd bench
npm install
npm run bench:modeA
# optional:
# LOCAL_LLM_ENDPOINT=http://127.0.0.1:8080 npm run bench:modeB
```

---

## Notes on fairness

We do not compare against other projects here.
We publish a protocol and fixtures so others can run their own agents against the same scenarios.
