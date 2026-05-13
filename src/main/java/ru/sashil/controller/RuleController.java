package ru.sashil.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sashil.rules.Rule;
import ru.sashil.rules.RuleEngine;
import ru.sashil.rules.RuleVersionService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {
    private final RuleEngine engine;
    private final RuleVersionService versionService;

    @GetMapping
    public List<Rule> listRules() {
        return engine.getCurrentRules();
    }

    @GetMapping("/version")
    public Map<String, String> getVersion() {
        return Map.of("activeVersion", versionService.getActiveVersion());
    }

    @GetMapping("/versions")
    public List<String> getAvailableVersions() {
        return versionService.getAvailableVersions();
    }

    @PutMapping("/version")
    public ResponseEntity<Map<String, String>> setVersion(@RequestParam("version") String version) {
        versionService.setActiveVersion(version);
        engine.reload();
        log.info("Active rules version changed to: {}", version);
        return ResponseEntity.ok(Map.of("activeVersion", version, "status", "loaded"));
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        engine.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded", "version", versionService.getActiveVersion()));
    }
}
