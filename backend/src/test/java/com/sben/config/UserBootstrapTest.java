package com.sben.config;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBootstrapTest {

    @Mock private UserRepository userRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DonorRepository donorRepository;
    @Mock private HospitalRepository hospitalRepository;
    @Mock private BloodBankRepository bloodBankRepository;
    @Mock private BloodStockRepository bloodStockRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(
                userRepository,
                patientRepository,
                donorRepository,
                hospitalRepository,
                bloodBankRepository,
                bloodStockRepository,
                passwordEncoder
        );
        when(passwordEncoder.encode(AdminBootstrap.DEFAULT_PASSWORD)).thenReturn("encodedPassword");
    }

    @Test
    void run_ShouldSeedAllDummyUsersAndProfilesWhenMissing() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return u;
        });

        when(patientRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(donorRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(hospitalRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(bloodBankRepository.findByUserId(100L)).thenReturn(Optional.empty());

        BloodBank mockBank = BloodBank.builder().id(10L).userId(100L).bankName("Red Cross").build();
        when(bloodBankRepository.save(any(BloodBank.class))).thenReturn(mockBank);

        bootstrap.run();

        // 5 roles seeded
        verify(userRepository, times(5)).save(any(User.class));
        verify(patientRepository, times(1)).save(any(Patient.class));
        verify(donorRepository, times(1)).save(any(Donor.class));
        verify(hospitalRepository, times(1)).save(any(Hospital.class));
        verify(bloodBankRepository, times(1)).save(any(BloodBank.class));
        verify(bloodStockRepository, atLeast(8)).save(any());
    }

    @Test
    void run_ShouldNotOverwriteExistingUsers() {
        User existingPatient = User.builder()
                .id(1L)
                .email("patient@sben.com")
                .role(Role.PATIENT)
                .passwordHash("oldHash")
                .build();

        when(userRepository.findByEmail("patient@sben.com")).thenReturn(Optional.of(existingPatient));
        when(userRepository.findByEmail(argThat(e -> !e.equals("patient@sben.com")))).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.findByUserId(1L)).thenReturn(Optional.of(Patient.builder().id(1L).userId(1L).build()));

        bootstrap.run();

        // Only the 4 missing accounts are created; the existing one is untouched
        // (a changed password or a suspension must survive restarts).
        verify(userRepository, times(4)).save(any(User.class));
        verify(userRepository, never()).save(existingPatient);
        org.junit.jupiter.api.Assertions.assertEquals("oldHash", existingPatient.getPasswordHash());
    }
}
