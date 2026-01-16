package com.example.recordroom.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/demo/api")
public class DemoApiController {

    @GetMapping(value = "/ok", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ok() {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("message", "ok");
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    @GetMapping(value = "/slow", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> slow(@RequestParam(name = "ms", defaultValue = "800") long ms) throws InterruptedException {
        Thread.sleep(ms);
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("message", "slow " + ms + "ms");
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    @GetMapping(value = "/fail", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> fail() {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("code", "E_DEMO_FAIL");
        m.put("message", "demo failure");
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    @PostMapping(value = "/echo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("echo", body);
        m.put("ts", System.currentTimeMillis());
        return m;
    }
}
