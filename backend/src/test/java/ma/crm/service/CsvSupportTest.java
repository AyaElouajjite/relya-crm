package ma.crm.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvSupportTest {

    @Test
    void formulasAreNeutralizedButPhoneNumbersAreKept() {
        assertThat(CsvSupport.sanitize("=HYPERLINK(\"http://x\")")).isEqualTo("'=HYPERLINK(\"http://x\")");
        assertThat(CsvSupport.sanitize("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvSupport.sanitize("+cmd|'/c calc'!A0")).isEqualTo("'+cmd|'/c calc'!A0");
        assertThat(CsvSupport.sanitize("+212 6 61 23 45 67")).isEqualTo("+212 6 61 23 45 67");
        assertThat(CsvSupport.sanitize("-12.5")).isEqualTo("-12.5");
        assertThat(CsvSupport.sanitize("Atlas")).isEqualTo("Atlas");
    }

    @Test
    void tagsRoundTrip() {
        assertThat(CsvSupport.splitTags(" vip | export ||")).containsExactlyInAnyOrder("vip", "export");
        assertThat(CsvSupport.joinTags(CsvSupport.splitTags("b|a"))).isEqualTo("a|b");
    }
}
