package com.example.operationalmetrics.repository;

import io.quarkus.test.Mock;
import jakarta.enterprise.inject.Produces;
import org.jdbi.v3.core.Jdbi;
import org.mockito.Mockito;

/**
 * Test-only CDI alternative that replaces {@link JdbiProducer} with a
 * Mockito mock.
 *
 * <p>We cannot use {@code @InjectMock Jdbi} directly because the JDBI API
 * declares methods with {@code <X extends Exception>} type bounds, which
 * confuses the bytecode-generated Arc client proxy created by Quarkus's
 * {@code MockSupport} (it throws
 * {@code NoClassDefFoundError: X extends java/lang/Exception} when
 * enumerating the proxy's methods). Producing the mock as a CDI alternative
 * via {@link Mock @Mock} sidesteps that proxy creation, and tests can still
 * stub / verify it through {@code Mockito.mockingDetails(...)} or by
 * injecting the same instance with {@code @Inject Jdbi}.</p>
 */
public class JdbiTestProducer {

    /**
     * Single shared mock instance — one per JVM is fine for these tests.
     * Each {@code @QuarkusTest} (which restarts the Mockito harness via
     * {@code Mockito.reset(...)}) should reset it explicitly to avoid cross-
     * test bleed; see {@code resetJdbiMock(Jdbi)} below.
     */
    public static final Jdbi MOCK = Mockito.mock(Jdbi.class);

    @Mock
    @Produces
    public Jdbi jdbi() {
        return MOCK;
    }
}
