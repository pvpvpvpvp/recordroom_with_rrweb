# RecordRoom Week7 (Thymeleaf) - Gradle 7.4 compatible

Week7 MVP: Record room + Console + Network + Breadcrumb + rrweb(Session Replay) capture via WebSocket
+ Timeline view + Previous Record chaining + Replay(Network) + Replay(Session)

## Requirements
- JDK 17+
- Gradle 7.4.x (works) or higher

## Run
```bash
cd recordroom-week7-thymeleaf-gradle74
gradle bootRun
```

Open:
- Demo: http://localhost:8080/demo-sdk

## What is included
### APIs
- POST /api/records : create record room (accepts previousRecordId)
- WS  /ws/ingest?recordId=... : ingest events (console + network + breadcrumb)
- GET /api/records/{recordId} : record meta (includes previousRecordId)
- GET /api/records/{recordId}/console : console events list
- GET /api/records/{recordId}/network : network events list
- GET /api/records/{recordId}/network/{eventId} : network event detail
- GET /api/records/{recordId}/breadcrumbs : breadcrumb events list

### Thymeleaf UI
- /demo
- /r/{recordId}/timeline  (Console + Network + Breadcrumb merged)
- /r/{recordId}/console
- /r/{recordId}/network
- /r/{recordId}/network/{eventId}
- /r/{recordId}/breadcrumbs

## Notes
- Storage is in-memory (for MVP). Restarting server clears data.
- WebSocket allowed origins are '*' for demo only.
- Breadcrumb capture masks password input and truncates long values.


### Recorder page
- /rec/{recordId} : attach recorder to existing record and generate events; also can create next record chained from current.


## Week 4 (Persistence + Timeline API)
- Embedded H2 file DB (./data/recordroom)
- /api/records/{recordId}/timeline?after=ts_seq&limit=200&types=console,network,breadcrumb
- List endpoints support cursor-based pagination (after/limit)
- Recorder page: /rec/{recordId}
- H2 console: /h2-console


## Week5 (SDK 분리 + 앱 연동 준비)

- SDK 파일: `src/main/resources/static/sdk/recordroom-sdk.js`
- SDK 데모: `/demo-sdk`
- 설치 가이드: `/sdk-install`

### Quick Start (SDK)

```html
<!-- RecordRoom SDK -->
<script src="http://localhost:8080/sdk/recordroom-sdk.js"></script>
<script>
  RecordRoom.start({
    apiBase: "http://localhost:8080",
    appVersion: "0.5.0",
    overlay: true,
    reuseRecordInSession: true,
    autoCreateRecord: true,
    patchConsole: true,
    patchNetwork: true,
    patchBreadcrumb: true
  });
</script>
```


## Week6 (Replay MVP)

- Replay page: `/r/{recordId}/replay`
- Network replay endpoint: `POST /api/records/{recordId}/network/{eventId}/replay`
  - Same-origin only
  - Non-idempotent methods are blocked by default. Use `allowNonIdempotent=true` to override (demo only).


## Week7 (rrweb Session Replay)

- SDK에서 rrweb 기록을 켜면 DOM/입력/스크롤/클릭 등의 이벤트가 rrweb 포맷으로 저장됩니다.
- rrweb 이벤트 ingest: WS `/ws/ingest?recordId=...` 에 `type: "rrweb"` 배치 메시지로 전송
- rrweb 이벤트 조회: `GET /api/records/{recordId}/rrweb?after=ts_seq&limit=2000`
- Session Replay UI: `/r/{recordId}/replay` 페이지 하단 `Session Replay (rrweb)` 섹션

### Quick Start (SDK + rrweb)

```html
<!-- RecordRoom SDK -->
<script src="http://localhost:8080/sdk/recordroom-sdk.js"></script>
<script>
  RecordRoom.start({
    apiBase: "http://localhost:8080",
    appVersion: "0.7.0",
    overlay: true,
    reuseRecordInSession: true,
    autoCreateRecord: true,
    patchConsole: true,
    patchNetwork: true,
    patchBreadcrumb: true,

    // rrweb
    patchRrweb: true,
    rrwebSrc: "https://cdn.jsdelivr.net/npm/rrweb@latest/dist/rrweb.min.js",
    rrwebSampling: { mousemove: 50, scroll: 150, input: "last" },
    rrwebFlushIntervalMs: 2000,
    rrwebMaxBatch: 40,
    rrwebMaxTotalEvents: 6000,
    rrwebMaskAllInputs: true,
    rrwebMaskTextClass: "rr-mask",
    rrwebBlockClass: "rr-block"
  });
</script>

<!-- rrweb 마스킹 예시 -->
<div class="rr-mask">이 영역 텍스트는 rrweb에서 마스킹됩니다</div>
<div class="rr-block">이 영역은 rrweb에서 블록(기록 제외)됩니다</div>
```



## Week9 (DevTools CDP timed + rrweb Replay Hub)

- Replay Hub: `/r/{recordId}/replay`
  - rrweb session replay
  - DevTools Frontend URL generator (mode=timed)
- rrweb API: `GET /api/records/{recordId}/rrweb`
- rrweb ingest via WS: type=`rrweb`


## Week10 (Gated Sync: rrweb playback -> DevTools CDP)

- Clock WS: `/ws/clock` (rrweb player sends {tMs, mode})
- DevTools CDP: `/ws/cdp?recordId=...&mode=gated`
  - Emits Network/Console events only up to current tMs (sequential reveal)
- Limitation: backward seek closes DevTools session (DevTools cannot undo already emitted events)


### Sync note
- Week10 fixed5: rrweb clock sends baseEpochMs + absEpochMs to reduce drift.
- CDP gated uses absEpochMs when available.
