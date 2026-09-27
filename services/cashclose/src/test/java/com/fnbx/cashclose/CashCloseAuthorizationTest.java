package com.fnbx.cashclose;
import com.fnbx.cashclose.entity.*;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.repository.*;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.shared.security.*;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.exception.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CashCloseAuthorizationTest {
 @Mock CashCloseRepository closeRepository;
 @Mock CashMovementRepository movementRepository;
 @Mock CashCloseDecisionRepository closeDecisionRepository;
 @Mock CashCloseCalcRepository calcRepository;
 @Mock com.fnbx.cashclose.mapper.CashCloseMapper mapper;
 @Mock BranchAccessGuard branchAccess;
 @Mock EntityManager entityManager;
 @InjectMocks CashCloseServiceImpl service;
 UUID business=UUID.randomUUID(),staff=UUID.randomUUID(),branch=UUID.randomUUID();
 @BeforeEach void context(){TenantContext.set(TenantContext.of(business,staff));}
 @AfterEach void clear(){TenantContext.clear();}
 @Test void openingRequiresOpenPermissionBeforeAnyRead() {
  when(branchAccess.require(branch,Permission.CLOSE_OPEN)).thenThrow(new AccessDeniedException("denied"));
  assertThatThrownBy(()->service.openDraft(branch,new com.fnbx.cashclose.dto.request.OpenDraftRequest())).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(closeRepository);
 }
 @Test void editingAuthorityDoesNotConferReview() {
  when(branchAccess.require(branch,Permission.CLOSE_REVIEW)).thenThrow(new AccessDeniedException("denied"));
  assertThatThrownBy(()->service.approve(branch,UUID.randomUUID(),"yes")).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(closeRepository,movementRepository);
 }
 @Test void authorizedReviewerStillFollowsStateMachine() {
  var close=close(CloseStatus.DRAFT);
  when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
  assertThatThrownBy(()->service.approve(branch,close.getCashCloseId(),"yes")).isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
 }
 @Test void decisionsRecordTheCapabilityNotAPositionOrRole() {
  var close=close(CloseStatus.SUBMITTED);
  when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
  service.approve(branch,close.getCashCloseId(),"yes");
  var capture=ArgumentCaptor.forClass(CashCloseDecision.class);
  verify(closeDecisionRepository).save(capture.capture());
  assertThat(capture.getValue().getActedPermission()).isEqualTo("CLOSE_REVIEW");
  assertThat(capture.getValue().getActedRole()).isNull();
  assertThat(capture.getValue().getActedBy()).isEqualTo(staff);
 }
 private CashClose close(CloseStatus status){var c=new CashClose();c.setCashCloseId(UUID.randomUUID());c.setBranchId(branch);c.setBusinessId(business);c.setStatus(status);return c;}
}
