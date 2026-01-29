/**
 * RecordRoom SDK (Week5 demo)
 * - Creates/attaches a Record ("record room")
 * - Patches console / fetch / XHR / breadcrumbs and sends events via WebSocket to /ws/ingest
 * - Provides a small overlay for "open timeline / new record"
 *
 * NOTE: This is a demo-quality SDK. In production, you should harden PII masking, sampling, batching, retry, etc.
 */
(function (global) {
  "use strict";

  function now() { return Date.now(); }

  function uuid() {
    try {
      if (global.crypto && global.crypto.randomUUID) return global.crypto.randomUUID();
    } catch (e) {}
    // fallback
    var s = "", i;
    for (i = 0; i < 32; i++) {
      var r = (Math.random() * 16) | 0;
      if (i === 8 || i === 12 || i === 16 || i === 20) s += "-";
      s += (i === 12 ? 4 : (i === 16 ? ((r & 3) | 8) : r)).toString(16);
    }
    return s;
  }

  function safeStringify(x) {
    try {
      if (typeof x === "string") return x;
      return JSON.stringify(x);
    } catch (e) {
      try { return String(x); } catch (e2) { return "[unstringifiable]"; }
    }
  }

  function isBlank(s) { return s == null || String(s).trim() === ""; }

  function baseUrlFromApiBase(apiBase) {
    // apiBase like "http://localhost:8080" or "https://example.com"
    // ensure no trailing slash
    return String(apiBase).replace(/\/+$/, "");
  }

  function wsUrlFromBase(baseUrl, recordId) {
    return baseUrl.replace(/^http/, "ws") + "/ws/ingest?recordId=" + encodeURIComponent(recordId);
  }

  function shareUrlFromBase(baseUrl, recordId) {
    return baseUrl + "/r/" + encodeURIComponent(recordId) + "/timeline";
  }

  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function loadScriptOnce(src, globalVarName) {
    return new Promise(function (resolve, reject) {
      try {
        if (globalVarName && global[globalVarName]) return resolve();
        if (!global.document) return reject(new Error("no document"));
        var id = "rr_script_" + (globalVarName || "").replace(/[^a-zA-Z0-9_]/g, "");
        var exist = global.document.getElementById(id);
        if (exist) {
          exist.addEventListener("load", function(){ resolve(); });
          exist.addEventListener("error", function(){ reject(new Error("script load failed: " + src)); });
          return;
        }
        var s = global.document.createElement("script");
        s.id = id;
        s.src = src;
        s.async = true;
        s.onload = function () { resolve(); };
        s.onerror = function () { reject(new Error("script load failed: " + src)); };
        global.document.head.appendChild(s);
      } catch (e) {
        reject(e);
      }
    });
  }

  var DEFAULTS = {
    apiBase: (global.location && global.location.origin) ? global.location.origin : "",
    appVersion: "0.5.0",
    overlay: true,

    // ===== Overlay (Shadow DOM + Auto Theme) =====
    // "auto" | "light" | "dark"
    overlayTheme: "auto",
    // 다크모드 확장프로그램/강제 invert 때문에 오버레이 색이 뒤틀리면 true로 켜세요.
    // (상위 invert를 overlay에서 한번 더 invert해서 상쇄)
    overlayAntiInvert: false,

    // overlay UI state (session)
    overlayCollapsedKey: "rr_overlayCollapsed",
    overlayCornerKey: "rr_overlayCorner",

    // session storage keys
    sessionIdKey: "rr_sessionId",
    recordIdKey: "rr_currentRecordId",

    // if recordId exists in sessionStorage, attach instead of creating a new record
    reuseRecordInSession: true,

    // creates a new record if no recordId is known
    autoCreateRecord: true,

    // patches
    patchConsole: true,
    patchNetwork: true,
    patchBreadcrumb: true,
    patchRrweb: true,

    // rrweb recording
    rrwebSrc: "https://cdn.jsdelivr.net/npm/rrweb@latest/dist/rrweb.min.js",
    rrwebFlushIntervalMs: 2000,
    rrwebMaxBatch: 40,
    rrwebMaxTotalEvents: 6000,
    rrwebSampling: { mousemove: 50, scroll: 150, input: "last" },
    rrwebMaskAllInputs: true,
    rrwebBlockClass: "rr-block",
    rrwebMaskTextClass: "rr-mask",

    // limits
    maxBody: 20000,
    maxInput: 200,

    // mask input types
    maskPassword: true,

    // user identification
    userId: null,
    userEmail: null,

    // sampling (0.0 to 1.0, 1.0 = record all)
    sampleRate: 1.0,

    // masking selectors (CSS selectors to mask)
    maskSelectors: ["input[type='password']", "input[type='email']", ".rr-mask"],
    
    // auto-remove sensitive headers
    removeHeaders: ["authorization", "cookie", "x-api-key", "x-auth-token"]
  };

  function RecordRoomSDK() {
    this.started = false;
    this.recordId = null;
    this.sessionId = null;
    this.previousRecordId = null;
    this.shareUrl = null;
    this.ingestWsUrl = null;

    this.ws = null;
    this.queue = [];
    this.seq = 0;

    this._overlayEl = null;
    this._overlayTimer = null;
    this._overlayMql = null;
    this._overlayThemeListener = null;

    this._rrwebStop = null;
    this._rrwebBuffer = [];
    this._rrwebFlushTimer = null;
    this._rrwebTotal = 0;
    this._unloadHooked = false;

    this._origConsole = null;
    this._origFetch = null;
    this._origXHR = null;
    this._origPushState = null;

    this._patched = { console: false, network: false, breadcrumb: false, rrweb: false };
  }

  RecordRoomSDK.prototype.getState = function () {
    return {
      recordId: this.recordId,
      sessionId: this.sessionId,
      previousRecordId: this.previousRecordId,
      shareUrl: this.shareUrl,
      ingestWsUrl: this.ingestWsUrl
    };
  };

  RecordRoomSDK.prototype._enqueue = function (evt) {
    this.queue.push(evt);
    this._flush();
  };

  RecordRoomSDK.prototype._flush = function () {
    if (!this.ws || this.ws.readyState !== global.WebSocket.OPEN) return;
    while (this.queue.length > 0) {
      var evt = this.queue.shift();
      try {
        this.ws.send(JSON.stringify(evt));
      } catch (e) {
        this.queue.unshift(evt);
        break;
      }
    }
  };

  RecordRoomSDK.prototype._openWs = function (wsUrl) {
    var self = this;
    try {
      if (self.ws && (self.ws.readyState === global.WebSocket.OPEN || self.ws.readyState === global.WebSocket.CONNECTING)) return;
    } catch (e) {}

    self.ws = new global.WebSocket(wsUrl);

    self.ws.addEventListener("open", function () {
      self._flush();
    });

    self.ws.addEventListener("close", function () {
      // reconnect loop
      setTimeout(function () {
        if (!self.ingestWsUrl) return;
        self._openWs(self.ingestWsUrl);
      }, 1000);
    });
  };

  RecordRoomSDK.prototype._isInternalUrl = function (url) {
    try {
      var u = new URL(url, global.location.href);
      return (
          u.pathname.indexOf("/api/records") === 0 ||
          u.pathname.indexOf("/ws/ingest") === 0 ||
          u.pathname.indexOf("/sdk/recordroom-sdk.js") === 0
      );
    } catch (e) {
      return false;
    }
  };

  RecordRoomSDK.prototype._patchConsoleOnce = function (opts) {
    if (this._patched.console) return;
    this._patched.console = true;

    var self = this;
    this._origConsole = {
      log: global.console.log ? global.console.log.bind(global.console) : function () {},
      warn: global.console.warn ? global.console.warn.bind(global.console) : function () {},
      error: global.console.error ? global.console.error.bind(global.console) : function () {}
    };

    function send(level, args) {
      var msg = Array.prototype.slice.call(args).map(safeStringify).join(" ");
      self.seq += 1;
      self._enqueue({
        type: "console",
        level: level,
        message: msg,
        stack: null,
        ts: now(),
        seq: self.seq
      });
    }

    global.console.log = function () {
      self._origConsole.log.apply(null, arguments);
      send("log", arguments);
    };
    global.console.warn = function () {
      self._origConsole.warn.apply(null, arguments);
      send("warn", arguments);
    };
    global.console.error = function () {
      self._origConsole.error.apply(null, arguments);
      send("error", arguments);
    };

    global.addEventListener("error", function (e) {
      self.seq += 1;
      self._enqueue({
        type: "console",
        level: "error",
        message: (e && e.message) ? e.message : "window.error",
        stack: (e && e.error && e.error.stack) ? e.error.stack : null,
        ts: now(),
        seq: self.seq
      });
    });

    global.addEventListener("unhandledrejection", function (e) {
      self.seq += 1;
      self._enqueue({
        type: "console",
        level: "error",
        message: (e && e.reason) ? safeStringify(e.reason) : "unhandledrejection",
        stack: null,
        ts: now(),
        seq: self.seq
      });
    });
  };

  RecordRoomSDK.prototype._patchNetworkOnce = function (opts) {
    if (this._patched.network) return;
    this._patched.network = true;

    var self = this;
    var MAX_BODY = opts.maxBody;

    function truncate(s) {
      if (typeof s !== "string") return s;
      return (s.length > MAX_BODY) ? (s.slice(0, MAX_BODY) + "\n...[truncated]") : s;
    }

    // fetch
    if (global.fetch) {
      this._origFetch = global.fetch.bind(global);
      global.fetch = async function () {
        var args = arguments;
        var startedAt = now();
        var clientRequestId = "f_" + uuid();

        var method = "GET";
        var url = "";
        var requestHeaders = {};
        var requestBody = null;

        try {
          var input = args[0];
          var init = args[1] || {};
          if (input instanceof Request) {
            url = input.url;
            method = input.method || method;
            if (input.headers) {
              input.headers.forEach(function (v, k) { requestHeaders[k] = v; });
            }
          } else {
            url = String(input);
          }
          if (init.method) method = init.method;

          if (init.headers) {
            if (init.headers instanceof Headers) {
              init.headers.forEach(function (v, k) { requestHeaders[k] = v; });
            } else if (Array.isArray(init.headers)) {
              init.headers.forEach(function (kv) { requestHeaders[String(kv[0])] = String(kv[1]); });
            } else if (typeof init.headers === "object") {
              Object.keys(init.headers).forEach(function (k) { requestHeaders[k] = String(init.headers[k]); });
            }
          }

          if (init.body != null) {
            if (typeof init.body === "string") requestBody = init.body;
            else if (init.body instanceof URLSearchParams) requestBody = init.body.toString();
            else requestBody = "[non-string body]";
          }
        } catch (e) {}

        var response = null;
        var status = 0;
        var responseHeaders = {};
        var responseBody = null;
        var error = null;

        try {
          response = await self._origFetch.apply(null, args);
          status = response.status;
          try {
            if (response.headers) response.headers.forEach(function (v, k) { responseHeaders[k] = v; });
          } catch (e) {}
          try {
            var text = await response.clone().text();
            responseBody = truncate(text);
          } catch (e) {
            responseBody = "[unreadable body]";
          }
          return response;
        } catch (e) {
          error = (e && e.message) ? e.message : String(e);
          throw e;
        } finally {
          var durationMs = now() - startedAt;
          if (!self._isInternalUrl(url)) {
            // sampling check
            if (opts.sampleRate < 1.0 && Math.random() > opts.sampleRate) {
              return response || null;
            }
            
            // remove sensitive headers
            var cleanReqHeaders = {};
            var cleanResHeaders = {};
            var removeHeadersLower = (opts.removeHeaders || []).map(function(h) { return h.toLowerCase(); });
            Object.keys(requestHeaders).forEach(function(k) {
              if (removeHeadersLower.indexOf(k.toLowerCase()) === -1) {
                cleanReqHeaders[k] = requestHeaders[k];
              }
            });
            Object.keys(responseHeaders).forEach(function(k) {
              if (removeHeadersLower.indexOf(k.toLowerCase()) === -1) {
                cleanResHeaders[k] = responseHeaders[k];
              }
            });
            
            self.seq += 1;
            self._enqueue({
              type: "network",
              clientRequestId: clientRequestId,
              method: method,
              url: url,
              status: status,
              requestHeaders: cleanReqHeaders,
              requestBody: requestBody ? truncate(requestBody) : null,
              responseHeaders: cleanResHeaders,
              responseBody: responseBody ? truncate(responseBody) : null,
              startedAtEpochMs: startedAt,
              durationMs: durationMs,
              error: error,
              seq: self.seq
            });
          }
        }
      };
    }

    // XHR
    if (global.XMLHttpRequest) {
      this._origXHR = global.XMLHttpRequest;
      var OriginalXHR = global.XMLHttpRequest;

      function PatchedXHR() {
        var xhr = new OriginalXHR();
        var _method = "GET";
        var _url = "";
        var _reqHeaders = {};
        var _reqBody = null;
        var _startedAt = 0;
        var _clientRequestId = "x_" + uuid();

        var origOpen = xhr.open;
        xhr.open = function (method, url) {
          _method = method || "GET";
          _url = url ? String(url) : "";
          return origOpen.apply(xhr, arguments);
        };

        var origSetHeader = xhr.setRequestHeader;
        xhr.setRequestHeader = function (k, v) {
          _reqHeaders[String(k)] = String(v);
          return origSetHeader.apply(xhr, arguments);
        };

        var origSend = xhr.send;
        xhr.send = function (body) {
          _startedAt = now();
          if (body != null) _reqBody = (typeof body === "string") ? body : "[non-string body]";

          xhr.addEventListener("loadend", function () {
            var status = xhr.status || 0;
            var durationMs = now() - _startedAt;

            var resHeaders = {};
            try {
              var raw = xhr.getAllResponseHeaders() || "";
              raw.trim().split(/\r?\n/).forEach(function (line) {
                var idx = line.indexOf(":");
                if (idx > 0) resHeaders[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
              });
            } catch (e) {}

            var resBody = null;
            try {
              resBody = xhr.responseText;
              if (typeof resBody !== "string") resBody = "[non-text response]";
            } catch (e) {
              resBody = "[unreadable body]";
            }

            var err = (status === 0 && xhr.statusText) ? xhr.statusText : null;

            if (!self._isInternalUrl(_url)) {
              // sampling check
              if (opts.sampleRate < 1.0 && Math.random() > opts.sampleRate) {
                return;
              }
              
              // remove sensitive headers
              var cleanReqHeaders = {};
              var cleanResHeaders = {};
              var removeHeadersLower = (opts.removeHeaders || []).map(function(h) { return h.toLowerCase(); });
              Object.keys(_reqHeaders).forEach(function(k) {
                if (removeHeadersLower.indexOf(k.toLowerCase()) === -1) {
                  cleanReqHeaders[k] = _reqHeaders[k];
                }
              });
              Object.keys(resHeaders).forEach(function(k) {
                if (removeHeadersLower.indexOf(k.toLowerCase()) === -1) {
                  cleanResHeaders[k] = resHeaders[k];
                }
              });
              
              self.seq += 1;
              self._enqueue({
                type: "network",
                clientRequestId: _clientRequestId,
                method: _method,
                url: _url,
                status: status,
                requestHeaders: cleanReqHeaders,
                requestBody: _reqBody ? truncate(_reqBody) : null,
                responseHeaders: cleanResHeaders,
                responseBody: resBody ? truncate(resBody) : null,
                startedAtEpochMs: _startedAt,
                durationMs: durationMs,
                error: err,
                seq: self.seq
              });
            }
          });

          return origSend.apply(xhr, arguments);
        };

        return xhr;
      }

      global.XMLHttpRequest = PatchedXHR;
    }
  };

  RecordRoomSDK.prototype._patchBreadcrumbOnce = function (opts) {
    if (this._patched.breadcrumb) return;
    this._patched.breadcrumb = true;

    var self = this;
    var MAX_INPUT = opts.maxInput;

    function safeText(s) {
      if (s == null) return "";
      var t = String(s);
      return (t.length > MAX_INPUT) ? (t.slice(0, MAX_INPUT) + "...") : t;
    }

    function selectorOf(el) {
      try {
        if (!el) return "";
        var parts = [];
        var cur = el;
        for (var i = 0; i < 4 && cur; i++) {
          var part = cur.tagName ? cur.tagName.toLowerCase() : "node";
          if (cur.id) part += "#" + cur.id;
          if (cur.classList && cur.classList.length > 0) part += "." + Array.prototype.slice.call(cur.classList, 0, 2).join(".");
          parts.unshift(part);
          cur = cur.parentElement;
        }
        return parts.join(" > ");
      } catch (e) { return ""; }
    }

    document.addEventListener("click", function (e) {
      var t = e.target;
      self.breadcrumb("click", "click " + selectorOf(t), {
        selector: selectorOf(t),
        text: safeText(t && t.innerText ? t.innerText : ""),
        href: (t && t.href) ? String(t.href) : ""
      });
    }, true);

    document.addEventListener("input", function (e) {
      var t = e.target;
      if (!t || !t.tagName) return;
      var tag = t.tagName.toLowerCase();
      if (tag !== "input" && tag !== "textarea" && tag !== "select") return;

      var type = (t.type || "").toLowerCase();
      var isPwd = (type === "password");
      var value = (opts.maskPassword && isPwd) ? "[masked]" : safeText(t.value);

      self.breadcrumb("input", "input " + selectorOf(t), {
        selector: selectorOf(t),
        name: t.name ? String(t.name) : "",
        value: value
      });
    }, true);

    this._origPushState = global.history && global.history.pushState ? global.history.pushState.bind(global.history) : null;
    if (this._origPushState) {
      global.history.pushState = function () {
        var before = global.location.href;
        var ret = self._origPushState.apply(global.history, arguments);
        var after = global.location.href;
        self.breadcrumb("navigation", "history.pushState", { before: before, after: after });
        return ret;
      };
    }

    global.addEventListener("popstate", function () {
      self.breadcrumb("navigation", "popstate", { href: global.location.href });
    });
  };

  RecordRoomSDK.prototype.breadcrumb = function (name, message, data) {
    this.seq += 1;
    this._enqueue({
      type: "breadcrumb",
      name: name,
      message: message,
      data: data || {},
      ts: now(),
      seq: this.seq
    });
  };

  RecordRoomSDK.prototype._startRrwebAsync = function (opts) {
    var self = this;
    if (!opts.patchRrweb) return;

    // stop previous recorder if any
    try { if (self._rrwebStop) self._rrwebStop(); } catch (e) {}
    self._rrwebStop = null;

    try { if (self._rrwebFlushTimer) clearInterval(self._rrwebFlushTimer); } catch (e) {}
    self._rrwebFlushTimer = null;

    self._rrwebBuffer = [];
    self._rrwebTotal = 0;

    // load rrweb if missing
    (async function () {
      try {
        if (!global.rrweb || !global.rrweb.record) {
          await loadScriptOnce(opts.rrwebSrc, "rrweb");
        }
        if (!global.rrweb || !global.rrweb.record) {
          return;
        }

        var sampling = opts.rrwebSampling || { mousemove: 50, scroll: 150, input: "last" };

        self._rrwebStop = global.rrweb.record({
          emit: function (event) {
            try {
              if (!event) return;
              if (opts.rrwebMaxTotalEvents && self._rrwebTotal >= opts.rrwebMaxTotalEvents) {
                return;
              }

              var ts = (event.timestamp != null) ? event.timestamp : Date.now();
              self.seq += 1;
              var env = { ts: ts, seq: self.seq, payload: event };
              self._rrwebBuffer.push(env);
              self._rrwebTotal += 1;

              if (self._rrwebBuffer.length >= (opts.rrwebMaxBatch || 40)) {
                self._flushRrweb(opts);
              }
            } catch (e) {
              // ignore
            }
          },
          sampling: sampling,
          blockClass: opts.rrwebBlockClass || "rr-block",
          maskTextClass: opts.rrwebMaskTextClass || "rr-mask",
          maskAllInputs: (opts.rrwebMaskAllInputs !== false)
        });

        self._rrwebFlushTimer = setInterval(function () {
          self._flushRrweb(opts);
        }, opts.rrwebFlushIntervalMs || 2000);

        self._patched.rrweb = true;

        // mark start
        self.breadcrumb("rrweb", "rrweb recording started", { sampling: sampling, maxTotal: opts.rrwebMaxTotalEvents });
      } catch (e) {
        // rrweb not available; ignore
      }
    })();
  };

  RecordRoomSDK.prototype._flushRrweb = function (opts) {
    if (!this.ws) return;
    if (!this._rrwebBuffer || this._rrwebBuffer.length === 0) return;

    var maxBatch = opts.rrwebMaxBatch || 40;
    var batch = this._rrwebBuffer.splice(0, maxBatch);

    // send as one WS message
    this._enqueue({ type: "rrweb", events: batch });
  };

  RecordRoomSDK.prototype._hookUnloadOnce = function () {
    var self = this;
    if (self._unloadHooked) return;
    self._unloadHooked = true;

    if (!global.addEventListener) return;
    global.addEventListener("beforeunload", function () {
      try { self._flushRrweb(self.opts || DEFAULTS); } catch (e) {}
      try { self._flush(); } catch (e) {}
    });
  };

  RecordRoomSDK.prototype._createRecord = async function (opts, previousRecordId) {
    var baseUrl = baseUrlFromApiBase(opts.apiBase);

    // ensure sessionId
    var sid = null;
    try { sid = global.sessionStorage.getItem(opts.sessionIdKey); } catch (e) {}
    if (isBlank(sid)) sid = uuid();
    try { global.sessionStorage.setItem(opts.sessionIdKey, sid); } catch (e) {}

    function buildDeviceInfo() {
      try {
        var nav = global.navigator || {};
        var scr = global.screen || {};
        var parts = [];
        if (nav.platform) parts.push("platform=" + nav.platform);
        if (nav.language) parts.push("lang=" + nav.language);
        if (scr.width && scr.height) parts.push("screen=" + scr.width + "x" + scr.height);
        if (global.devicePixelRatio) parts.push("dpr=" + global.devicePixelRatio);
        return parts.join(" | ");
      } catch (e) {
        return "";
      }
    }

    var payload = {
      pageUrl: global.location ? global.location.href : "",
      userAgent: global.navigator ? global.navigator.userAgent : "",
      appVersion: opts.appVersion,
      sessionId: sid,
      previousRecordId: previousRecordId || null,
      deviceInfo: buildDeviceInfo(),
      userId: opts.userId || null,
      userEmail: opts.userEmail || null
    };

    var res = await fetch(baseUrl + "/api/records", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
      var t = "";
      try { t = await res.text(); } catch (e) {}
      throw new Error("createRecord failed: " + res.status + " " + t);
    }

    var data = await res.json();
    return data; // {recordId, shareUrl, ingestWsUrl}
  };

  RecordRoomSDK.prototype._attachRecord = async function (opts, recordId) {
    var baseUrl = baseUrlFromApiBase(opts.apiBase);

    // validate record exists (optional)
    try {
      var res = await fetch(baseUrl + "/api/records/" + encodeURIComponent(recordId));
      if (!res.ok) throw new Error("not ok");
      var data = await res.json();
      return {
        recordId: recordId,
        shareUrl: shareUrlFromBase(baseUrl, recordId),
        ingestWsUrl: wsUrlFromBase(baseUrl, recordId),
        previousRecordId: data.previousRecordId || null
      };
    } catch (e) {
      // if cannot attach, create new
      var created = await this._createRecord(opts, null);
      return created;
    }
  };

  RecordRoomSDK.prototype._applyRecord = function (opts, recordData) {
    var baseUrl = baseUrlFromApiBase(opts.apiBase);

    this.recordId = recordData.recordId;
    this.shareUrl = recordData.shareUrl || shareUrlFromBase(baseUrl, recordData.recordId);
    this.ingestWsUrl = recordData.ingestWsUrl || wsUrlFromBase(baseUrl, recordData.recordId);
    this.previousRecordId = recordData.previousRecordId || null;

    // persist current recordId in session
    try { global.sessionStorage.setItem(opts.recordIdKey, this.recordId); } catch (e) {}

    // open ws and ensure patches
    this._openWs(this.ingestWsUrl);

    if (opts.patchConsole) this._patchConsoleOnce(opts);
    if (opts.patchNetwork) this._patchNetworkOnce(opts);
    if (opts.patchBreadcrumb) this._patchBreadcrumbOnce(opts);

    // rrweb session recording (per-record)
    if (opts.patchRrweb) this._startRrwebAsync(opts);
    this._hookUnloadOnce();

    // emit attached breadcrumb
    this.breadcrumb("custom", "recordroom attached", {
      recordId: this.recordId,
      shareUrl: this.shareUrl,
      appVersion: opts.appVersion
    });

    // overlay
    if (opts.overlay) this._ensureOverlay(opts);
  };

  /**
   * Overlay (Shadow DOM + Auto Theme)
   * - 상위 다크모드/전역 CSS 영향을 거의 안 받음
   * - opts.overlayTheme: "auto" | "light" | "dark"
   * - opts.overlayAntiInvert: true면 강제 invert류(다크모드 확장)에서 색 뒤틀림을 상쇄
   */
  RecordRoomSDK.prototype._ensureOverlay = function (opts) {
    var self = this;

    if (!global.document || !global.document.documentElement) return;
    if (this._overlayEl) return;

    // host
    var host = global.document.createElement("div");
    host.id = "recordroom-overlay-host";
    host.style.cssText = [
      "all: initial",
      "position: fixed",
      // corner positioning is applied by JS (supports 4-corner snap + drag)
      "z-index: 2147483647",
      "font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif",
      "font-size: 12px",
      "line-height: 1.4",
      "user-select: text",
      "isolation: isolate",
      "pointer-events: auto"
    ].join(";");

    // 다크모드 확장(강제 invert) 대응 옵션
    if (opts.overlayAntiInvert) {
      host.style.filter = "invert(1) hue-rotate(180deg)";
    } else {
      host.style.filter = "none";
    }

    global.document.documentElement.appendChild(host);
    this._overlayEl = host;

    // Shadow DOM 사용 (가능한 환경에서)
    var shadow = null;
    try {
      if (host.attachShadow) shadow = host.attachShadow({ mode: "open" });
    } catch (e) { shadow = null; }

    // Shadow DOM이 안 되면(아주 구형) fallback으로 그냥 body에 넣는 방식으로도 동작은 하게 처리
    var root = shadow || host;

    // style
    var style = global.document.createElement("style");
    style.textContent = [
      ":host{all:initial;}",
      ".rr{",
      "  --bg:#ffffff;",
      "  --fg:#111827;",
      "  --muted:#6b7280;",
      "  --border:#e5e7eb;",
      "  --chip-bg:#f3f4f6;",
      "  --chip-fg:#111827;",
      "  --btn-bg:#ffffff;",
      "  --btn-fg:#111827;",
      "  --btn-border:#e5e7eb;",
      "  --btn-hover:#f9fafb;",
      "  box-sizing:border-box;",
      "  width:340px;",
      "  padding:10px 12px;",
      "  border-radius:12px;",
      "  background:var(--bg);",
      "  color:var(--fg);",
      "  border:1px solid var(--border);",
      "  box-shadow:0 10px 25px rgba(0,0,0,0.18);",
      "  font-family:system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif;",
      "  font-size:12px;",
      "  line-height:1.4;",
      "}",
      ".rr[data-theme='dark']{",
      "  --bg:rgba(17,24,39,0.92);",
      "  --fg:#ffffff;",
      "  --muted:rgba(255,255,255,0.7);",
      "  --border:rgba(255,255,255,0.18);",
      "  --chip-bg:rgba(255,255,255,0.12);",
      "  --chip-fg:#ffffff;",
      "  --btn-bg:transparent;",
      "  --btn-fg:#ffffff;",
      "  --btn-border:rgba(255,255,255,0.25);",
      "  --btn-hover:rgba(255,255,255,0.08);",
      "}",
      ".title{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;}",
      ".title .name{font-weight:800;display:flex;gap:8px;align-items:center;}",
      ".title .drag{display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;border:1px solid var(--btn-border);background:var(--btn-bg);color:var(--btn-fg);font-size:12px;opacity:0.9;cursor:grab;user-select:none;}",
      ".title .drag:active{cursor:grabbing;}",
      ".title .actions{display:flex;gap:6px;align-items:center;}",
      ".title .iconbtn{border:none;background:transparent;cursor:pointer;font-size:16px;color:var(--fg);padding:2px 6px;border-radius:8px;}",
      ".title .iconbtn:hover{background:var(--btn-hover);}",
      ".title .close:hover{background:var(--btn-hover);}",
      ".label{font-size:12px;color:var(--muted);margin-bottom:6px;font-weight:700;}",
      "code{display:block;font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;",
      "  font-size:12px;padding:6px;background:var(--chip-bg);color:var(--chip-fg);border-radius:8px;word-break:break-all;}",
      ".btns{display:flex;gap:8px;margin-top:10px;flex-wrap:wrap;}",
      "button, a{padding:8px 10px;border:1px solid var(--btn-border);border-radius:10px;background:var(--btn-bg);color:var(--btn-fg);cursor:pointer;text-decoration:none;}",
      "button:hover, a:hover{background:var(--btn-hover);}",
      ".meta{margin-top:10px;font-size:12px;color:var(--muted);line-height:1.3;}",
      ".meta a{color:var(--btn-fg);display:inline-block;max-width:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}",
      ".collapsedWrap{display:none;}",
      ".collapsedBtn{display:inline-flex;align-items:center;justify-content:center;width:44px;height:44px;border-radius:999px;border:1px solid var(--border);background:var(--bg);color:var(--fg);box-shadow:0 10px 25px rgba(0,0,0,0.18);cursor:pointer;user-select:none;font-weight:900;font-size:18px;}",
      ".collapsedBtn:hover{filter:brightness(0.98);}",
      ".hint{margin-top:8px;color:var(--muted);font-size:11px;}",
      ".rr[data-collapsed='true']{width:auto;padding:0;border:none;background:transparent;box-shadow:none;}",
      ".rr[data-collapsed='true'] .expandedWrap{display:none;}",
      ".rr[data-collapsed='true'] .collapsedWrap{display:block;}"
    ].join("\n");
    root.appendChild(style);

    // box
    var box = global.document.createElement("div");
    box.className = "rr";
    box.setAttribute("data-theme", "light");
    box.setAttribute("data-collapsed", "false");

    // NOTE: Shadow DOM이면 root.getElementById로 찾을 수 있도록 id는 내부에 둡니다.
    box.innerHTML = [
      '<div class="expandedWrap">',
      '  <div class="title">',
      '    <div class="name"><span>RecordRoom</span><span id="rr_drag" class="drag" title="Drag">↕</span></div>',
      '    <div class="actions">',
      '      <button id="rr_btn_min" class="iconbtn" type="button" title="Minimize">–</button>',
      '      <button id="rr_btn_close" class="iconbtn close" type="button" title="Close">✕</button>',
      "    </div>",
      "  </div>",
      '  <div class="label">recordId</div>',
      '  <code id="rr_recordId">(pending)</code>',
      '  <div class="btns">',
      '    <button id="rr_btn_timeline" type="button">Timeline</button>',
      '    <button id="rr_btn_copy" type="button">Copy Link</button>',
      '    <button id="rr_btn_new" type="button">New Record</button>',
      "  </div>",
      '  <div class="meta">',
      "    <div>shareUrl:</div>",
      '    <div id="rr_shareUrl">-</div>',
      '    <div class="hint">Tip: drag ↕ to snap to corners</div>',
      "  </div>",
      "</div>",
      '<div class="collapsedWrap">',
      '  <button id="rr_btn_expand" class="collapsedBtn" type="button" title="Open">+</button>',
      "</div>"
    ].join("");

    root.appendChild(box);

    // theme: auto/light/dark
    function resolveTheme() {
      var t = (opts.overlayTheme || "auto");
      if (t === "light" || t === "dark") return t;
      // auto
      try {
        if (global.matchMedia) {
          var mql = global.matchMedia("(prefers-color-scheme: dark)");
          return (mql && mql.matches) ? "dark" : "light";
        }
      } catch (e) {}
      return "light";
    }

    function applyTheme() {
      try {
        box.setAttribute("data-theme", resolveTheme());
      } catch (e) {}
    }

    applyTheme();

    // attach listener for auto theme
    try {
      if (opts.overlayTheme === "auto" || isBlank(opts.overlayTheme)) {
        self._overlayMql = global.matchMedia ? global.matchMedia("(prefers-color-scheme: dark)") : null;
        if (self._overlayMql) {
          self._overlayThemeListener = function () { applyTheme(); };
          if (self._overlayMql.addEventListener) self._overlayMql.addEventListener("change", self._overlayThemeListener);
          else if (self._overlayMql.addListener) self._overlayMql.addListener(self._overlayThemeListener);
        }
      }
    } catch (e) {}

    // helpers to find inside shadow or fallback root
    function byId(id) {
      // shadow DOM 우선 조회
      try {
        if (shadow && shadow.querySelector) {
          var el = shadow.querySelector("#" + id);
          if (el) return el;
        }
      } catch (e) {}
      // shadow를 못 쓰는 구형 브라우저용 fallback
      try {
        return host.querySelector("#" + id);
      } catch (e) {}
      return null;
    }

    function refresh() {
      var ridEl = byId("rr_recordId");
      var suEl = byId("rr_shareUrl");
      if (!ridEl || !suEl) return;

      ridEl.textContent = self.recordId || "-";
      if (self.shareUrl) {
        // 긴 URL은 앞/뒤만 보여주고 가운데는 ... 로 축약
        var text = self.shareUrl;
        try {
          if (text.length > 60) {
            text = text.slice(0, 30) + "..." + text.slice(-15);
          }
        } catch (e) {}
        // safe anchor
        suEl.innerHTML = '<a href="' + self.shareUrl + '" target="_blank" rel="noopener noreferrer">' + text + "</a>";
      } else {
        suEl.textContent = "-";
      }
    }

    refresh();

    // --- overlay positioning (4-corner snap) + persistence ---
    var MARGIN = 16;
    function getCorner() {
      try {
        var v = global.sessionStorage.getItem(opts.overlayCornerKey || "rr_overlayCorner");
        if (v === "tl" || v === "tr" || v === "bl" || v === "br") return v;
      } catch (e) {}
      return "br";
    }
    function setCorner(c) {
      try { global.sessionStorage.setItem(opts.overlayCornerKey || "rr_overlayCorner", c); } catch (e) {}
    }
    function applyCorner(c) {
      // reset
      host.style.left = "auto";
      host.style.right = "auto";
      host.style.top = "auto";
      host.style.bottom = "auto";
      host.style.transform = "none";
      // corner
      if (c === "tl") { host.style.left = MARGIN + "px"; host.style.top = MARGIN + "px"; }
      else if (c === "tr") { host.style.right = MARGIN + "px"; host.style.top = MARGIN + "px"; }
      else if (c === "bl") { host.style.left = MARGIN + "px"; host.style.bottom = MARGIN + "px"; }
      else { host.style.right = MARGIN + "px"; host.style.bottom = MARGIN + "px"; }
      setCorner(c);
    }
    applyCorner(getCorner());

    // --- collapse/expand ---
    function getCollapsed() {
      try {
        var v = global.sessionStorage.getItem(opts.overlayCollapsedKey || "rr_overlayCollapsed");
        if (v === null || v === undefined) {
          // default: collapsed (small + button only)
          return true;
        }
        return v === "1" || v === "true";
      } catch (e) {}
      // 세션 스토리지를 못 쓰는 경우에도 기본은 접힘 상태
      return true;
    }
    function setCollapsed(v) {
      try { global.sessionStorage.setItem(opts.overlayCollapsedKey || "rr_overlayCollapsed", v ? "1" : "0"); } catch (e) {}
      try { box.setAttribute("data-collapsed", v ? "true" : "false"); } catch (e) {}
    }
    setCollapsed(getCollapsed());

    var btnClose = byId("rr_btn_close");
    if (btnClose) btnClose.addEventListener("click", function () {
      try {
        if (self._overlayTimer) clearInterval(self._overlayTimer);
      } catch (e) {}
      self._overlayTimer = null;

      // remove theme listener
      try {
        if (self._overlayMql && self._overlayThemeListener) {
          if (self._overlayMql.removeEventListener) self._overlayMql.removeEventListener("change", self._overlayThemeListener);
          else if (self._overlayMql.removeListener) self._overlayMql.removeListener(self._overlayThemeListener);
        }
      } catch (e) {}
      self._overlayMql = null;
      self._overlayThemeListener = null;

      try { self._overlayEl.remove(); } catch (e) {}
      self._overlayEl = null;
    });

    var btnMin = byId("rr_btn_min");
    if (btnMin) btnMin.addEventListener("click", function () {
      setCollapsed(true);
    });
    var btnExpand = byId("rr_btn_expand");
    if (btnExpand) btnExpand.addEventListener("click", function (e) {
      // 드래그한 직후 발생한 클릭은 무시 (아래 drag 로직에서 플래그 설정)
      if (btnExpand.__rr_dragged) {
        btnExpand.__rr_dragged = false;
        try { e.preventDefault(); e.stopPropagation(); } catch (ee) {}
        return;
      }
      setCollapsed(false);
      refresh();
    });

    var btnTimeline = byId("rr_btn_timeline");
    if (btnTimeline) btnTimeline.addEventListener("click", function () {
      if (!self.shareUrl) return;
      global.open(self.shareUrl, "_blank", "noopener,noreferrer");
    });

    var btnCopy = byId("rr_btn_copy");
    if (btnCopy) btnCopy.addEventListener("click", async function () {
      if (!self.shareUrl) return;
      try {
        if (global.navigator && global.navigator.clipboard && global.navigator.clipboard.writeText) {
          await global.navigator.clipboard.writeText(self.shareUrl);
          alert("copied");
        } else {
          // fallback
          var ta = global.document.createElement("textarea");
          ta.value = self.shareUrl;
          global.document.body.appendChild(ta);
          ta.select();
          global.document.execCommand("copy");
          ta.remove();
          alert("copied");
        }
      } catch (e) {
        alert("copy failed: " + (e && e.message ? e.message : String(e)));
      }
    });

    var btnNew = byId("rr_btn_new");
    if (btnNew) btnNew.addEventListener("click", async function () {
      try {
        await self.newRecord(opts);
        refresh();
      } catch (e) {
        alert("new record failed: " + (e && e.message ? e.message : String(e)));
      }
    });

    // periodic refresh (in case user calls newRecord programmatically)
    this._overlayTimer = setInterval(function () {
      try { applyTheme(); } catch (e) {}
      refresh();
    }, 1500);

    // --- drag to snap (expanded + collapsed 모두) ---
    (function attachDrag() {
      var dragHandleExpanded = byId("rr_drag");
      var dragHandleCollapsed = byId("rr_btn_expand");
      if ((!dragHandleExpanded && !dragHandleCollapsed) || !global.addEventListener) return;

      var dragging = false;
      var startX = 0, startY = 0;
      var startRect = null;
      var activeHandle = null;
      var isTouchDrag = false;

      function extractXY(e) {
        if (!e) return { x: 0, y: 0 };
        if (e.touches && e.touches.length > 0) {
          return { x: e.touches[0].clientX || 0, y: e.touches[0].clientY || 0 };
        }
        return {
          x: (e.clientX != null) ? e.clientX : 0,
          y: (e.clientY != null) ? e.clientY : 0
        };
      }

      function onDown(e) {
        isTouchDrag = !!(e && e.type && String(e.type).indexOf("touch") === 0);
        // On mobile, calling preventDefault() on touchstart can cancel the synthetic click,
        // which breaks "+" tap-to-open. We'll only preventDefault after we detect a real drag.
        if (!isTouchDrag) {
          try { e.preventDefault(); } catch (ee) {}
        }
        dragging = true;
        activeHandle = e && e.currentTarget ? e.currentTarget : null;
        var p = extractXY(e);
        startX = p.x;
        startY = p.y;
        try { startRect = host.getBoundingClientRect(); } catch (ee) { startRect = null; }

        // switch to explicit left/top while dragging
        if (startRect) {
          host.style.left = Math.max(MARGIN, startRect.left) + "px";
          host.style.top = Math.max(MARGIN, startRect.top) + "px";
          host.style.right = "auto";
          host.style.bottom = "auto";
        }
        global.addEventListener("mousemove", onMove, true);
        global.addEventListener("mouseup", onUp, true);
        global.addEventListener("touchmove", onMove, true);
        global.addEventListener("touchend", onUp, true);
      }

      function onMove(e) {
        if (!dragging || !startRect) return;
        var p = extractXY(e);
        var dx = p.x - startX;
        var dy = p.y - startY;

        // 축소 상태(+ 버튼)에서 실제로 움직였다는 것을 표시해서 click handler에서 구분
        if (activeHandle && activeHandle === dragHandleCollapsed) {
          if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
            activeHandle.__rr_dragged = true;
          }
        }

        // If this is a touch interaction and we detected real movement, prevent page scroll
        if (isTouchDrag && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
          try { e.preventDefault(); } catch (ee) {}
        }

        var vw = global.innerWidth || 0;
        var vh = global.innerHeight || 0;
        var w = startRect.width || 0;
        var h = startRect.height || 0;

        var left = startRect.left + dx;
        var top = startRect.top + dy;

        // clamp inside viewport margins
        left = Math.min(Math.max(MARGIN, left), Math.max(MARGIN, vw - w - MARGIN));
        top = Math.min(Math.max(MARGIN, top), Math.max(MARGIN, vh - h - MARGIN));

        host.style.left = left + "px";
        host.style.top = top + "px";
      }

      function onUp(e) {
        if (!dragging) return;
        dragging = false;
        global.removeEventListener("mousemove", onMove, true);
        global.removeEventListener("mouseup", onUp, true);
        global.removeEventListener("touchmove", onMove, true);
        global.removeEventListener("touchend", onUp, true);

        // Touch tap on collapsed "+" should open (and should NOT snap / move).
        if (activeHandle && activeHandle === dragHandleCollapsed && isTouchDrag) {
          if (!activeHandle.__rr_dragged) {
            setCollapsed(false);
            refresh();
            return;
          }
          // reset flag after a drag sequence
          activeHandle.__rr_dragged = false;
        }

        // snap to nearest corner based on current center
        var rect = null;
        try { rect = host.getBoundingClientRect(); } catch (ee) { rect = null; }
        if (!rect) {
          applyCorner(getCorner());
          return;
        }
        var cx = rect.left + rect.width / 2;
        var cy = rect.top + rect.height / 2;
        var vw = global.innerWidth || 0;
        var vh = global.innerHeight || 0;

        var horiz = (cx < vw / 2) ? "l" : "r";
        var vert = (cy < vh / 2) ? "t" : "b";
        var corner = vert + horiz; // "tl"/"tr"/"bl"/"br"
        applyCorner(corner);
      }

      if (dragHandleExpanded) {
        dragHandleExpanded.addEventListener("mousedown", onDown, true);
        dragHandleExpanded.addEventListener("touchstart", onDown, true);
      }
      if (dragHandleCollapsed) {
        dragHandleCollapsed.addEventListener("mousedown", onDown, true);
        dragHandleCollapsed.addEventListener("touchstart", onDown, true);
      }
    })();
  };

  RecordRoomSDK.prototype.start = async function (options) {
    var opts = {};
    Object.keys(DEFAULTS).forEach(function (k) { opts[k] = DEFAULTS[k]; });
    options = options || {};
    Object.keys(options).forEach(function (k) { opts[k] = options[k]; });

    if (isBlank(opts.apiBase)) throw new Error("apiBase is required");

    var baseUrl = baseUrlFromApiBase(opts.apiBase);

    // resolve sessionId
    var sid = null;
    try { sid = global.sessionStorage.getItem(opts.sessionIdKey); } catch (e) {}
    if (isBlank(sid)) sid = uuid();
    try { global.sessionStorage.setItem(opts.sessionIdKey, sid); } catch (e) {}
    this.sessionId = sid;

    // determine recordId
    var recordId = options.recordId || null;
    if (isBlank(recordId) && opts.reuseRecordInSession) {
      try { recordId = global.sessionStorage.getItem(opts.recordIdKey); } catch (e) {}
    }

    var recordData = null;

    if (!isBlank(recordId)) {
      recordData = await this._attachRecord(opts, recordId);
    } else {
      if (!opts.autoCreateRecord) throw new Error("no recordId and autoCreateRecord=false");
      // previousRecordId from session recordId (if any)
      var prev = null;
      try { prev = global.sessionStorage.getItem(opts.recordIdKey); } catch (e) {}
      if (isBlank(prev)) prev = null;
      recordData = await this._createRecord(opts, prev);
      // enrich previousRecordId for UI
      if (prev) recordData.previousRecordId = prev;
    }

    // compute missing urls
    if (!recordData.shareUrl) recordData.shareUrl = shareUrlFromBase(baseUrl, recordData.recordId);
    if (!recordData.ingestWsUrl) recordData.ingestWsUrl = wsUrlFromBase(baseUrl, recordData.recordId);

    this._applyRecord(opts, recordData);
    this.started = true;

    return this.getState();
  };

  RecordRoomSDK.prototype.newRecord = async function (options) {
    var opts = {};
    Object.keys(DEFAULTS).forEach(function (k) { opts[k] = DEFAULTS[k]; });
    options = options || {};
    Object.keys(options).forEach(function (k) { opts[k] = options[k]; });

    if (isBlank(opts.apiBase)) throw new Error("apiBase is required");
    var prev = this.recordId;
    if (isBlank(prev)) {
      // if no current record, just start()
      return await this.start(opts);
    }

    var created = await this._createRecord(opts, prev);
    created.previousRecordId = prev;

    // close existing ws (best-effort)
    try { if (this.ws) this.ws.close(); } catch (e) {}

    // reset record-level state (keep patches)
    this.recordId = null;
    this.shareUrl = null;
    this.ingestWsUrl = null;
    this.previousRecordId = null;

    this._applyRecord(opts, created);

    return this.getState();
  };

  // singleton
  var sdk = new RecordRoomSDK();

  // expose
  global.RecordRoom = {
    start: function (opts) { return sdk.start(opts); },
    newRecord: function (opts) { return sdk.newRecord(opts); },
    getState: function () { return sdk.getState(); },
    breadcrumb: function (name, message, data) { return sdk.breadcrumb(name, message, data); }
  };
})(window);
