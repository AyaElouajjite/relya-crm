package ma.crm.repository;

import ma.crm.domain.Enums.LeadStatus;
import ma.crm.domain.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByStatusAndConvertedAtBetween(LeadStatus status, Instant from, Instant to);

    /** Leads non convertis et inactifs : candidats à l'anonymisation (durée de conservation, loi 09-08). */
    @Query("select l from Lead l where l.status <> ma.crm.domain.Enums.LeadStatus.CONVERTI "
            + "and l.anonymized = false and l.updatedAt < :before")
    List<Lead> findStaleForRetention(Instant before);

    List<Lead> findByStatusIn(List<LeadStatus> statuses);
}
