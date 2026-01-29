package com.example.recordroom.web;

import com.example.recordroom.model.NetworkEvent;
import com.example.recordroom.model.Record;
import com.example.recordroom.service.RecordroomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class RecordPageController {

    private final RecordroomService service;

    public RecordPageController(RecordroomService service) {
        this.service = service;
    }

    @GetMapping("/demo")
    public String demo(Model model) {
        model.addAttribute("appVersion", "0.5.0");
        return "demo";
    }

    @GetMapping("/demo-sdk")
    public String demoSdk(Model model) {
        model.addAttribute("appVersion", "0.5.0");
        return "demo-sdk";
    }

    @GetMapping("/sdk-install")
    public String sdkInstall() {
        return "sdk-install";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin-dashboard";
    }


    @GetMapping("/r/{recordId}")
    public String recordRoot(@PathVariable String recordId) {
        return "redirect:/r/" + recordId + "/timeline";
    }

    @GetMapping("/r/{recordId}/timeline")
    public String timeline(@PathVariable String recordId, Model model) {
        Record r = service.getRecord(recordId);
        if (r == null) throw new ResponseStatusException(NOT_FOUND, "record not found");
        model.addAttribute("recordId", recordId);
        return "record-timeline";
    }
    @GetMapping("/r/{recordId}/console")
    public String console(@PathVariable String recordId, Model model) {
        if (!service.recordExists(recordId)) throw new ResponseStatusException(NOT_FOUND, "record not found");
        model.addAttribute("recordId", recordId);
        return "record-console";
    }

    @GetMapping("/r/{recordId}/network")
    public String network(@PathVariable String recordId, Model model) {
        if (!service.recordExists(recordId)) throw new ResponseStatusException(NOT_FOUND, "record not found");
        model.addAttribute("recordId", recordId);
        return "record-network";
    }

    @GetMapping("/r/{recordId}/network/{eventId}")
    public String networkDetail(@PathVariable String recordId, @PathVariable String eventId, Model model) {
        if (!service.recordExists(recordId)) throw new ResponseStatusException(NOT_FOUND, "record not found");
        NetworkEvent e = service.getNetworkDetail(recordId, eventId);
        if (e == null) throw new ResponseStatusException(NOT_FOUND, "network event not found");
        model.addAttribute("recordId", recordId);
        model.addAttribute("event", e);
        return "record-network-detail";
    }


    @GetMapping("/r/{recordId}/replay")
    public String replay(@PathVariable String recordId, Model model) {
        if (!service.recordExists(recordId)) throw new ResponseStatusException(NOT_FOUND, "record not found");
        model.addAttribute("recordId", recordId);
        return "record-replay";
    }

    @GetMapping("/r/{recordId}/breadcrumbs")
    public String breadcrumbs(@PathVariable String recordId, Model model) {
        if (!service.recordExists(recordId)) throw new ResponseStatusException(NOT_FOUND, "record not found");
        model.addAttribute("recordId", recordId);
        return "record-breadcrumbs";
    }

    @GetMapping("/rec/{recordId}")
    public String recorder(@PathVariable String recordId, Model model) {
        if (!service.recordExists(recordId)) throw new ResponseStatusException(NOT_FOUND, "record not found");
        model.addAttribute("recordId", recordId);
        return "record-recorder";
    }

    @GetMapping("/sessions/{sessionId}")
    public String sessionView(@PathVariable String sessionId, Model model) {
        model.addAttribute("sessionId", sessionId);
        return "session-view";
    }
}
