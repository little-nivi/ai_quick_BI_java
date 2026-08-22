package com.nl2sql.feedback;

import com.nl2sql.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈闭环（REQ-515、D-20）。
 * 正负反馈均落库：positive→resolved，negative→pending；定时轮询 pending 队列打日志。
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final JdbcTemplate jdbcTemplate;

    public FeedbackService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long submit(String question, String generatedSql, String resultHash,
                       String feedbackType, String userComment, String userCorrectedSql) {
        UserContext.AuthUser user = UserContext.get();
        Long userId = user == null ? 0L : user.userId();
        String status = "positive".equals(feedbackType) ? "resolved" : "pending";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO feedback_queue (user_id, question, generated_sql, result_hash, " +
                            "feedback_type, user_comment, user_corrected_sql, status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, question);
            ps.setString(3, generatedSql);
            ps.setString(4, resultHash);
            ps.setString(5, feedbackType);
            ps.setString(6, userComment);
            ps.setString(7, userCorrectedSql);
            ps.setString(8, status);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    /** 每 5 分钟轮询 pending 队列（M3 仅打日志，人工分析留后续）。 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void pollPending() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT feedback_id, question, user_comment, user_corrected_sql FROM feedback_queue WHERE status = 'pending'");
        if (!rows.isEmpty()) {
            log.info("feedback pending queue size={}", rows.size());
            for (Map<String, Object> row : rows) {
                log.info("pending feedback id={}, question={}", row.get("feedback_id"), row.get("question"));
            }
        }
    }
}
