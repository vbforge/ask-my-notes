package com.vbforge.asknotes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AskMyNotesApplicationTests {

    @Test
    void contextLoads() {
        // Starts the whole app against a fresh pgvector container: proves Flyway V1 runs
        // on an empty database and every bean wires up.
    }
}