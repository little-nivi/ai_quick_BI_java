package com.nl2sql.governance;

import com.nl2sql.auth.UserContext;
import com.nl2sql.common.BizException;
import com.nl2sql.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指标审批流（REQ-524、SR-9）。
 * 覆盖 TC-408-01（审批）、TC-409-01（废弃）、TC-407-02（越权）。
 */
@ExtendWith(MockitoExtension.class)
class MetricGovernanceServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MetricGovernanceService service;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void approveDraftToPublished() {
        // TC-408-01
        UserContext.set(new UserContext.AuthUser(1L, "admin", "all"));
        service = new MetricGovernanceService(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.approve(21L);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void deprecatePublishedToDeprecated() {
        // TC-409-01
        UserContext.set(new UserContext.AuthUser(1L, "admin", "all"));
        service = new MetricGovernanceService(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        service.deprecate(21L);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void nonAdminCannotApprove() {
        // TC-407-02 越权 → 4004
        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东"));
        service = new MetricGovernanceService(jdbcTemplate);

        BizException ex = catchThrowableOfType(() -> service.approve(21L), BizException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void nonAdminCannotDeprecate() {
        UserContext.set(new UserContext.AuthUser(2L, "operator", "华东"));
        service = new MetricGovernanceService(jdbcTemplate);

        BizException ex = catchThrowableOfType(() -> service.deprecate(21L), BizException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}
