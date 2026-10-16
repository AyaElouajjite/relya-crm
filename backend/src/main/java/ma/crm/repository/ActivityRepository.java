package ma.crm.repository;

import ma.crm.domain.Activity;
import ma.crm.domain.Enums.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    @Query("select a from Activity a where a.reminderSent = false and a.reminderAt is not null "
            + "and a.reminderAt <= :now and a.status = ma.crm.domain.Enums.ActivityStatus.A_FAIRE")
    List<Activity> findDueReminders(Instant now);

    /** Évite de recréer une relance automatique tant que la précédente n'est pas traitée. */
    boolean existsByOpportunity_IdAndAutomaticTrueAndStatusAndSubjectStartingWith(
            Long opportunityId, ActivityStatus status, String subjectPrefix);

    long countByLead_Id(Long leadId);
}
