package com.fix.gateway.service;

import com.fix.gateway.model.DestinationConfig;
import com.fix.gateway.model.RouteMapping;
import com.fix.gateway.model.RoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoutingService {

    @Autowired
    private RoutingConfig routingConfig;

    /**
     * Get all available routes
     */
    public List<RouteMapping> getAllRoutes() {
        return routingConfig.getRoutes();
    }

    /**
     * Find routes matching sender and target comp IDs
     */
    public List<RouteMapping> findMatchingRoutes(String senderCompId, String targetCompId) {
        return routingConfig.getRoutes().stream()
            .filter(route -> matchesRoute(route, senderCompId, targetCompId))
            .collect(Collectors.toList());
    }

    /**
     * Get destinations for a specific sender/target combination
     */
    public List<String> getDestinationsFor(String senderCompId, String targetCompId) {
        return findMatchingRoutes(senderCompId, targetCompId).stream()
            .flatMap(route -> route.getDestinationConfigs().stream())
            .map(DestinationConfig::buildCompleteUri)
            .collect(Collectors.toList());
    }

    /**
     * Check if a route exists for the given sender/target
     */
    public boolean hasRouteFor(String senderCompId, String targetCompId) {
        return routingConfig.getRoutes().stream()
            .anyMatch(route -> matchesRoute(route, senderCompId, targetCompId));
    }

    private boolean matchesRoute(RouteMapping route, String senderCompId, String targetCompId) {
        boolean senderMatch = route.getSenderCompId().equalsIgnoreCase(senderCompId);
        boolean targetMatch = route.getTargetCompId().equalsIgnoreCase(targetCompId);
        return senderMatch && targetMatch;
    }
}