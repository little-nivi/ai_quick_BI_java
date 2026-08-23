package com.nl2sql.query;

import com.nl2sql.cache.SemanticCache;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResponse;
import com.nl2sql.llm.dto.LlmUsage;
import com.nl2sql.observability.LangfuseObservability;
import com.nl2sql.auth.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询编排（M4 升级，REQ-406）：
 * 语义缓存检查 → 指标匹配 → 术语映射 → 生成(含意图/置信度) → 意图识别 → 置信度分级 →
 * AST 校验 → 权限注入 → LIMIT 注入 → 只读执行(报错→自我修正) → 溯源 → 审计 → 会话写穿透 → 缓存写入。
 */
@Service
public class QueryService {

    private final SemanticCache semanticCache;
    private final DegradationHandler degradationHandler;
    private final MetricMatcher metricMatcher;
    private final GlossaryMatcher glossaryMatcher;
    private final SqlGenerator sqlGenerator;
    private final IntentConfidenceEvaluator evaluator;
    private final ClarificationGenerator clarificationGenerator;
    private final SelfCorrector selfCorrector;
    private final SqlValidator sqlValidator;
    private final PermissionInjector permissionInjector;
    private final LimitInjector limitInjector;
    private final SqlExecutor sqlExecutor;
    private final TraceBuilder traceBuilder;
    private final AuditService auditService;
    private final SessionRecorder sessionRecorder;
    private final LangfuseObservability observability;
    private final String model;

    public QueryService(SemanticCache semanticCache,
                        DegradationHandler degradationHandler,
                        MetricMatcher metricMatcher,
                        GlossaryMatcher glossaryMatcher,
                        SqlGenerator sqlGenerator,
                        IntentConfidenceEvaluator evaluator,
                        ClarificationGenerator clarificationGenerator,
                        SelfCorrector selfCorrector,
                        SqlValidator sqlValidator,
                        PermissionInjector permissionInjector,
                        LimitInjector limitInjector,
                        SqlExecutor sqlExecutor,
                        TraceBuilder traceBuilder,
                        AuditService auditService,
                        SessionRecorder sessionRecorder,
                        LangfuseObservability observability,
                        @Value("${nl2sql.llm.model:qwen-turbo}") String model) {
        this.semanticCache = semanticCache;
        this.degradationHandler = degradationHandler;
        this.metricMatcher = metricMatcher;
        this.glossaryMatcher = glossaryMatcher;
        this.sqlGenerator = sqlGenerator;
        this.evaluator = evaluator;
        this.clarificationGenerator = clarificationGenerator;
        this.selfCorrector = selfCorrector;
        this.sqlValidator = sqlValidator;
        this.permissionInjector = permissionInjector;
        this.limitInjector = limitInjector;
        this.sqlExecutor = sqlExecutor;
        this.traceBuilder = traceBuilder;
        this.auditService = auditService;
        this.sessionRecorder = sessionRecorder;
        this.observability = observability;
        this.model = model;
    }

