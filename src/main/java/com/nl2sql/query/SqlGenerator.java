package com.nl2sql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.llm.LlmClient;
import com.nl2sql.llm.dto.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 组装 prompt 并调用 qwen 生成 SQL（REQ-501）。
 * Prompt 四段：系统指令 + orders schema + few-shot + question（ADR-0002 C2）。
 */
@Component
public class SqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerator.class);

    /** REQ-301 orders 全量 schema（固定文本注入）。 */
    private static final String ORDERS_SCHEMA = """
            CREATE TABLE orders (
              order_id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
              user_id    BIGINT UNSIGNED NOT NULL,
              product_id BIGINT UNSIGNED NOT NULL,
              amount     DECIMAL(10,2)  NOT NULL,
              quantity   INT UNSIGNED   NOT NULL,
              price      DECIMAL(10,2)  NOT NULL,
              order_date DATETIME       NOT NULL,
              region     VARCHAR(50)    NOT NULL,
              status     VARCHAR(20)    NOT NULL DEFAULT 'completed',
              channel    VARCHAR(50)    NOT NULL
            );
            """;

    private static final String SYSTEM_PROMPT = """
            你是 SQL 生成助手。根据用户问题，仅针对 orders 表生成一条只读 SELECT 查询。
            只输出 JSON，结构固定为 {"sql": "...", "explanation": "..."}，不要输出任何其他文字。
            规则：
            1. 只允许 SELECT 语句。
            2. 不允许使用 DROP/DELETE/UPDATE/ALTER/TRUNCATE/INSERT/CREATE。
            3. 金额字段 amount、price 使用 SUM 聚合时保留两位小数语义。
            4. 时间过滤使用 order_date。
            """;

    private static final String FEW_SHOT = """
            示例1：问题"总销售额是多少" -> {"sql":"SELECT SUM(amount) FROM orders","explanation":"全部订单金额求和"}
            示例2：问题"各地区的订单数量排名" -> {"sql":"SELECT region, COUNT(*) AS cnt FROM orders GROUP BY region ORDER BY cnt DESC","explanation":"按地区分组统计订单数并降序"}
            示例3：问题"上个月销售额" -> {"sql":"SELECT SUM(amount) FROM orders WHERE order_date >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)","explanation":"近一个月订单金额求和"}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SqlGenerator(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 SQL；上游返回格式异常时重试 1 次（ADR-0002 A3）。
     */
    public LlmResponse generate(String question) {
        String prompt = buildPrompt(question);
        String content = callWithRetry(prompt);
        return parse(content);
    }

    private String buildPrompt(String question) {
        return SYSTEM_PROMPT
                + "\n\n表结构：\n" + ORDERS_SCHEMA
                + "\n\n示例：\n" + FEW_SHOT
                + "\n\n用户问题：" + question;
    }

    private String callWithRetry(String prompt) {
        try {
            return llmClient.generate(prompt);
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.LLM_FORMAT_ERROR) {
                log.warn("llm format error, retry once");
                return llmClient.generate(prompt); // 第 2 次；再失败则异常上抛（TC-401-02）
            }
            throw e;
        }
    }

    private LlmResponse parse(String content) {
        try {
            return objectMapper.readValue(content, LlmResponse.class);
        } catch (Exception e) {
            log.error("llm content not parseable as LlmResponse: {}", content, e);
            throw new BizException(ErrorCode.LLM_FORMAT_ERROR);
        }
    }
}
