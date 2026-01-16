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
