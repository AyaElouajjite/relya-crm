package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.Notification;
import ma.crm.domain.User;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.repository.NotificationRepository;
import ma.crm.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Notifications in-app, poussées en temps réel par WebSocket et, si demandé, doublées d'un e-mail. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public record NotificationDto(Long id, String type, String title, String message, String link,
                                  Instant readAt, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                    n.getReadAt(), n.getCreatedAt());
        }
    }

    private final NotificationRepository repository;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;
    private final MailService mail;

    @Transactional
    public void notify(User recipient, String type, String title, String message, String link, boolean alsoEmail) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        repository.save(n);
        NotificationDto dto = NotificationDto.of(n);
        String email = recipient.getEmail();
        // Envoi après validation de la transaction, pour ne jamais pousser une notification annulée.
        afterCommit(() -> {
            messaging.convertAndSendToUser(email, "/queue/notifications", dto);
            if (alsoEmail) {
                mail.send(email, title, (message == null ? "" : message) + "\n\n— Votre CRM");
            }
        });
    }

    @Transactional
    public void notifyAll(Collection<User> recipients, String type, String title, String message, String link) {
        recipients.forEach(u -> notify(u, type, title, message, link, false));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, int page, int size) {
        return PageResponse.of(repository.findByRecipient_IdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)),
                NotificationDto::of);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> unread(Long userId) {
        return Map.of("count", repository.countByRecipient_IdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        repository.findById(id).filter(n -> n.getRecipient().getId().equals(userId))
                .ifPresent(n -> n.setReadAt(Instant.now()));
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public User reference(Long userId) {
        return users.getReferenceById(userId);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
