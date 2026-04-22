package test.application;

import application.CompanyService;
import application.Response;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.user.IUserRepo;
import domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CompanyServiceTest {

    private ICompanyRepo companyRepoMock;
    private IUserRepo userRepoMock;
    private CompanyService companyService;
    private User mockUser;

    @BeforeEach
    public void setUp() {
        companyRepoMock = mock(ICompanyRepo.class);
        userRepoMock = mock(IUserRepo.class);

        companyService = new CompanyService(companyRepoMock, userRepoMock);

        mockUser = new User("user123");
        mockUser.setConnected(true);
    }

    //Successful_Creation
    @Test
    public void GivenValidInputs_WhenCreateProductionCompany_ThenReturnSuccessAndCreateCompany() {
        // Arrange
        when(userRepoMock.findById("token123")).thenReturn(mockUser);
        when(companyRepoMock.existsById("comp555")).thenReturn(false);
        when(companyRepoMock.existsByName("LiveNation")).thenReturn(false);

        // Act
        Response<Company> response = companyService.createProductionCompany(
                "token123", "comp555", "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        // Assert
        assertNotNull(response.getValue(), "The company object should not be null on success");
        assertEquals("LiveNation", response.getValue().getCompanyName());

        verify(companyRepoMock, times(1)).save(any(Company.class));
        verify(userRepoMock, times(1)).save(mockUser);
    }

    // Duplicate_Company_Number
    @Test
    public void GivenExistingCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        // Arrange
        when(userRepoMock.findById("token123")).thenReturn(mockUser);
        when(companyRepoMock.existsById("comp555")).thenReturn(true); // מדמים שהח"פ כבר קיים!

        // Act
        Response<Company> response = companyService.createProductionCompany(
                "token123", "comp555", "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        // Assert
        assertNull(response.getValue(), "Company object should be null when creation fails");
        assertTrue(response.getMessage().contains("already exists"));

        verify(companyRepoMock, never()).save(any(Company.class));
    }

    // Logged_Out_User_Access
    @Test
    public void GivenDisconnectedUser_WhenCreateProductionCompany_ThenReturnError() {
        // Arrange
        mockUser.setConnected(false);
        when(userRepoMock.findById("token123")).thenReturn(mockUser);

        // Act
        Response<Company> response = companyService.createProductionCompany(
                "token123", "comp555", "LiveNation", "admin@livenation.com", "0501234567", "bank-123"
        );

        // Assert
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("logged in"));
    }

    // Wrong_Mandatory_Fields
    @Test
    public void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        // Arrange
        when(userRepoMock.findById("token123")).thenReturn(mockUser);
        when(companyRepoMock.existsById("comp555")).thenReturn(false);

        // Act
        Response<Company> response = companyService.createProductionCompany(
                "token123", "comp555", "LiveNation", "invalidEmailFormat", "0501234567", "bank-123"
        );

        // Assert
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
    }
}