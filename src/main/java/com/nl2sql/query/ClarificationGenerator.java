package com.nl2sql.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import com.nl2sql.governance.MetricGovernanceService;
import com.nl2sql.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 反问澄清生成（REQ-512、D-18）。
 * <p>
 * 关键设计：澄清选项 <b>只允许来自已发布的指标列表</b>，不能让 LLM 自由发明"你关心哪个时间段"
 * 这类与业务口径无关的话术。否则治理层（published / deprecated）机制就失去了执行抓手。
 */
@Component
public class ClarificationGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClarificationGenerator.class);

    /** 固定问法（不交给 LLM 发挥，保证所有澄清弹窗观感统一）。 */
    private static final String FIXED_QUESTION =
            "您想问的是下面哪个已发布的指标？如果都不对，请更具体地描述您的问题（例如加上销售额 / 复购率 / 订单量等指标名）。";

    private static final int MAX_OPTIONS = 4;
    private static final int MIN_OPTIONS = 2;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final MetricGovernanceService metricGovernanceService;

    public ClarificationGenerator(LlmClient llmClient,
                                  ObjectMapper objectMapper,
                                  MetricGovernanceService metricGovernanceService) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.metricGovernanceService = metricGovernanceService;
    }

    public Clarification generate(String question) {
        // 1) 拿已发布指标候选池（治理口径 = 只有 published 才允许出现在选项里）
        List<Map<String, Object>> published = metricGovernanceService.listPublished();
        if (published == null || published.isEmpty()) {
            // 防御：一个指标都没发布时（极早期 / 纯 SQL 自由生成模式）退回通用兜底，不 NPE
            return fallbackUniversal(question);
        }

        List<String> allNames = published.stream()
                .map(m -> (String) m.get("metric_name"))
                .filter(Objects::nonNull)
                .toList();

        // 2) 先用启发式本地做一次字符命中打分，拿到 6 个最相关的候选作为「LLM 短名单」
        //    —— 降低 prompt 里塞 20+ 条导致 LLM 跑偏到 3 条无关选项的概率
        List<String> shortlist = shortlistByKeywordMatch(question, published, 6);

        // 3) LLM 在短名单里硬选 3 个（prompt 强约束：必须来自选项，不在列表里就判错，fallback）
        List<String> picked = pickByLlm(question, shortlist);
        if (picked == null || picked.size() < MIN_OPTIONS) {
            picked = shortlist.stream().limit(MAX_OPTIONS).toList();
            log.warn("clarification LLM pick <2 options, fallback to keyword-top: question={}, picked={}",
                    question, picked);
        }

        // 4) 二次守卫：LLM 可能偷偷返回不在 published 的选项（幻觉）。把不合法的替换成短名单头部，
        //    最终保证用户点击任何一条 option，下次请求都能走到 MetricMatcher 同义词命中路径。
        Set<String> legal = new HashSet<>(allNames);
        List<String> safeOptions = new ArrayList<>();
        for (String p : picked) {
            if (legal.contains(p)) safeOptions.add(p);
        }
        Iterator<String> headIter = shortlist.iterator();
        while (safeOptions.size() < MIN_OPTIONS && headIter.hasNext()) {
            String n = headIter.next();
            if (!safeOptions.contains(n)) safeOptions.add(n);
        }
        if (safeOptions.isEmpty()) {
            safeOptions = allNames.stream().limit(MAX_OPTIONS).collect(Collectors.toList());
        }
        if (safeOptions.size() > MAX_OPTIONS) {
            safeOptions = safeOptions.subList(0, MAX_OPTIONS);
        }

        return new Clarification(FIXED_QUESTION, safeOptions);
    }

    // ================== 私有辅助 ==================

    /** 本地启发式：对 question 字符在 metric_name + synonyms 的命中长度计分，取 topN。 */
    private List<String> shortlistByKeywordMatch(String question,
                                                 List<Map<String, Object>> published,
                                                 int topN) {
        String q = question == null ? "" : question.trim();

        record Score(String name, int s) {}
        List<Score> scored = new ArrayList<>(published.size());

        for (Map<String, Object> m : published) {
            String name = (String) m.get("metric_name");
            String synonyms = (String) m.get("synonyms");
            String definition = (String) m.get("definition");
            if (name == null) continue;

            int s = 0;
            // 字符命中（不需要分词，中文子串命中足够当启发式）
            String bag = name + " " + (synonyms == null ? "" : synonyms) + " " + (definition == null ? "" : definition);
            for (int i = 0; i < q.length(); i++) {
                char c = q.charAt(i);
                if (Character.isWhitespace(c)) continue;
                if (bag.indexOf(c) >= 0) s += 1;
            }
            // 名字完全命中（如 q 含"销售"且 name 含"销售额"）额外加分
            if (!q.isEmpty() && (name.contains(q) || (q.length() >= 2 && containsAnySub(name, q, 2)))) {
                s += 10;
            }
            // 同义词拆分命中额外加分
            if (synonyms != null) {
                for (String syn : synonyms.split(",")) {
                    String t = syn.trim();
                    if (!t.isEmpty() && q.contains(t)) s += 5;
                }
            }
            scored.add(new Score(name, s));
        }

        return scored.stream()
                .sorted((a, b) -> Integer.compare(b.s(), a.s()))
                .limit(topN)
                .map(Score::name)
                .toList();
    }

    private static boolean containsAnySub(String longer, String shorter, int minLen) {
        if (shorter.length() < minLen || longer.length() < minLen) return false;
        for (int i = 0; i <= shorter.length() - minLen; i++) {
            if (longer.contains(shorter.substring(i, i + minLen))) return true;
        }
        return false;
    }

    /** LLM 强约束：options 每一项都必须来自 shortlist 候选，且 2~4 项。非法时返回 null 让上层 fallback。 */
    private List<String> pickByLlm(String question, List<String> shortlist) {
        String legalSetStr = String.join("、", shortlist);
        String prompt =
                "你是企业数仓问答系统的澄清助手。用户的原始问题：\"" + question + "\"。\n" +
                        "系统中「已审批发布」的指标清单如下（按相关性从高到低排序）：\n" +
                        "  - " + legalSetStr + "\n\n" +
                        "任务：请从上面【已发布指标清单】中选择 3 个最符合用户意图的指标作为澄清选项。\n" +
                        "严格规则（违反即不合格）：\n" +
                        "  1) options 数组中每一个字符串都必须是上面清单里已有的一个完整指标名，一字不差；\n" +
                        "  2) 不允许出现清单以外的任何文字（例如'时间段'、'维度'、'更多描述'、'其他'、'请补充'都算不合格）；\n" +
                        "  3) options 长度 2~4 个；\n" +
                        "  4) question 字段固定写成：\"您想问的是下面哪个已发布的指标？如果都不对，请更具体地描述您的问题。\"；\n" +
                        "  5) 整个回答只能是合法 JSON，不要 markdown 块、不要反引号、不要解释文字。\n\n" +
                        "输出格式：{\"question\": \"...\", \"options\": [\"指标A\", \"指标B\", \"指标C\"]}";

        String content;
        try {
            content = llmClient.generate(prompt).content();
        } catch (Exception e) {
            log.warn("clarification llm call failed, fallback to keyword-only: {}", e.toString());
            return null;
        }

        try {
            Map<String, Object> map = objectMapper.readValue(content, Map.class);
            Object optObj = map.get("options");
            if (!(optObj instanceof List<?> list)) return null;
            return list.stream()
                    .map(o -> o == null ? null : o.toString().trim())
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(MAX_OPTIONS + 1L)
                    .toList();
        } catch (Exception e) {
            log.warn("clarification llm JSON parse failed: content={}, err={}", content, e.toString());
            return null;
        }
    }

    /** 防御兜底：没有 published 指标时（项目首启阶段）的通用澄清。 */
    private Clarification fallbackUniversal(String question) {
        try {
            String prompt = "用户问题：\"" + question + "\"。当前还没有任何已发布的业务指标，" +
                    "请生成 1 句澄清问题 + 2 个通用引导选项（例如：请具体描述您的业务指标 / 先去指标管理页新建一个指标）。" +
                    "只输出 JSON {\"question\": \"...\", \"options\": [\"a\", \"b\"]}。";
            String content = llmClient.generate(prompt).content();
            Map<String, Object> map = objectMapper.readValue(content, Map.class);
            String q = (String) map.get("question");
            Object optObj = map.get("options");
            List<String> options = optObj instanceof List
                    ? ((List<?>) optObj).stream().map(String::valueOf).toList()
                    : List.of();
            return new Clarification(q, options);
        } catch (Exception e) {
            log.warn("fallback universal clarification failed: {}", e.toString());
            return new Clarification(
                    "您的问题还缺少具体的业务指标名，请问您想查询哪类数据？",
                    List.of("请先到'指标管理'页面新建并审批发布您需要的业务指标",
                            "请更具体地描述，例如加上'总销售额 / 订单数 / 用户数'等明确指标名"));
        }
    }
}
