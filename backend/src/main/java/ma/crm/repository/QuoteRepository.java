package ma.crm.repository;

import ma.crm.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    List<Quote> findByCompany_IdOrderByDateDesc(Long companyId);

    List<Quote> findByOpportunity_IdOrderByDateDesc(Long opportunityId);

    Optional<Quote> findByPublicToken(String token);
}
