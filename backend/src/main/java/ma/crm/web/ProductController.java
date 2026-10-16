package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.ProductType;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.QuoteDtos.ProductDto;
import ma.crm.dto.QuoteDtos.ProductRequest;
import ma.crm.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Catalogue")
public class ProductController {

    private final ProductService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('QUOTE_MANAGE', 'PRODUCT_MANAGE')")
    public PageResponse<ProductDto> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) ProductType type,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.search(q, type, active, page, Math.min(size, 200));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    public ProductDto create(@Valid @RequestBody ProductRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return service.update(id, req);
    }
}
