package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.DocumentSequence;
import ma.crm.repository.DocumentSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/** Numérotation continue par année : DEV-2026-0001, FAC-2026-0001, TCK-2026-0001. */
@Service
@RequiredArgsConstructor
public class SequenceService {

    private final DocumentSequenceRepository repository;

    @Transactional
    public String next(String prefix) {
        String name = prefix + "-" + Year.now().getValue();
        DocumentSequence seq = repository.lockByName(name).orElseGet(() -> repository.saveAndFlush(new DocumentSequence(name)));
        seq.setLastValue(seq.getLastValue() + 1);
        return name + "-" + String.format("%04d", seq.getLastValue());
    }
}
