package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.*;
import ma.crm.exception.BusinessException;
import ma.crm.repository.*;
import ma.crm.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Module 8 : indicateurs de performance. Le chiffre d'affaires réalisé correspond aux factures émises
 * (hors annulées) en HT ; le prévisionnel au montant pondéré (montant × probabilité) des opportunités ouvertes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final InvoiceRepository invoices;
    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
    private final QuoteRepository quotes;
    private final TicketRepository tickets;
    private final ActivityRepository activities;
    private final ObjectiveRepository objectives;
    private final UserRepository users;

    public record Kpis(BigDecimal revenue, BigDecimal objective, Double objectiveRate, BigDecimal forecast,
                       BigDecimal pipelineTotal, long openOpportunities, long won, long lost, Double winRate,
                       long leadsCreated, long leadsConverted, Double leadConversionRate, long quotesSent,
                       Double quoteAcceptanceRate, long openTickets, long overdueTickets, Double avgResolutionHours,
                       long activitiesDone, long overdueActivities) {
    }

    public record MonthPoint(int month, BigDecimal revenue, BigDecimal objective) {
    }

    public record SellerPerformance(Long userId, String name, BigDecimal revenue, BigDecimal objective,
                                    Double objectiveRate, BigDecimal forecast, long won, long lost, Double winRate,
                                    long leadsConverted, Double leadConversionRate, long activitiesDone) {
    }

    public record Slice(String key, long count, BigDecimal amount) {
    }

    public record Dashboard(LocalDate from, LocalDate to, Long ownerId, Kpis kpis, List<MonthPoint> monthly,
                            List<SellerPerformance> sellers, List<Slice> pipelineByStage, List<Slice> lossReasons,
                            List<Slice> leadSources, List<Slice> ticketsByStatus, List<Slice> ticketsByPriority) {
    }

    public Dashboard build(LocalDate from, LocalDate to, Long ownerId) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : LocalDate.now();
        if (end.isBefore(start)) {
            throw new BusinessException("La date de fin précède la date de début");
        }
        // Un commercial sans DASHBOARD_ALL ne voit que ses propres chiffres.
        Long scope = CurrentUser.has(Permission.DASHBOARD_ALL) ? ownerId : CurrentUser.id();
        if (!CurrentUser.has(Permission.DASHBOARD_ALL) && !CurrentUser.has(Permission.DASHBOARD_VIEW)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        Instant fromI = start.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toI = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<Invoice> allInvoices = invoices.findAll().stream()
                .filter(i -> i.getStatus() != InvoiceStatus.ANNULEE).toList();
        List<Opportunity> allOpps = opportunities.findAll();
        List<Lead> allLeads = leads.findAll();
        List<Quote> allQuotes = quotes.findAll();
        List<Ticket> allTickets = tickets.findAll();
        List<Activity> allActivities = activities.findAll();

        Predicate<User> inScope = u -> scope == null || (u != null && u.getId().equals(scope));
        Predicate<LocalDate> inPeriod = d -> d != null && !d.isBefore(start) && !d.isAfter(end);
        Predicate<Instant> inPeriodI = i -> i != null && !i.isBefore(fromI) && i.isBefore(toI);

        List<Invoice> periodInvoices = allInvoices.stream()
                .filter(i -> inPeriod.test(i.getIssueDate()) && inScope.test(i.getQuote().getOwner())).toList();
        BigDecimal revenue = sum(periodInvoices.stream().map(Invoice::getTotalHT));

        List<Opportunity> scopedOpps = allOpps.stream().filter(o -> inScope.test(o.getOwner())).toList();
        List<Opportunity> open = scopedOpps.stream().filter(o -> !o.getStage().isClosed()).toList();
        BigDecimal forecast = sum(open.stream().map(DashboardService::weighted));
        BigDecimal pipelineTotal = sum(open.stream().map(Opportunity::getAmount));
        long won = scopedOpps.stream().filter(o -> o.getStage() == OpportunityStage.GAGNEE && inPeriodI.test(o.getClosedAt())).count();
        long lost = scopedOpps.stream().filter(o -> o.getStage() == OpportunityStage.PERDUE && inPeriodI.test(o.getClosedAt())).count();

        List<Lead> scopedLeads = allLeads.stream().filter(l -> inScope.test(l.getAssignedTo())).toList();
        long leadsCreated = scopedLeads.stream().filter(l -> inPeriodI.test(l.getCreatedAt())).count();
        long leadsConverted = scopedLeads.stream().filter(l -> l.getStatus() == LeadStatus.CONVERTI && inPeriodI.test(l.getConvertedAt())).count();

        List<Quote> scopedQuotes = allQuotes.stream().filter(q -> inScope.test(q.getOwner()) && inPeriodI.test(q.getSentAt())).toList();
        long quotesSent = scopedQuotes.size();
        long quotesAccepted = scopedQuotes.stream().filter(q -> q.getStatus() == QuoteStatus.ACCEPTE).count();
        long quotesAnswered = scopedQuotes.stream().filter(q -> q.getStatus() == QuoteStatus.ACCEPTE || q.getStatus() == QuoteStatus.REFUSE).count();

        // Les indicateurs SAV ne dépendent pas du commercial sélectionné.
        long openTickets = allTickets.stream().filter(t -> t.getStatus().isOpen()).count();
        long overdueTickets = allTickets.stream().filter(t -> t.getStatus().isOpen() && t.getDueAt().isBefore(Instant.now())).count();
        OptionalDouble avgRes = allTickets.stream().filter(t -> inPeriodI.test(t.getResolvedAt()))
                .mapToLong(t -> Duration.between(t.getOpenedAt(), t.getResolvedAt()).toMinutes()).average();

        List<Activity> scopedActivities = allActivities.stream()
                .filter(a -> a.getType() != ActivityType.SYSTEME && inScope.test(a.getOwner())).toList();
        long activitiesDone = scopedActivities.stream().filter(a -> a.getStatus() == ActivityStatus.TERMINEE && inPeriodI.test(a.getCompletedAt())).count();
        long overdueActivities = scopedActivities.stream().filter(a -> a.getStatus() == ActivityStatus.A_FAIRE && a.getDueAt().isBefore(Instant.now())).count();

        BigDecimal objective = objectiveFor(scope, start, end);

        Kpis kpis = new Kpis(revenue, objective, rate(revenue, objective), forecast, pipelineTotal, open.size(), won, lost,
                ratio(won, won + lost), leadsCreated, leadsConverted, ratio(leadsConverted, leadsCreated), quotesSent,
                ratio(quotesAccepted, quotesAnswered), openTickets, overdueTickets,
                avgRes.isPresent() ? Math.round(avgRes.getAsDouble() / 6.0) / 10.0 : null, activitiesDone, overdueActivities);

        // Série mensuelle de l'année de fin de période.
        int year = end.getYear();
        List<MonthPoint> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            int month = m;
            BigDecimal rev = sum(allInvoices.stream().filter(i -> i.getIssueDate().getYear() == year
                    && i.getIssueDate().getMonthValue() == month && inScope.test(i.getQuote().getOwner())).map(Invoice::getTotalHT));
            BigDecimal obj = objectiveFor(scope, LocalDate.of(year, m, 1), LocalDate.of(year, m, 1).withDayOfMonth(LocalDate.of(year, m, 1).lengthOfMonth()));
            monthly.add(new MonthPoint(m, rev, obj));
        }

        List<SellerPerformance> sellers = new ArrayList<>();
        if (CurrentUser.has(Permission.DASHBOARD_ALL)) {
            for (User u : users.findByActiveTrueAndRole_CodeIn(List.of(Role.COMMERCIAL, Role.DIRECTEUR))) {
                if (scope != null && !scope.equals(u.getId())) {
                    continue;
                }
                Predicate<User> mine = x -> x != null && x.getId().equals(u.getId());
                BigDecimal rev = sum(allInvoices.stream().filter(i -> inPeriod.test(i.getIssueDate()) && mine.test(i.getQuote().getOwner())).map(Invoice::getTotalHT));
                BigDecimal obj = objectiveFor(u.getId(), start, end);
                long w = allOpps.stream().filter(o -> mine.test(o.getOwner()) && o.getStage() == OpportunityStage.GAGNEE && inPeriodI.test(o.getClosedAt())).count();
                long l = allOpps.stream().filter(o -> mine.test(o.getOwner()) && o.getStage() == OpportunityStage.PERDUE && inPeriodI.test(o.getClosedAt())).count();
                long lc = allLeads.stream().filter(x -> mine.test(x.getAssignedTo()) && x.getStatus() == LeadStatus.CONVERTI && inPeriodI.test(x.getConvertedAt())).count();
                long lt = allLeads.stream().filter(x -> mine.test(x.getAssignedTo()) && inPeriodI.test(x.getCreatedAt())).count();
                BigDecimal fc = sum(allOpps.stream().filter(o -> mine.test(o.getOwner()) && !o.getStage().isClosed()).map(DashboardService::weighted));
                long ad = allActivities.stream().filter(a -> mine.test(a.getOwner()) && a.getType() != ActivityType.SYSTEME
                        && a.getStatus() == ActivityStatus.TERMINEE && inPeriodI.test(a.getCompletedAt())).count();
                sellers.add(new SellerPerformance(u.getId(), u.getFullName(), rev, obj, rate(rev, obj), fc, w, l,
                        ratio(w, w + l), lc, ratio(lc, lt), ad));
            }
            sellers.sort(Comparator.comparing(SellerPerformance::revenue).reversed());
        }

        List<Slice> byStage = Arrays.stream(OpportunityStage.values()).map(s -> {
            List<Opportunity> list = scopedOpps.stream().filter(o -> o.getStage() == s
                    && (!s.isClosed() || inPeriodI.test(o.getClosedAt()))).toList();
            return new Slice(s.name(), list.size(), sum(list.stream().map(Opportunity::getAmount)));
        }).toList();

        List<Slice> lossReasons = scopedOpps.stream()
                .filter(o -> o.getStage() == OpportunityStage.PERDUE && o.getLossReason() != null && inPeriodI.test(o.getClosedAt()))
                .collect(Collectors.groupingBy(o -> o.getLossReason().name(), TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> new Slice(e.getKey(), e.getValue().size(), sum(e.getValue().stream().map(Opportunity::getAmount))))
                .sorted(Comparator.comparingLong(Slice::count).reversed()).toList();

        List<Slice> leadSources = scopedLeads.stream().filter(l -> inPeriodI.test(l.getCreatedAt()))
                .collect(Collectors.groupingBy(l -> l.getSource().name(), TreeMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new Slice(e.getKey(), e.getValue(), null))
                .sorted(Comparator.comparingLong(Slice::count).reversed()).toList();

        List<Slice> ticketsByStatus = Arrays.stream(TicketStatus.values())
                .map(s -> new Slice(s.name(), allTickets.stream().filter(t -> t.getStatus() == s).count(), null)).toList();
        List<Slice> ticketsByPriority = Arrays.stream(TicketPriority.values())
                .map(p -> new Slice(p.name(), allTickets.stream().filter(t -> t.getStatus().isOpen() && t.getPriority() == p).count(), null)).toList();

        return new Dashboard(start, end, scope, kpis, monthly, sellers, byStage, lossReasons, leadSources,
                ticketsByStatus, ticketsByPriority);
    }

    /** Somme des objectifs mensuels couvrant la période (tous les commerciaux si scope est nul). */
    private BigDecimal objectiveFor(Long userId, LocalDate start, LocalDate end) {
        BigDecimal total = BigDecimal.ZERO;
        YearMonth ym = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!ym.isAfter(last)) {
            for (Objective o : objectives.findByYearAndMonth(ym.getYear(), ym.getMonthValue())) {
                if (userId == null || o.getUser().getId().equals(userId)) {
                    total = total.add(o.getTargetAmount());
                }
            }
            ym = ym.plusMonths(1);
        }
        return total;
    }

    static BigDecimal weighted(Opportunity o) {
        return o.getAmount().multiply(BigDecimal.valueOf(o.getProbability())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double ratio(long part, long total) {
        return total == 0 ? null : Math.round(1000.0 * part / total) / 10.0;
    }

    private static Double rate(BigDecimal value, BigDecimal target) {
        return target.signum() == 0 ? null
                : value.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP).doubleValue();
    }
}
