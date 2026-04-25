package domain.company;

import application.CompanyService;
import application.IAuth;
import application.Response;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyUnitTest {

    private ICompanyRepo companyRepoMock;
    private IUserRepo userRepoMock;
    private IAuth authMock;
    private IOrderRepo orderRepoMock;
    private CompanyService companyService;
    private Member mockUser;
    private int userId;
    private String sessionToken;

    @BeforeEach
    public void setUp() {
        companyRepoMock = mock(ICompanyRepo.class);
        userRepoMock = mock(IUserRepo.class);
        authMock = mock(IAuth.class);
        orderRepoMock = mock(IOrderRepo.class);

        companyService = new CompanyService(authMock, companyRepoMock, userRepoMock, orderRepoMock);

        mockUser = new Member("user123","aa", "aa","bb","050-432-6677", LocalDate.of(2001,5,12),"ee");
        mockUser.setConnected(true);

        when(authMock.getUserId(anyString())).thenReturn(1);
    }

    @Test
    public void GivenValidInputs_WhenCreateProductionCompany_ThenReturnSuccessAndCreateCompany() {
        when(userRepoMock.findById(1)).thenReturn(mockUser);
        when(companyRepoMock.existsById(555)).thenReturn(false);
        when(companyRepoMock.existsByName("LiveNation")).thenReturn(false);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNotNull(response.getValue(), "The company object should not be null on success");
        assertEquals("LiveNation", response.getValue().getCompanyName());

        verify(companyRepoMock, times(1)).store(any(Company.class));
        verify(userRepoMock, times(1)).store(mockUser);
    }

    @Test
    public void GivenExistingCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById(1)).thenReturn(mockUser);
        when(companyRepoMock.existsById(555)).thenReturn(true);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue(), "Company object should be null when creation fails");
        assertTrue(response.getMessage().contains("already exists"));

        verify(companyRepoMock, never()).store(any(Company.class));
    }

    @Test
    public void GivenDisconnectedUser_WhenCreateProductionCompany_ThenReturnError() {
        mockUser.setConnected(false);
        when(userRepoMock.findById(1)).thenReturn(mockUser);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("logged in"));
    }

    @Test
    public void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById(1)).thenReturn(mockUser);
        when(companyRepoMock.existsById(555)).thenReturn(false);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "invalidEmailFormat", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
    }
}
