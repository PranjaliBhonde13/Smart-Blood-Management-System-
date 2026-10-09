package com.sben.auth.device;

import com.sben.notification.email.AccountEmails;
import com.sben.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginDeviceServiceTest {

    @Mock private LoginDeviceRepository repository;
    @Mock private AccountEmails accountEmails;

    private LoginDeviceService service;
    private final User user = User.builder().id(5L).email("p@gmail.com").fullName("Priya").build();

    @BeforeEach
    void setUp() {
        service = new LoginDeviceService(repository, accountEmails);
        when(repository.findByUserIdAndFingerprint(eq(5L), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void firstEverSignInIsRecordedWithoutAlert() {
        when(repository.existsByUserId(5L)).thenReturn(false);
        service.recordSignIn(user);
        verify(repository).save(any(LoginDevice.class));
        verifyNoInteractions(accountEmails);
    }

    @Test
    void signInFromNewDeviceSendsAlert() {
        when(repository.existsByUserId(5L)).thenReturn(true);
        service.recordSignIn(user);
        verify(accountEmails).newSignIn(eq(user), any(), anyString());
    }

    @Test
    void knownDeviceOnlyUpdatesLastSeen() {
        LoginDevice known = LoginDevice.builder().userId(5L).fingerprint("x")
                .firstSeenAt(LocalDateTime.now().minusDays(3)).lastSeenAt(LocalDateTime.now().minusDays(3)).build();
        when(repository.findByUserIdAndFingerprint(eq(5L), anyString())).thenReturn(Optional.of(known));
        service.recordSignIn(user);
        assertTrue(known.getLastSeenAt().isAfter(LocalDateTime.now().minusMinutes(1)));
        verifyNoInteractions(accountEmails);
    }

    @Test
    void describesBrowserAndOperatingSystem() {
        assertEquals("Chrome on Windows", LoginDeviceService.describe(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36"));
        assertEquals("Edge on Windows", LoginDeviceService.describe(
                "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/129.0 Safari/537.36 Edg/129.0"));
        assertEquals("Safari on iOS", LoginDeviceService.describe(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile Safari/604.1"));
        assertEquals("Unknown device", LoginDeviceService.describe(null));
    }

    @Test
    void sameNetworkPrefixIsTreatedAsTheSameNetwork() {
        assertEquals(LoginDeviceService.networkPrefix("49.36.10.21"), LoginDeviceService.networkPrefix("49.36.10.200"));
        assertNotEquals(LoginDeviceService.networkPrefix("49.36.10.21"), LoginDeviceService.networkPrefix("49.36.11.21"));
    }
}
