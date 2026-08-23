package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * SqlValidator AST 级白名单（REQ-509、D-15）。
 * 覆盖 TC-509-01（字符串字面量不误拦）、TC-509-02（DML 拦截）。
 */
class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator();

    @Test
    void deleteIsBlocked() {
        // TC-509-02 DML 拦截
        BizException ex = catchThrowableOfType(
                () -> validator.validate("DELETE FROM orders"), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
    }

    @Test
    void updateIsBlocked() {
        BizException ex = catchThrowableOfType(
                () -> validator.validate("UPDATE orders SET amount = 0"), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
    }

    @Test
    void insertIsBlocked() {
        BizException ex = catchThrowableOfType(
                () -> validator.validate("INSERT INTO orders VALUES (1)"), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
    }

    @Test
    void ddlIsBlocked() {
        BizException ex = catchThrowableOfType(
                () -> validator.validate("CREATE TABLE t (id INT)"), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SQL_BLOCKED);
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
    void stringLiteralWithDangerousWordIsNotBlocked() {
        // TC-509-01：字符串字面量含 'DELETE' 不误拦（AST 级优势）
        assertThatCode(() -> validator.validate(
                "SELECT * FROM orders WHERE status = 'DELETE 此订单'"))
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
