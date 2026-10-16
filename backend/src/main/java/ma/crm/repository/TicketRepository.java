package ma.crm.repository;

import ma.crm.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByCompany_IdOrderByOpenedAtDesc(Long companyId);

    List<Ticket> findByResolvedAtBetween(Instant from, Instant to);
}
