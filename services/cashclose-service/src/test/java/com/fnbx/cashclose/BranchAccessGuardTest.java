package com.fnbx.cashclose;
import com.fnbx.shared.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class BranchAccessGuardTest {
 @Test void scopesCannotBeSubstituted() {
  JdbcTemplate jdbc=mock(JdbcTemplate.class);
  BranchAccessGuard guard=new BranchAccessGuard(jdbc);
  assertThatThrownBy(()->guard.require(UUID.randomUUID(),Permission.PERMISSION_GRANT)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->guard.requireBusiness(Permission.CLOSE_REVIEW)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->guard.require(null,Permission.CLOSE_READ)).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(jdbc);
 }
}
