package com.nl2sql.query;

import com.nl2sql.common.ApiResponse;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.common.TraceIdHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 查询接口（REQ-403，M2 升级：鉴权由 AuthFilter 完成）。
 * 含 Prompt 注入防御（REQ-505、SR-3）与并发超限（NFR-3）。
 */
@RestController
@RequestMapping("/api/v1")
public class QueryController {

    /** SR-3 注入模式（ADR-0002 C6）。 */
    private static final Pattern INJECTION = Pattern.compile(
            "忽略以上指令|忽略上述|ignore\\s+(all|above|previous)|system\\s*覆盖|扮演");

    /** 并发超限阈值（ADR-0002 B5）。 */
    private static final int MAX_CONCURRENT = 10;

    private final QueryService queryService;
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/query")
    public ApiResponse<QueryService.QueryResponse> query(@Valid @RequestBody QueryRequest request,
                                                         HttpServletRequest httpRequest) {
        String traceId = TraceIdHolder.getOrCreate();

        // REQ-505 注入防御
        if (INJECTION.matcher(request.question()).find()) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, traceId);
        }

        // NFR-3 并发超限
        int current = inFlight.incrementAndGet();
        if (current > MAX_CONCURRENT) {
            inFlight.decrementAndGet();
            return ApiResponse.error(ErrorCode.RATE_LIMITED, traceId);
        }

        try {
            QueryService.QueryResponse result = queryService.query(request.question(), clientIp(httpRequest));
            return ApiResponse.ok(result, traceId);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record QueryRequest(
            @NotBlank(message = "question 不能为空")
            @Size(max = 500, message = "question 不能超过 500 字符")
            String question) {
    }
}