    public QueryResponse query(String question, String ipAddress) {
        long start = System.currentTimeMillis();

        // REQ-516 语义缓存检查
        Object cached = semanticCache.get(question);
        if (cached instanceof QueryResponse cachedResp) {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.record(question, cachedResp.sql(), latencyMs, ipAddress, true);
            sessionRecorder.record(question, cachedResp.sql(), true);
            return new QueryResponse(question, cachedResp.sql(), cachedResp.columns(), cachedResp.rows(),
                    cachedResp.rowCount(), latencyMs, cachedResp.matchedMetric(),
                    cachedResp.confidence(), cachedResp.trace(), true);
        }

        String generatedSql = null;
        String matchedMetric = null;
        Double confidence = null;
        String confidenceLevel = null;
        boolean cacheHit = false;

        try {
            // REQ-506 指标匹配 + REQ-510 术语映射
            MetricMatcher.MetricMatch match = metricMatcher.match(question);
            String questionForLlm = glossaryMatcher.apply(question);

            // REQ-501/506 生成（含 is_query + confidence，一次调用）；超时→降级链 REQ-518
            LlmResponse llm;
            LlmUsage usage;
            try {
                SqlGenerator.SqlGeneration gen;
                if (match != null) {
                    matchedMetric = match.metricName();
                    gen = sqlGenerator.generateWithTemplate(questionForLlm, match.templateSql());
                } else {
                    gen = sqlGenerator.generate(questionForLlm);
                }
                llm = gen.response();
                usage = gen.usage();
            } catch (BizException e) {
                if (e.getErrorCode() == ErrorCode.LLM_TIMEOUT) {
                    // 降级链：语义缓存兜底
                    Object degraded = degradationHandler.degrade(question);
                    if (degraded instanceof QueryResponse degradedResp) {
                        long latencyMs = System.currentTimeMillis() - start;
                        auditService.record(question, degradedResp.sql(), latencyMs, ipAddress, true);
                        sessionRecorder.record(question, degradedResp.sql(), true);
                        return new QueryResponse(question, degradedResp.sql(), degradedResp.columns(),
                                degradedResp.rows(), degradedResp.rowCount(), latencyMs,
                                degradedResp.matchedMetric(), degradedResp.confidence(),
                                degradedResp.trace(), true);
                    }
                }
                throw e;
            }

            generatedSql = llm.sql();
            confidence = llm.confidence();

            // REQ-511 意图识别 + 置信度分级
            try {
                confidenceLevel = evaluator.evaluate(llm);
            } catch (BizException e) {
                if (e.getErrorCode() == ErrorCode.LOW_CONFIDENCE) {
                    Clarification clarification = clarificationGenerator.generate(question);
                    throw new ClarifyException(clarification);
                }
                throw e;
            }

            // REQ-509 AST 校验
            sqlValidator.validate(generatedSql);

            // REQ-507 权限注入 + REQ-520 LIMIT 注入 + REQ-503 只读执行（报错→自我修正）
            String executedSql = generatedSql;
            String scopedSql = limitInjector.inject(permissionInjector.inject(generatedSql));
            SqlExecutor.QueryResult result;
            try {
                result = sqlExecutor.execute(scopedSql);
            } catch (BizException e) {
                if (e.getErrorCode() == ErrorCode.DB_TIMEOUT) {
                    throw e;
                }
                String corrected = selfCorrector.correct(question, generatedSql, e.getMessage());
                if (corrected == null) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR);
                }
                sqlValidator.validate(corrected);
                executedSql = corrected;
                result = sqlExecutor.execute(limitInjector.inject(permissionInjector.inject(corrected)));
            }

            long latencyMs = System.currentTimeMillis() - start;

            // REQ-514 溯源
            Trace trace = traceBuilder.build(result.columns(), matchedMetric, confidence, confidenceLevel);

            // REQ-508 审计 + REQ-519 会话写穿透 + REQ-531 观测
            auditService.record(question, executedSql, latencyMs, ipAddress, false);
            sessionRecorder.record(question, executedSql, false);
            observability.record(question, executedSql, latencyMs, false, confidenceLevel,
                    matchedMetric, currentRole(), model, usage);

            QueryResponse response = new QueryResponse(question, limitInjector.inject(permissionInjector.inject(executedSql)),
                    result.columns(), result.rows(), result.rows().size(), latencyMs, matchedMetric,
                    confidence, trace, false);

            // REQ-516 语义缓存写入（空结果也缓存，防穿透）
            semanticCache.put(question, response, result.rows().isEmpty());

            return response;
        } catch (ClarifyException e) {
            auditService.record(question, generatedSql, System.currentTimeMillis() - start, ipAddress, false);
            throw e;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.record(question, generatedSql, latencyMs, ipAddress, false);
            sessionRecorder.record(question, generatedSql, false);
            throw e;
        }
    }

    private String currentRole() {
        UserContext.AuthUser user = UserContext.get();
        return user == null ? null : user.role();
    }

    /** 接口返回 data 结构（REQ-406 正常返回，新增 cache_hit）。 */
    public record QueryResponse(
            String question,
            String sql,
            List<String> columns,
            List<List<Object>> rows,
            int rowCount,
            long latencyMs,
            String matchedMetric,
            Double confidence,
            Trace trace,
            boolean cacheHit) {
    }
}
