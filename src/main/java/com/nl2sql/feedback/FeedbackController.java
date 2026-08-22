package com.nl2sql.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nl2sql.common.ApiResponse;
import com.nl2sql.common.TraceIdHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 反馈提交接口（REQ-405）。
 */
@RestController
@RequestMapping("/api/v1")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Long>> submit(@Valid @RequestBody FeedbackRequest request) {
        String traceId = TraceIdHolder.getOrCreate();
        long feedbackId = feedbackService.submit(
                request.question(), request.generatedSql(), request.resultHash(),
                request.feedbackType(), request.userComment(), request.userCorrectedSql());
        return ApiResponse.ok(Map.of("feedback_id", feedbackId), traceId);
    }

    public record FeedbackRequest(
            @NotBlank(message = "question 不能为空") @Size(max = 500) String question,
            @JsonProperty("generated_sql") String generatedSql,
            @JsonProperty("result_hash") @Size(max = 64) String resultHash,
            @NotBlank(message = "feedback_type 不能为空")
            @JsonProperty("feedback_type")
            @Pattern(regexp = "positive|negative", message = "feedback_type 必须为 positive 或 negative")
            String feedbackType,
            @JsonProperty("user_comment") @Size(max = 2000) String userComment,
            @JsonProperty("user_corrected_sql") String userCorrectedSql) {
    }
}
