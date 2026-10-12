package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.LitterSubstrateProfileRepository;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.SubstrateProfileCatalog;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Nối profile nền cát của colorchart vào cổng hiệu chỉnh màu do scan khai báo. */
@Component
public class SubstrateProfileCatalogAdapter implements SubstrateProfileCatalog {

    private final LitterSubstrateProfileRepository repository;

    public SubstrateProfileCatalogAdapter(LitterSubstrateProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Lab> findReferenceLab(String productLine, String productionBatch) {
        return repository.findActiveByProductLineAndBatch(ProductLine.valueOf(productLine), productionBatch)
                .map(profile -> new Lab(
                        profile.getLabL().doubleValue(),
                        profile.getLabA().doubleValue(),
                        profile.getLabB().doubleValue()));
    }
}
