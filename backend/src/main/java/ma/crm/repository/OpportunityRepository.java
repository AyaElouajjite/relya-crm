package ma.crm.repository;

import ma.crm.domain.Enums.OpportunityStage;
import ma.crm.domain.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long>, JpaSpecificationExecutor<Opportunity> {

    List<Opportunity> findByCompany_IdOrderByCreatedAtDesc(Long companyId);

    List<Opportunity> findByStageNotIn(Collection<OpportunityStage> stages);

    /** Opportunités ouvertes sans activité depuis la date donnée (relances automatiques). */
    @Query("select o from Opportunity o where o.stage not in "
            + "(ma.crm.domain.Enums.OpportunityStage.GAGNEE, ma.crm.domain.Enums.OpportunityStage.PERDUE) "
            + "and coalesce(o.lastActivityAt, o.createdAt) < :before")
    List<Opportunity> findInactiveSince(Instant before);

    List<Opportunity> findByStageAndClosedAtBetween(OpportunityStage stage, Instant from, Instant to);
}
