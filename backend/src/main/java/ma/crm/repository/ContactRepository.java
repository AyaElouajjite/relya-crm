package ma.crm.repository;

import ma.crm.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {
    List<Contact> findByCompany_IdAndArchivedFalseOrderByLastNameAsc(Long companyId);

    @Query("select c.company.id, count(c) from Contact c where c.archived = false and c.company.id in :ids group by c.company.id")
    List<Object[]> countActiveByCompanyIds(Collection<Long> ids);
}
