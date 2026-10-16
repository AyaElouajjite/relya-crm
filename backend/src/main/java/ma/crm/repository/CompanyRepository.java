package ma.crm.repository;

import ma.crm.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {
    Optional<Company> findByIce(String ice);

    Optional<Company> findFirstByNameIgnoreCase(String name);

    @Query("select distinct c.sector from Company c where c.sector is not null order by c.sector")
    List<String> findDistinctSectors();

    @Query("select distinct c.city from Company c where c.city is not null order by c.city")
    List<String> findDistinctCities();
}
