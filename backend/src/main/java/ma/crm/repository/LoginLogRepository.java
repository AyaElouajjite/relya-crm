package ma.crm.repository;

import ma.crm.domain.LoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    long countByEmailIgnoreCaseAndSuccessFalseAndAtAfter(String email, Instant since);

    Page<LoginLog> findByEmailIgnoreCaseOrderByAtDesc(String email, Pageable pageable);

    Page<LoginLog> findAllByOrderByAtDesc(Pageable pageable);
}
