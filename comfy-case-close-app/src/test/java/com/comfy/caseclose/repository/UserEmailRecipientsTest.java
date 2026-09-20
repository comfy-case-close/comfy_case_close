package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.StaffPositionEntity;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.entity.UserBranch;
import com.comfy.caseclose.entity.UserPosition;
import com.comfy.caseclose.utils.enums.StaffPosition;
import com.comfy.caseclose.utils.enums.UserRole;
import org.hibernate.cfg.Configuration;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Executes the actual JPQL against isolated H2 data; no application database is touched. */
class UserEmailRecipientsTest {
    @Test
    void selectsOnlyActiveUsersWithRequestedRolesAndEmailsAssignedToTheRequestedBranch() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(User.class).addAnnotatedClass(Branch.class).addAnnotatedClass(UserBranch.class)
                .addAnnotatedClass(StaffPositionEntity.class).addAnnotatedClass(UserPosition.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:mail-recipients")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            for (long id = 1; id <= 2; id++) {
                session.createNativeMutationQuery("""
                        INSERT INTO branches (id, branch_code, branch_name, is_active, created_at, updated_at)
                        VALUES (:id, :code, :code, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """).setParameter("id", id).setParameter("code", id == 1 ? "TX" : "OTHER").executeUpdate();
            }
            staffPosition(session, StaffPosition.SHIFT_LEADER);
            staffPosition(session, StaffPosition.CASHIER);
            staffPosition(session, StaffPosition.STORE_MANAGER);

            user(session, 1, "STAFF", "first@example.com", true, 1);
            userPosition(session, 1, StaffPosition.SHIFT_LEADER);
            user(session, 2, "STAFF", " second@example.com ", true, 1);
            userPosition(session, 2, StaffPosition.CASHIER);
            user(session, 3, "STAFF", "other@example.com", true, 2);
            userPosition(session, 3, StaffPosition.SHIFT_LEADER);
            user(session, 4, "MANAGER", "manager@example.com", true, 1);
            userPosition(session, 4, StaffPosition.STORE_MANAGER);
            user(session, 8, "ADMIN", "admin@example.com", true, 1);
            user(session, 5, "STAFF", "inactive@example.com", false, 1);
            userPosition(session, 5, StaffPosition.STORE_MANAGER);
            user(session, 6, "STAFF", null, true, 1);
            userPosition(session, 6, StaffPosition.STORE_MANAGER);
            user(session, 7, "STAFF", "   ", true, 1);
            userPosition(session, 7, StaffPosition.STORE_MANAGER);
            session.createNativeMutationQuery("INSERT INTO user_branches (user_id, branch_id) VALUES (1, 2)")
                    .executeUpdate();
            transaction.commit();

            UserRepository repository = new JpaRepositoryFactory(session).getRepository(UserRepository.class);
            assertThat(repository.findActiveEmailsByBranchIdAndRoles(1L, List.of(UserRole.STAFF)))
                    .containsExactlyInAnyOrder("first@example.com", "second@example.com");
            assertThat(repository.findActiveEmailsByBranchIdAndRoles(1L, List.of(UserRole.MANAGER, UserRole.ADMIN)))
                    .containsExactlyInAnyOrder("manager@example.com", "admin@example.com");
            assertThat(repository.findActiveEmailsByBranchIdAndRoles(2L, List.of(UserRole.STAFF)))
                    .containsExactlyInAnyOrder("first@example.com", "other@example.com");
            assertThat(repository.findActiveEmailsByBranchIdAndPositions(
                    1L, List.of(StaffPosition.STORE_MANAGER)))
                    .containsExactly("manager@example.com");
            assertThat(repository.findActiveEmailsByBranchIdAndRoles(99L, List.of(UserRole.STAFF))).isEmpty();
        }
    }

    private void staffPosition(Session session, StaffPosition position) {
        session.createNativeMutationQuery("""
                INSERT INTO staff_positions (code, display_title) VALUES (:code, :title)
                """).setParameter("code", position.name())
                .setParameter("title", position.getDisplayTitle()).executeUpdate();
    }

    private void user(Session session, long id, String role, String email, boolean active, long branchId) {
        session.createNativeMutationQuery("""
                INSERT INTO users (id, employee_code, full_name, passcode_hash, role, email,
                                   is_active, created_at, updated_at)
                VALUES (:id, :code, 'Test user', 'unused', :role, :email, :active,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter("id", id).setParameter("code", "EMP-" + id)
                .setParameter("role", role).setParameter("email", email)
                .setParameter("active", active).executeUpdate();
        session.createNativeMutationQuery("INSERT INTO user_branches (user_id, branch_id) VALUES (:user, :branch)")
                .setParameter("user", id).setParameter("branch", branchId).executeUpdate();
    }

    private void userPosition(Session session, long userId, StaffPosition position) {
        session.createNativeMutationQuery("""
                INSERT INTO user_positions (user_id, staff_position_code) VALUES (:user, :position)
                """).setParameter("user", userId)
                .setParameter("position", position.name()).executeUpdate();
    }
}
