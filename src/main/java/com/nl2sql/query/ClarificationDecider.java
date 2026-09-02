package com.nl2sql.query;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 澄清触发预判定（REQ-512、D-17 加固）。
 * qwen 对“对比一下 top”“每个的多少”这种问句常把 is_query 打成 false 或给出 0.8+ 的自信，
 * 导致 4001/4002 触发条件都漏掉。这里用规则兜底：
 * - 领域关键词为 0（没有指标/维度词）+ 指标未命中 → 缺语义锚点，强制澄清
 * - 命中了领域关键词但 confidence 偏低 → 走 4002（与原逻辑对齐）
 */
@Component
public class ClarificationDecider {

    /** orders 表里的业务锚点关键词（含指标词 + 维度词）。 */
    private static final Set<String> KEYWORDS = Set.of(
            // 指标词
            "销售额", "销售", "订单量", "订单", "客单价", "客单", "销量", "数量",
            "退款", "金额", "消费", "复购率", "复购", "回头率", "平均订单",
            // 维度词
            "地区", "区域", "华南", "华北", "华东", "华中", "西南", "西北", "东北", "大区",
            "商品", "类目", "渠道", "app", "web", "小程序", "门店",
            "用户", "客户", "消费额", "单价",
            "今天", "昨天", "上周", "本月", "上月", "上个月", "近7天", "近30天", "近一天",
            "最近", "每日", "每天", "每月", "每个", "排名", "前10", "top", "TOP", "占比",
            "对比", "环比", "同比");

    /** 中文单字（"的"、"了"、"和" 这类助词）过多时 → 视为无意义残句。注意 JDK Set.of 不允许重复元素，已去重。 */
    private static final Set<Character> STOP_CHARS =
            Set.of('的', '了', '和', '与', '及', '是', '在', '有', '吗', '吧', '啊',
                   '一', '我', '你', '他', '它', '看', '给', '说', '请',
                   '下', '上', '中', '出', '差', '分', '新', '老', '些',
                   '呀', '嘛', '每', '个', '对', '比', '把',
                   '没', '不', '就', '还', '会', '能', '想', '要', '到', '过', '得');

    /** 至少需要多少个锚点字符才算“有业务含义”（按字符计数，不是词）。 */
    private static final int MIN_KEYWORD_CHARS = 2;

    /**
     * 是否应该触发澄清（在 LLM 之外、或者 is_query=false 时兜底调用）。
     * 命中任意一条规则 → 建议澄清，不要直接 4001。
     *
     * @param question 原问题
     * @param metricMatched 是否已经匹配到指标
     * @param isQuery LLM 返回的 isQuery（可能 null）
     * @param confidence LLM 返回的 confidence（可能 null）
     */
    public Decision decide(String question, boolean metricMatched, Boolean isQuery, Double confidence) {
        int keywordChars = countKeywordChars(question);
        boolean noAnchor = !metricMatched && keywordChars < MIN_KEYWORD_CHARS;

        // 规则 1：完全缺语义锚点 → 强制澄清（不要 4001）
        if (noAnchor) {
            return new Decision(true, "缺语义锚点：未命中指标且领域关键词<" + MIN_KEYWORD_CHARS + "字");
        }

        // 规则 2：LLM 自己说不是查询，但我们检测到了领域关键词（锚点强度足够）→ 降级澄清，别直接 4001。
        // 注意：这里故意不写 !metricMatched。规则 2 需要覆盖两种都可能出现 isQuery=false 的场景：
        //   (a) metricMatched=false（比如"对比一下 top5"，没有指标名但有领域词）
        //   (b) metricMatched=true（比如"每个的多少 已完成订单量"，指标命中了但 LLM 觉得模板缺维度补不了）
        // 真非问数（keywordChars < 2，比如"你好"）由规则 1 兜底，不会走到这里。
        if (Boolean.FALSE.equals(isQuery) && keywordChars >= MIN_KEYWORD_CHARS) {
            return new Decision(true, "LLM 判非问数但检测到领域关键词，降级澄清");
        }

        // 规则 3：置信度 <0.75 且未命中指标 → 走澄清（原 0.60 放宽到 0.75，更敏感）
        if (!metricMatched && confidence != null && confidence < 0.75) {
            return new Decision(true, "未命中指标且 confidence<0.75");
        }

        return new Decision(false, null);
    }

    private int countKeywordChars(String q) {
        if (q == null) return 0;
        int total = 0;
        for (String kw : KEYWORDS) {
            int idx = 0;
            while ((idx = q.indexOf(kw, idx)) >= 0) {
                total += kw.length();
                idx += kw.length();
            }
        }
        // 去掉纯助词部分，避免“对比一下top”里助词算入“长度”导致误判命中
        int meaningful = 0;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (!STOP_CHARS.contains(c) && !Character.isWhitespace(c)) meaningful++;
        }
        return meaningful == 0 ? 0 : Math.min(total, meaningful);
    }

    /** 判定结果：shouldClarify + 触发原因（仅用于日志，不对外）。 */
    public record Decision(boolean shouldClarify, String reason) {}
}
