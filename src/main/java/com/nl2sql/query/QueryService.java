package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.dto.LlmResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询编排（M3 升级，REQ-404）：
 * 指标匹配 → 术语映射 → 生成(含意图/置信度) → 意图识别 → 置信度分级 →
 * AST 校验 → 权限注入 → 只读执行(报错→自我修正) → 溯源 → 审计。
 */
@Service
public class QueryService {

    private final MetricMatcher metricMatcher;
    private final GlossaryMatcher glossaryMatcher;
    private final SqlGenerator sqlGenerator;
    private final IntentConfidenceEvaluator evaluator;
    private final ClarificationGenerator clarificationGenerator;
    private final SelfCorrector selfCorrector;
    private final SqlValidator sqlValidator;
    private final PermissionInjector permissionInjector;
    private final SqlExecutor sqlExecutor;
    private final TraceBuilder traceBuilder;
    private final AuditService auditService;

    public QueryService(MetricMatcher metricMatcher,
                        GlossaryMatcher glossaryMatcher,
                        SqlGenerator sqlGenerator,
                        IntentConfidenceEvaluator evaluator,
                        ClarificationGenerator clarificationGenerator,
                        SelfCorrector selfCorrector,
                        SqlValidator sqlValidator,
                        PermissionInjector permissionInjector,
                        SqlExecutor sqlExecutor,
                        TraceBuilder traceBuilder,
                        AuditService auditService) {
        this.metricMatcher = metricMatcher;
        this.glossaryMatcher = glossaryMatcher;
        this.sqlGenerator = sqlGenerator;
        this.evaluator = evaluator;
        this.clarificationGenerator = clarificationGenerator;
        this.selfCorrector = selfCorrector;
        this.sqlValidator = sqlValidator;
        this.permissionInjector = permissionInjector;
        this.sqlExecutor = sqlExecutor;
        this.traceBuilder = traceBuilder;
        this.auditService = auditService;
    }

    public QueryResponse query(String question, String ipAddress) {
        long start = System.currentTimeMillis();
        String generatedSql = null;
        String matchedMetric = null;
        Double confidence = null;
        String confidenceLevel = null;

        try {
            // REQ-506 指标匹配 + REQ-510 术语映射
            MetricMatcher.MetricMatch match = metricMatcher.match(question);
            String questionForLlm = glossaryMatcher.apply(question);

            // REQ-501/506 生成（含 is_query + confidence，一次调用）
            LlmResponse llm;
            if (match != null) {
                matchedMetric = match.metricName();
                llm = sqlGenerator.generateWithTemplate(questionForLlm, match.templateSql());
            } else {
                llm = sqlGenerator.generate(questionForLlm);
            }
            generatedSql = llm.sql();
            confidence = llm.confidence();

            // REQ-511 意图识别 + 置信度分级
            try {
                confidenceLevel = evaluator.evaluate(llm);
            } catch (BizException e) {
                if (e.getErrorCode() == ErrorCode.LOW_CONFIDENCE) {
                    // REQ-512 反问澄清
                    Clarification clarification = clarificationGenerator.generate(question);
                    throw new ClarifyException(clarification);
                }
                throw e; // 4001 非问数
            }

            // REQ-509 AST 校验
            sqlValidator.validate(generatedSql);

            // REQ-507 权限注入 + REQ-503 只读执行（报错→自我修正 REQ-513）
            String executedSql = generatedSql;
            String scopedSql = permissionInjector.inject(generatedSql);
            SqlExecutor.QueryResult result;
            try {
                result = sqlExecutor.execute(scopedSql);
            } catch (BizException e) {
                if (e.getErrorCode() == ErrorCode.DB_TIMEOUT) {
                    throw e; // 超时不修正（P1-5）
                }
                // 语法/字段类错误 → 自我修正
                String corrected = selfCorrector.correct(question, generatedSql, e.getMessage());
                if (corrected == null) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR);
                }
                sqlValidator.validate(corrected);
                executedSql = corrected;
                result = sqlExecutor.execute(permissionInjector.inject(corrected));
            }

            long latencyMs = System.currentTimeMillis() - start;

            // REQ-514 溯源
            Trace trace = traceBuilder.build(result.columns(), matchedMetric, confidence, confidenceLevel);

            // REQ-508 审计
            auditService.record(question, executedSql, latencyMs, ipAddress);

            return new QueryResponse(question, permissionInjector.inject(executedSql), result.columns(),
                    result.rows(), result.rows().size(), latencyMs, matchedMetric, confidence, trace);
        } catch (ClarifyException e) {
            auditService.record(question, generatedSql, System.currentTimeMillis() - start, ipAddress);
            throw e;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            auditService.record(question, generatedSql, latencyMs, ipAddress);
            throw e;
        }
    }

    /** 接口返回 data 结构（REQ-404 正常返回，新增 confidence + trace）。 */
    public record QueryResponse(
            String question,
            String sql,
            List<String> columns,
            List<List<Object>> rows,
            int rowCount,
            long latencyMs,
            String matchedMetric,
            Double confidence,
            Trace trace) {
    }
}
