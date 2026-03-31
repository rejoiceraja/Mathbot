package com.mathbot.api.controller;

import com.mathbot.api.dto.response.ApiResponse;
import com.mathbot.api.dto.response.DashboardDto;
import com.mathbot.api.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardDto>> getDashboard(
            @AuthenticationPrincipal String userId) {
        DashboardDto dashboard = progressService.getDashboard(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }
}
