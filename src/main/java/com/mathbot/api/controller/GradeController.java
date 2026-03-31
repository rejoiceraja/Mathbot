package com.mathbot.api.controller;

import com.mathbot.api.dto.response.ApiResponse;
import com.mathbot.api.dto.response.GradeDto;
import com.mathbot.api.dto.response.TopicWithProgressDto;
import com.mathbot.api.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grades")
@RequiredArgsConstructor
public class GradeController {

    private final CurriculumService curriculumService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GradeDto>>> getAllGrades() {
        return ResponseEntity.ok(ApiResponse.ok(curriculumService.getAllGrades()));
    }

    @GetMapping("/{gradeId}/topics")
    public ResponseEntity<ApiResponse<List<TopicWithProgressDto>>> getTopics(
            @PathVariable Short gradeId,
            @AuthenticationPrincipal String userId) {
        List<TopicWithProgressDto> topics =
                curriculumService.getTopicsWithProgress(gradeId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(topics));
    }
}
