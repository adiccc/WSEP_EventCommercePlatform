package domain.company;

import application.CompanyService;
import application.IAuth;
import application.Response;
import domain.event.IOrderRepo;
import domain.user.Member;
import domain.user.User;
import infrastructure.CompanyRepoImpl;
import infrastructure.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CompanyConcurrencyTest {

    private CompanyRepoImpl companyRepo;
    private UserRepo userRepo;
    private CompanyService companyService;

    @BeforeEach
    public void setUp() {
        companyRepo = new CompanyRepoImpl();
        userRepo = new UserRepo();

        IAuth authMock = mock(IAuth.class);
        IOrderRepo orderRepoMock = mock(IOrderRepo.class);

        when(authMock.getUserId(anyString())).thenReturn(new Response<>(1,""));
        when(authMock.isLoggedIn(anyString())).thenReturn(Response.ok(true));

        companyService = new CompanyService(authMock, companyRepo, userRepo);

        Member member = new Member("admin123","aa","aa","ff","050-422-4567", LocalDate.of(2022,12,3),"aa");
        member.setConnected(true);
        userRepo.store(member);
    }

    @Test
    public void Given100ConcurrentRequests_WhenCreateSameCompany_ThenOnlyOneSucceeds() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();

                    Response<Company> response = companyService.createProductionCompany(
                            "admin123", 100, "RaceCompany", "race@comp.com", "0500000000", "bank1"
                    );

                    if (response.getValue() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        assertEquals(1, successCount.get(), "Race Condition detected: More than one thread created the company!");
        assertEquals(99, failCount.get(), "99 threads should have been rejected due to duplicate ID");
    }
}
