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
 @Mock com.fnbx.cashclose.service.FundWithdrawalService fundWithdrawals;
 @Mock jakarta.persistence.Query positionQuery;
 @InjectMocks CashCloseServiceImpl service;
 UUID business=UUID.randomUUID(),staff=UUID.randomUUID(),branch=UUID.randomUUID();
 @BeforeEach void context(){TenantContext.set(TenantContext.of(business,staff));}
 @AfterEach void clear(){TenantContext.clear();}
 @Test void submissionRequiresPermissionBeforeAnyRead() {
  when(branchAccess.require(branch,Permission.CLOSE_SUBMIT)).thenThrow(new AccessDeniedException("denied"));
  assertThatThrownBy(()->service.submit(branch,new com.fnbx.cashclose.dto.request.SubmitCashCloseRequest())).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(closeRepository);
 }
 @Test void editingAuthorityDoesNotConferReview() {
  when(branchAccess.require(branch,Permission.CLOSE_REVIEW)).thenThrow(new AccessDeniedException("denied"));
  assertThatThrownBy(()->service.approve(branch,UUID.randomUUID(),"yes")).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(closeRepository,movementRepository);
 }
 @Test void reviewingAuthorityDoesNotConferCorrection() {
  when(branchAccess.require(branch,Permission.CLOSE_CORRECT)).thenThrow(new AccessDeniedException("denied"));
  var request=new com.fnbx.cashclose.dto.request.CorrectCashCloseRequest();
  request.setEditReason("wrong count");
  assertThatThrownBy(()->service.correctCashClose(branch,UUID.randomUUID(),request))
      .isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(closeRepository);
 }
 @Test void authorizedReviewerStillFollowsStateMachine() {
  var close=close(CloseStatus.VOIDED);
  when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
  assertThatThrownBy(()->service.approve(branch,close.getCashCloseId(),"yes")).isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
 }
 @Test void decisionsRecordTheCapabilityAndBranchPositionWhenAvailable() {
  var close=close(CloseStatus.SUBMITTED);
  when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
  when(entityManager.createNativeQuery(anyString())).thenReturn(positionQuery);
  when(positionQuery.setParameter(anyString(), any())).thenReturn(positionQuery);
  when(positionQuery.getResultList()).thenReturn(List.of("STORE_MANAGER"));
  service.approve(branch,close.getCashCloseId(),"yes");
  var capture=ArgumentCaptor.forClass(CashCloseDecision.class);
  verify(closeDecisionRepository).save(capture.capture());
  assertThat(capture.getValue().getActedPermission()).isEqualTo("CLOSE_REVIEW");
  assertThat(capture.getValue().getActedPosition()).isEqualTo("STORE_MANAGER");
  assertThat(capture.getValue().getActedBy()).isEqualTo(staff);
 }
 private CashClose close(CloseStatus status){var c=new CashClose();c.setCashCloseId(UUID.randomUUID());c.setBranchId(branch);c.setBusinessId(business);c.setStatus(status);return c;}
}
