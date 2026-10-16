package ma.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compteur de numérotation (DEV-2026-0001, FAC-2026-0001, TCK-2026-0001), verrouillé à chaque tirage. */
@Entity
@Table(name = "document_sequences")
@Getter
@Setter
@NoArgsConstructor
public class DocumentSequence {

    /** Préfixe + année, ex. « DEV-2026 ». */
    @Id
    @Column(length = 20)
    private String name;

    @Column(nullable = false)
    private long lastValue;

    public DocumentSequence(String name) {
        this.name = name;
    }
}
