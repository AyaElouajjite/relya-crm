package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.ProductType;
import ma.crm.domain.Product;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.QuoteDtos.ProductDto;
import ma.crm.dto.QuoteDtos.ProductRequest;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static ma.crm.service.CompanyService.blankToNull;

/** Catalogue produits et services. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, ProductType type, Boolean active, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("reference")), like)));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return PageResponse.of(products.findAll(spec, PageRequest.of(page, size, Sort.by("name"))), ProductDto::of);
    }

    public ProductDto create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        products.save(p);
        audit.log(AuditService.CREATE, "Product", p.getId(), p.getName());
        return ProductDto.of(p);
    }

    public ProductDto update(Long id, ProductRequest req) {
        Product p = find(id);
        apply(p, req);
        audit.log(AuditService.UPDATE, "Product", id, p.getName());
        return ProductDto.of(p);
    }

    private void apply(Product p, ProductRequest req) {
        String ref = blankToNull(req.reference());
        if (ref != null) {
            products.findByReference(ref).filter(o -> !o.getId().equals(p.getId())).ifPresent(o -> {
                throw BusinessException.conflict("La référence " + ref + " est déjà utilisée");
            });
        }
        p.setName(req.name().trim());
        p.setReference(ref);
        p.setDescription(blankToNull(req.description()));
        p.setType(req.type() == null ? ProductType.PRODUIT : req.type());
        p.setPriceHT(req.priceHT());
        p.setVatRate(req.vatRate());
        p.setUnit(blankToNull(req.unit()));
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }

    Product find(Long id) {
        return products.findById(id).orElseThrow(() -> NotFoundException.of("Produit", id));
    }
}
