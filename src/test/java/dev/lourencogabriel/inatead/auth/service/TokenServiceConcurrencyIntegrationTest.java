package dev.lourencogabriel.inatead.auth.service;

import dev.lourencogabriel.inatead.auth.dto.AuthResponse;
import dev.lourencogabriel.inatead.auth.model.RefreshToken;
import dev.lourencogabriel.inatead.auth.repository.RefreshTokenRepository;
import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TokenServiceConcurrencyIntegrationTest {

    private static final String USER_EMAIL = "concurrency@example.com";

    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.0")
            .withDatabaseName("inat_ead_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldAllowOnlyOneRotationWhenSameRefreshTokenIsUsedConcurrently() throws Exception {
        Person person = this.personRepository.saveAndFlush(this.person());
        User newUser = new User("Concurrent User", USER_EMAIL, "encoded-password");
        newUser.setPerson(person);
        newUser.setStatus(UserAccountStatus.ACTIVE);
        newUser.setEmailVerifiedAt(Instant.now());
        User user = this.userRepository.saveAndFlush(newUser);
        AuthResponse initialTokens = this.tokenService.generateTokens(
                user.getUlid(),
                "initial-agent",
                "203.0.113.1"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstRotationFinished = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondRotationStarted = new CountDownLatch(1);

        try {
            Future<AuthResponse> firstResult = executor.submit(() -> {
                TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
                return transaction.execute(status -> {
                    AuthResponse response = this.tokenService.refreshTokens(
                            initialTokens.refreshToken(),
                            "first-agent",
                            "203.0.113.2"
                    );
                    firstRotationFinished.countDown();
                    this.await(allowFirstCommit);
                    return response;
                });
            });

            assertTrue(
                    firstRotationFinished.await(10, SECONDS),
                    "The first rotation did not acquire and hold the token lock"
            );

            Future<AuthResponse> secondResult = executor.submit(() -> {
                secondRotationStarted.countDown();
                return this.tokenService.refreshTokens(
                        initialTokens.refreshToken(),
                        "second-agent",
                        "203.0.113.3"
                );
            });

            assertTrue(secondRotationStarted.await(5, SECONDS));

            try {
                assertThrows(
                        TimeoutException.class,
                        () -> secondResult.get(500, MILLISECONDS),
                        "The second rotation should wait while the first transaction holds the lock"
                );
            } finally {
                allowFirstCommit.countDown();
            }

            AuthResponse winningResponse = firstResult.get(10, SECONDS);
            ExecutionException losingResult = assertThrows(
                    ExecutionException.class,
                    () -> secondResult.get(10, SECONDS)
            );

            JwtException losingException = assertInstanceOf(
                    JwtException.class,
                    losingResult.getCause()
            );
            RefreshToken originalToken = this.refreshTokenRepository
                    .findByTokenHash(this.hash(initialTokens.refreshToken()))
                    .orElseThrow();
            RefreshToken replacementToken = this.refreshTokenRepository
                    .findByTokenHash(this.hash(winningResponse.refreshToken()))
                    .orElseThrow();

            assertAll(
                    () -> assertEquals("Refresh token has been revoked", losingException.getMessage()),
                    () -> assertNotNull(originalToken.getRevokedAt()),
                    () -> assertEquals(
                            this.hash(winningResponse.refreshToken()),
                            originalToken.getReplacedByToken()
                    ),
                    () -> assertNull(replacementToken.getRevokedAt()),
                    () -> assertEquals(2, this.refreshTokenRepository.count())
            );
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, SECONDS));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test was interrupted", exception);
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Person person() {
        Address address = new Address();
        address.setPostalCode("80000000");
        address.setStreet("Concurrency Street");
        address.setStreetNumber("1");
        address.setDistrict("Center");
        address.setCity("Curitiba");
        address.setStateCode("PR");

        Person person = new Person();
        person.setAddress(address);
        person.setFullName("Concurrent User");
        person.setTaxId("98765432100");
        person.setContactEmail(USER_EMAIL);
        person.setPhoneNumber("41999999999");
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        return person;
    }
}
