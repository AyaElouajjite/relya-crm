package ma.crm.repository;

import ma.crm.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipient_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByRecipient_IdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipient.id = :userId and n.readAt is null")
    int markAllRead(Long userId, Instant now);
}
