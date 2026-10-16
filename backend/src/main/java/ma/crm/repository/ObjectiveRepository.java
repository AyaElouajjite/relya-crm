package ma.crm.repository;

import ma.crm.domain.Objective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObjectiveRepository extends JpaRepository<Objective, Long> {

    List<Objective> findByYearAndMonth(int year, int month);

    List<Objective> findByYear(int year);

    Optional<Objective> findByUser_IdAndYearAndMonth(Long userId, int year, int month);
}
