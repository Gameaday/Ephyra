# Ephyra Performance and Resource Budgets

> **Status:** initial budgets for measurement. Values are acceptance targets to validate, not permission to optimize blindly.

## Budget table

| Resource | Initial target | Measurement |
|---|---:|---|
| Visible page decode | ≤ 32 MiB working artifact per page | memory instrumentation |
| Visible continuous tile set | ≤ 64 MiB decoded working set | memory instrumentation |
| Reader working source bytes | ≤ 64 MiB active window | memory instrumentation |
| Document prefetch | ≤ 2 viewports beyond visible | tile request log |
| Cover decode | request-size bucket, never full cover bitmap in memory cache | Coil/memory metrics |
| Frame deadline | 16.67 ms at 60 Hz; 8.33 ms at 120 Hz | macrobenchmark/frame timeline |
| Search source deadline | per-source 8 s, overall 20 s | fake network + instrumentation |
| Search concurrency | bounded and configurable by device tier | scheduler test |
| Source cover cache | durable, LRU bounded, current ceiling 256 MiB pending measurement | cache metrics |
| Startup | no ANR; critical UI path target < 2 s on reference device | startup benchmark |

## Measurement rules

- A target is not a fact until measured.
- Record device, API, ABI, refresh rate, build SHA, and dataset.
- Include hit/miss, queue time, decode time, memory cost, and cancellation behavior.
- Do not tune a constant based on one device.
- If a target is unsafe on a low-memory device, define a fallback policy and record it.
- Memory budgets apply to native decoded bytes as well as Kotlin object size.

## Required reports

- long online chapter;
- long downloaded chapter;
- low-memory background/foreground cycle;
- repeated search across configured source sets;
- cover grid cold/warm/restart;
- continuous reader scroll at fit and zoomed states;
- source health/repair worker.