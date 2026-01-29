package com.example.recordroom.api;

import com.example.recordroom.model.AdminOverviewResponse;
import com.example.recordroom.service.RecordroomService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final RecordroomService service;

    public AdminApiController(RecordroomService service) {
        this.service = service;
    }

    /**
     * Monitoring overview (demo-quality, no auth)
     * - q: free text search across recordId/sessionId/pageUrl/userAgent/deviceInfo/userId/userEmail
     * - errorsOnly: if true, only records with consoleErrorCount>0 or networkHttpErrorCount>0
     * - fromTs/toTs: epoch ms range for global counters/bytes (not for record list)
     * - limit: record rows to return (max 500)
     */
    @GetMapping(value = "/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdminOverviewResponse overview(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean errorsOnly,
            @RequestParam(required = false) Long fromTs,
            @RequestParam(required = false) Long toTs,
            @RequestParam(required = false, defaultValue = "200") int limit
    ) {
        return service.getAdminOverview(q, errorsOnly, fromTs, toTs, limit);
    }
}


