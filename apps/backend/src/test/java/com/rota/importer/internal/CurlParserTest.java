package com.rota.importer.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.rota.endpoints.api.ImportModel.ImportEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurlParser: parses the common curl shapes (acceptance: 10 examples)")
class CurlParserTest {

    private final CurlParser parser = new CurlParser(new ObjectMapper());

    private ImportEndpoint parseOne(String curl) {
        var parsed = parser.parse(curl);
        assertThat(parsed.endpoints()).hasSize(1);
        return parsed.endpoints().get(0);
    }

    @Test
    @DisplayName("10 representative curl commands parse into the expected endpoint")
    void tenExamples() {
        // 1. Bare GET.
        ImportEndpoint e1 = parseOne("curl https://api.example.com/users");
        assertThat(e1.method()).isEqualTo("GET");
        assertThat(e1.path()).isEqualTo("/users");

        // 2. Implicit POST via -d.
        ImportEndpoint e2 = parseOne("curl https://api.example.com/users -d '{\"name\":\"Ada\"}'");
        assertThat(e2.method()).isEqualTo("POST");
        assertThat(e2.requestBodies()).hasSize(1);
        assertThat(e2.requestBodies().get(0).exampleJson()).containsEntry("name", "Ada");

        // 3. Explicit -X with header.
        ImportEndpoint e3 = parseOne("curl -X DELETE https://api.example.com/users/42 -H 'Authorization: Bearer t'");
        assertThat(e3.method()).isEqualTo("DELETE");
        assertThat(e3.path()).isEqualTo("/users/42");
        assertThat(e3.authType()).isEqualTo("bearer");

        // 4. Query parameters.
        ImportEndpoint e4 = parseOne("curl 'https://api.example.com/search?q=cats&limit=10'");
        assertThat(e4.path()).isEqualTo("/search");
        assertThat(e4.parameters()).extracting(p -> p.name()).contains("q", "limit");

        // 5. --request long form + --header.
        ImportEndpoint e5 = parseOne("curl --request PUT --header 'Content-Type: application/json' "
                + "--data '{\"a\":1}' https://api.example.com/items/1");
        assertThat(e5.method()).isEqualTo("PUT");
        assertThat(e5.requestBodies().get(0).contentType()).isEqualTo("application/json");

        // 6. Line continuations.
        ImportEndpoint e6 = parseOne("curl -X POST \\\n  https://api.example.com/login \\\n  -d 'user=a'");
        assertThat(e6.method()).isEqualTo("POST");
        assertThat(e6.path()).isEqualTo("/login");

        // 7. Basic auth (-u).
        ImportEndpoint e7 = parseOne("curl -u admin:secret https://api.example.com/admin");
        assertThat(e7.authType()).isEqualTo("basic");

        // 8. Double-quoted JSON body with spaces.
        ImportEndpoint e8 = parseOne("curl -X POST https://api.example.com/orders "
                + "-H \"Content-Type: application/json\" -d \"{\\\"q\\\": 2}\"");
        assertThat(e8.method()).isEqualTo("POST");
        assertThat(e8.requestBodies().get(0).exampleJson()).containsEntry("q", 2);

        // 9. Ignored flags (-s, -i, --compressed) don't break parsing.
        ImportEndpoint e9 = parseOne("curl -s -i --compressed https://api.example.com/health");
        assertThat(e9.method()).isEqualTo("GET");
        assertThat(e9.path()).isEqualTo("/health");

        // 10. Path with template-ish segment + multiple headers.
        ImportEndpoint e10 = parseOne("curl https://api.example.com/v1/orders/123/items "
                + "-H 'Accept: application/json' -H 'X-Trace: abc'");
        assertThat(e10.path()).isEqualTo("/v1/orders/123/items");
        assertThat(e10.parameters()).extracting(p -> p.name()).contains("Accept", "X-Trace");
        assertThat(e10.parameters()).allSatisfy(p -> {
            if (p.name().equals("Accept")) {
                assertThat(p.location()).isEqualTo("header");
            }
        });
    }

    @Test
    @DisplayName("non-curl / urlless input is rejected")
    void rejectsBadInput() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse("wget https://x.com"))
                .isInstanceOf(ImportParseException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse("curl -X GET"))
                .isInstanceOf(ImportParseException.class);
    }
}
