package com.fix.gateway.controller;

import com.fix.gateway.model.RoutingConfig;
import com.fix.gateway.service.RoutingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routing")
public class RoutingController {

    @Autowired
    private RoutingService routingService;

    @GetMapping("/routes")
    public ResponseEntity<List<RoutingConfig.RouteMapping>> getAllRoutes() {
        return ResponseEntity.ok(routingService.getAllRoutes());
    }

    @GetMapping("/match")
    public ResponseEntity<Map<String, Object>> findRoutes(
            @RequestParam String senderCompId,
            @RequestParam String targetCompId) {
        
        List<RoutingConfig.RouteMapping> routes = routingService.findMatchingRoutes(senderCompId, targetCompId);
        List<String> destinations = routingService.getDestinationsFor(senderCompId, targetCompId);
        
        return ResponseEntity.ok(Map.of(
            "senderCompId", senderCompId,
            "targetCompId", targetCompId,
            "matchingRoutes", routes,
            "destinations", destinations,
            "hasRoute", !routes.isEmpty()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "FIX Message Router",
            "version", "1.0.0"
        ));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        List<RoutingConfig.RouteMapping> routes = routingService.getAllRoutes();
        return ResponseEntity.ok(Map.of(
            "totalRoutes", routes.size(),
            "routes", routes
        ));
    }
}