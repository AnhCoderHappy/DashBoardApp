package com.mdata.backend.controller;

import com.mdata.backend.connector.PancakeConnector;
import com.mdata.backend.entity.PlatformConnection;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/connections")
@CrossOrigin(origins = "*")
public class PlatformConnectionController {

    @Autowired
    private PlatformConnectionRepository connectionRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PancakeConnector pancakeConnector;

    @GetMapping
    public ResponseEntity<List<PlatformConnection>> getConnections() {
        return ResponseEntity.ok(connectionRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> addConnection(@RequestBody Map<String, String> payload) {
        try {
            String platform = payload.get("platform");
            String apiKey = payload.get("apiKey");

            if (platform == null || platform.isEmpty() || apiKey == null || apiKey.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Missing required fields (platform, apiKey)"));
            }

            Map<String, String> shopInfo = pancakeConnector.fetchShopInfo(apiKey);
            String shopId = shopInfo.get("id");
            String fetchedName = shopInfo.get("name");
            String shopName = payload.get("shopName");
            if (shopName == null || shopName.trim().isEmpty()) {
                shopName = fetchedName;
            }

            PlatformConnection connection = new PlatformConnection();
            connection.setPlatform(platform);
            connection.setShopId(shopId);
            connection.setShopName(shopName);
            connection.setStatus("active");

            PlatformConnection savedConnection = connectionRepository.save(connection);
            tokenService.saveConnectionToken(savedConnection.getId(), apiKey);

            return ResponseEntity.ok(Map.of(
                    "message", "Connection saved successfully",
                    "connection", savedConnection
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "An error occurred: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateConnection(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        try {
            PlatformConnection connection = connectionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

            if (payload.containsKey("shopName")) {
                connection.setShopName(payload.get("shopName"));
            }
            
            // Only update apiKey if provided and not empty
            String apiKey = payload.get("apiKey");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                Map<String, String> shopInfo = pancakeConnector.fetchShopInfo(apiKey);
                connection.setShopId(shopInfo.get("id")); // Update shopId linked to new API Key
                if (!payload.containsKey("shopName") || payload.get("shopName").trim().isEmpty()) {
                    connection.setShopName(shopInfo.get("name")); // Auto update name if none provided
                }
                tokenService.saveConnectionToken(connection.getId(), apiKey);
            }

            PlatformConnection savedConnection = connectionRepository.save(connection);
            return ResponseEntity.ok(Map.of(
                    "message", "Connection updated successfully",
                    "connection", savedConnection
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "An error occurred: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConnection(@PathVariable UUID id) {
        try {
            if (!connectionRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            // First, delete the token to maintain referential integrity / clean up
            tokenService.deleteConnectionToken(id);
            // Then delete the connection
            connectionRepository.deleteById(id);
            
            return ResponseEntity.ok(Map.of("message", "Connection deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "An error occurred: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> testConnection(@PathVariable UUID id) {
        try {
            PlatformConnection connection = connectionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Connection not found"));

            boolean isAlive = false;
            if ("pancake".equals(connection.getPlatform())) {
                isAlive = pancakeConnector.testConnection(id);
            }

            if (isAlive) {
                connection.setStatus("active");
                connectionRepository.save(connection);
                return ResponseEntity.ok(Map.of("message", "Connection is working properly", "status", "active"));
            } else {
                connection.setStatus("error");
                connection.setLastErrorMessage("API Test failed. Status not 200 OK.");
                connectionRepository.save(connection);
                return ResponseEntity.badRequest().body(Map.of("message", "Connection failed. Please check your API Key.", "status", "error"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Connection test error: " + e.getMessage()));
        }
    }
}
