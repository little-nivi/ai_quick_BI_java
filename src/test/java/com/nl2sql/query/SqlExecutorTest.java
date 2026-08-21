package com.nl2sql.query;

import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;

import java.sql.SQLTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * SqlExecutor 执行与超时映射（REQ-503、NFR-4）。
 * 覆盖 TC-503-01。
 */
@ExtendWith(MockitoExtension.class)
class SqlExecutorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SqlExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SqlExecutor(jdbcTemplate);
    }

    @Test
    void dbTimeoutMapsTo5002() {
        // TC-503-01 验证 REQ-503 超时：SQLTimeoutException 在 cause 链 → 5002
        doThrow(new DataAccessResourceFailureException("timeout", new SQLTimeoutException()))
                .when(jdbcTemplate).execute(any(StatementCallback.class));

        BizException ex = catchThrowableOfType(
                () -> executor.execute("SELECT * FROM orders"), BizException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DB_TIMEOUT);
    }

    @Test
    void genericDbErrorMapsTo5000() {
        // 非超时的执行异常 → 兜底 5000
        doThrow(new DataAccessResourceFailureException("oops", new IllegalStateException("x")))
                .when(jdbcTemplate).execute(any(StatementCallback.class));

        BizException ex = catchThrowableOfType(
                () -> executor.execute("SELECT * FROM orders"), BizException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
