package ma.crm.repository;

import ma.crm.domain.Enums.InvoiceStatus;
import ma.crm.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByCompany_IdOrderByIssueDateDesc(Long companyId);

    Optional<Invoice> findByQuote_Id(Long quoteId);

    List<Invoice> findByStatusNotAndIssueDateBetween(InvoiceStatus status, LocalDate from, LocalDate to);
}
