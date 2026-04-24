package domain.company;

import application.CompanyService;
import application.IAuth;
import application.Response;
import application.TokenService;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.user.IUserRepo;
import domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyUnitTest {

    private ICompanyRepo companyRepoMock;
    private IUserRepo userRepoMock;
    private IAuth authMock;
    private TokenService tokenServiceMock;
    private IOrderRepo orderRepoMock;
    private CompanyService companyService;
    private User mockUser;

    @BeforeEach
    public void setUp() {
        companyRepoMock = mock(ICompanyRepo.class);
        userRepoMock = mock(IUserRepo.class);
        authMock = mock(IAuth.class);
        tokenServiceMock = mock(TokenService.class);
        orderRepoMock = mock(IOrderRepo.class);

        companyService = new CompanyService(tokenServiceMock, authMock, companyRepoMock, userRepoMock, orderRepoMock);

        mockUser = new User("user123");
        mockUser.setConnected(true);

        when(authMock.getUserId(anyString())).thenReturn(1);
    }

    @Test
    public void GivenValidInputs_WhenCreateProductionCompany_ThenReturnSuccessAndCreateCompany() {
        when(userRepoMock.findById("token123")).thenReturn(mockUser);
        when(companyRepoMock.existsById(555)).thenReturn(false);
        when(companyRepoMock.existsByName("LiveNation")).thenReturn(false);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNotNull(response.getValue(), "The company object should not be null on success");
        assertEquals("LiveNation", response.getValue().getCompanyName());

        verify(companyRepoMock, times(1)).save(any(Company.class));
        verify(userRepoMock, times(1)).save(mockUser);
    }

    @Test
    public void GivenExistingCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById("token123")).thenReturn(mockUser);
        when(companyRepoMock.existsById(555)).thenReturn(true);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue(), "Company object should be null when creation fails");
        assertTrue(response.getMessage().contains("already exists"));

        verify(companyRepoMock, never()).save(any(Company.class));
    }

    @Test
    public void GivenDisconnectedUser_WhenCreateProductionCompany_ThenReturnError() {
        mockUser.setConnected(false);
        when(userRepoMock.findById("token123")).thenReturn(mockUser);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("logged in"));
    }

    @Test
    public void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        when(userRepoMock.findById("token123")).thenReturn(mockUser);
        when(companyRepoMock.existsById(555)).thenReturn(false);

        Response<Company> response = companyService.createProductionCompany(
                "token123", 555, "LiveNation", "invalidEmailFormat", "0501234567", "bank-123"
        );

        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
    }
}
