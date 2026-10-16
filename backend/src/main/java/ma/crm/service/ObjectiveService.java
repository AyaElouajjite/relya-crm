package ma.crm.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Objective;
import ma.crm.domain.Role;
import ma.crm.domain.User;
import ma.crm.dto.UserDtos.UserSummary;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.ObjectiveRepository;
import ma.crm.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Objectifs mensuels de chiffre d'affaires fixés par le directeur commercial. */
@Service
@RequiredArgsConstructor
@Transactional
public class ObjectiveService {

    private final ObjectiveRepository objectives;
    private final UserRepository users;
    private final AuditService audit;

    /** Grille d'une année : une ligne par commercial, 12 montants (null = pas d'objectif). */
    public record ObjectiveRow(UserSummary user, List<BigDecimal> months) {
    }

    public record ObjectiveRequest(@NotNull Long userId, @Min(2000) @Max(2100) int year, @Min(1) @Max(12) int month,
                                   @NotNull @PositiveOrZero BigDecimal targetAmount) {
    }

    @Transactional(readOnly = true)
    public List<ObjectiveRow> year(int year) {
        Map<Long, BigDecimal[]> grid = new HashMap<>();
        for (Objective o : objectives.findByYear(year)) {
            grid.computeIfAbsent(o.getUser().getId(), k -> new BigDecimal[12])[o.getMonth() - 1] = o.getTargetAmount();
        }
        return users.findByActiveTrueAndRole_CodeIn(List.of(Role.COMMERCIAL, Role.DIRECTEUR)).stream()
                .sorted(Comparator.comparing(User::getLastName))
                .map(u -> new ObjectiveRow(UserSummary.of(u), Arrays.asList(grid.getOrDefault(u.getId(), new BigDecimal[12]))))
                .toList();
    }

    public void set(List<ObjectiveRequest> requests) {
        for (ObjectiveRequest r : requests) {
            User user = users.findById(r.userId()).orElseThrow(() -> NotFoundException.of("Utilisateur", r.userId()));
            Objective o = objectives.findByUser_IdAndYearAndMonth(r.userId(), r.year(), r.month()).orElseGet(() -> {
                Objective n = new Objective();
                n.setUser(user);
                n.setYear(r.year());
                n.setMonth(r.month());
                return n;
            });
            o.setTargetAmount(r.targetAmount());
            objectives.save(o);
        }
        audit.log(AuditService.UPDATE, "Objective", null, requests.size() + " objectif(s) mis à jour");
    }
}
