# Media Pipeline Contract

> **Status:** binding technical contract. UI requests semantic media; UI does not construct or interpret cache layers.

## Pipeline

```text
PageSource
  -> SourceIdentity
  -> SourceMetadata
  -> ContentGeometry
  -> DecodePlan
  -> Decoder
  -> DecodedArtifact
  -> RenderPlan
  -> Viewport
```

## Contracts

### PageSource

Identifies bytes without owning UI or persistence:

```text
sourceId, pageIndex, sourceRevision, sourceKind, accessPolicy
```

### SourceIdentity

Separates logical page identity from delivery URL. The identity must be stable enough to invalidate a changed page without hashing multi-megabyte bytes on every composition.

### ContentGeometry

```text
sourceWidth, sourceHeight, contentRect, orientation, animationKind, codecKind
```

### DecodePlan

Purely computed:

```text
targetWidth, scaleBucket, sourceRegion, cropPlan, hardwarePolicy, maxBytes
```

The plan participates in cache identity.

### DecodedArtifact

Disposable and explicitly owned:

```text
artifactId, sourceIdentity, decodePlanId, bitmap/frame handle, byteCost
```

## Decoder policy

- Static supported formats use the normal decoder.
- JXL has an explicit decoder path.
- Animated formats have an explicit animation policy.
- Region/tile decoding is used only when the format and memory limits permit it.
- Hardware failure falls back to software with an observable reason.
- Decode errors are typed and retryable where appropriate.
- A corrupt source is not rendered as a successful empty result.

## Crop policy

Crop is a geometry operation, not a UI toggle alone. The plan records:

- whether crop is enabled;
- detected content rectangle;
- confidence;
- fallback-to-original behavior.

Crop output participates in memory/disk identity. Animated or ambiguous content follows a declared fallback.

## Ownership

- Durable source bytes: chapter/download/cache owner.
- Working source bytes: bounded reader resource owner.
- Decoded artifacts: viewport/media memory owner.
- Render tiles: viewport owner.
- Coil: decoder/request infrastructure and disposable memory cache, not business state.

## Evidence

- identity and decode-plan unit tests;
- synthetic static/crop/animated/JXL/corrupt fixtures;
- region decoder fallback tests;
- byte-budget and tile eviction tests;
- cache invalidation tests;
- device decode and low-memory tests.