package com.rota.documents.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentService.slugify: ASCII-safe slugs, correct Turkish folding")
class SlugifyTest {

    @Test
    @DisplayName("basic ASCII names slugify as expected")
    void asciiSlugs() {
        assertThat(DocumentService.slugify("Payment API")).isEqualTo("payment-api");
        assertThat(DocumentService.slugify("  Hello   World  ")).isEqualTo("hello-world");
        assertThat(DocumentService.slugify("v2.0/users")).isEqualTo("v2-0-users");
    }

    @Test
    @DisplayName("Turkish letters fold to ASCII instead of being dropped")
    void turkishFolding() {
        // The regression: "ı" used to be dropped (→ "ack") because NFKD leaves it intact.
        assertThat(DocumentService.slugify("Açık API")).isEqualTo("acik-api");
        assertThat(DocumentService.slugify("Ödeme Şağ Çığ Ü İş"))
                .isEqualTo("odeme-sag-cig-u-is");
        assertThat(DocumentService.slugify("Gümüş Ağ")).isEqualTo("gumus-ag");
    }

    @Test
    @DisplayName("blank / symbol-only input falls back to a safe default")
    void fallback() {
        assertThat(DocumentService.slugify("!!!")).isEqualTo("doc");
        assertThat(DocumentService.slugify("   ")).isEqualTo("doc");
    }
}
