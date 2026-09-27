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
| Frame deadline | 16.67 ms at 60 Hz; 8.33 ms at 120 Hz | **manual / `E4-user` frame capture — no automated gate** |
| Search source deadline | per-source 8 s, overall 20 s | fake network + instrumentation |
| Search concurrency | bounded and configurable by device tier | scheduler test |
| Source cover cache | durable, LRU bounded, current ceiling 256 MiB pending measurement | cache metrics |
| Startup | no ANR; critical UI path target < 2 s on reference device | `adb shell am start -W` `TotalTime`, scripted |

## Measurement rules

- A target is not a fact until measured.
- Record device, API, ABI, refresh rate, build SHA, and dataset.
- Include hit/miss, queue time, decode time, memory cost, and cancellation behavior.
- Do not tune a constant based on one device.
- If a target is unsafe on a low-memory device, define a fallback policy and record it.
- Memory budgets apply to native decoded bytes as well as Kotlin object size.

## What macrobenchmark measured, and what now does instead

**Dropped 2026-09-27 by owner decision.** `MacrobenchmarkRule` refuses to measure on an unrooted emulated device, so the workflow had failed **24 consecutive scheduled runs** across all three matrix jobs and had never produced a result. It could not be made to work without a rooted image or physical hardware, so it was a gate that could only ever be red — the exact shape this project keeps treating as a defect.

Of the ten budgets above, **eight never depended on it** and are unchanged. The two that did are replaced rather than dropped:

| Was | Now | Honest status |
|---|---|---|
| Frame deadline via `macrobenchmark` frame timeline | manual frame capture | **No automated gate.** The budget stays a stated target; nothing measures it on every build. |
| Startup via `macrobenchmark` startup benchmark | `adb shell am start -W`, scripted | Measurable, and a plain shell command rather than a framework. It is a wall-clock figure, not a frame-timeline one. |

The module itself remains in the build and is **not** wired to anything. Retiring it is legacy deletion, which belongs to `CLEAN-001`, so it is recorded there rather than half-removed here.

## Required reports

- long online chapter;
- long downloaded chapter;
- low-memory background/foreground cycle;
- repeated search across configured source sets;
- cover grid cold/warm/restart;
- continuous reader scroll at fit and zoomed states;
- source health/repair worker.