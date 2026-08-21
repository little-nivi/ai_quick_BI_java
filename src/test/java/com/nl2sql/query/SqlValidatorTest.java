package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * SqlValidator 白名单校验（REQ-502、SR-1）。
 * 覆盖 TC-502-01、TC-502-02。
 */
class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @Test
    void deleteIsBlocked() {
        // TC-502-01 验证 REQ-502 高危拦截
        BizException ex = catchThrowableOfType(
                () -> validator.validate("DELETE FROM orders"), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
    }

    @Test
    void updateIsBlocked() {
        // TC-502-02 验证 REQ-502 非 SELECT 拦截
        BizException ex = catchThrowableOfType(
                () -> validator.validate("UPDATE orders SET amount = 0"), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
    }

    @Test
    void dropAlterTruncateInsertCreateAreBlocked() {
        for (String sql : new String[]{
                "DROP TABLE orders",
                "ALTER TABLE orders ADD COLUMN x INT",
                "TRUNCATE TABLE orders",
                "INSERT INTO orders VALUES (1)",
                "CREATE TABLE t (id INT)"}) {
            BizException ex = catchThrowableOfType(() -> validator.validate(sql), BizException.class);
            assertThat(ex).as("should block: %s", sql).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
        }
    }

    @Test
    void nullOrBlankIsBlocked() {
        for (String sql : new String[]{null, "", "   "}) {
            BizException ex = catchThrowableOfType(() -> validator.validate(sql), BizException.class);
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
        }
    }

    @Test
    void validSelectPasses() {
        assertThatCode(() -> validator.validate("SELECT SUM(amount) FROM orders"))
                .doesNotThrowAnyException();
    }

    @Test
    void syntacticallyInvalidSelectIsInternalError() {
        BizException ex = catchThrowableOfType(
                () -> validator.validate("SELECT FROM WHERE"), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
