package com.sben.config;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodstock.BloodStock;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.donor.Availability;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds default test users for PATIENT, DONOR, HOSPITAL, BLOOD_BANK, and ADMIN
 * when they do not exist yet. New dummy users get password 'Pass@123'; existing
 * accounts are never modified. Disable with {@code SEED_DEMO_USERS=false}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodStockRepository bloodStockRepository;
    private final PasswordEncoder passwordEncoder;

    public static final String DEFAULT_PASSWORD = "Pass@123";

    @Override
    public void run(String... args) {
        String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

        // 1. Admin
        seedUserIfMissing("admin@sben.com", "System Administrator", "9876543200", Role.ADMIN, encodedPassword, user -> {});

        // 2. Patient
        seedUserIfMissing("patient@sben.com", "Patient User", "9876543210", Role.PATIENT, encodedPassword, user -> {
            if (patientRepository.findByUserId(user.getId()).isEmpty()) {
                patientRepository.save(Patient.builder()
                        .userId(user.getId())
                        .bloodGroup("O+")
                        .age(30)
                        .gender("MALE")
                        .address("123 Emergency St, Mumbai")
                        .emergencyContact("9876543211")
                        .build());
            }
        });

        // 3. Donor
        seedUserIfMissing("donor@sben.com", "Donor User", "9876543220", Role.DONOR, encodedPassword, user -> {
            if (donorRepository.findByUserId(user.getId()).isEmpty()) {
                donorRepository.save(Donor.builder()
                        .userId(user.getId())
                        .bloodGroup("A+")
                        .age(28)
                        .city("Mumbai")
                        .latitude(new BigDecimal("19.0760"))
                        .longitude(new BigDecimal("72.8777"))
                        .lastDonationDate(LocalDate.now().minusMonths(3))
                        .availability(Availability.AVAILABLE)
                        .build());
            }
        });

        // 4. Hospital
        seedUserIfMissing("hospital@sben.com", "City General Hospital", "9876543230", Role.HOSPITAL, encodedPassword, user -> {
            if (hospitalRepository.findByUserId(user.getId()).isEmpty()) {
                hospitalRepository.save(Hospital.builder()
                        .userId(user.getId())
                        .hospitalName("City General Hospital")
                        .licenseNumber("HOSP-100200")
                        .phone("9876543230")
                        .address("45 Medical Enclave")
                        .city("Mumbai")
                        .state("Maharashtra")
                        .pincode("400001")
                        .latitude(new BigDecimal("19.0800"))
                        .longitude(new BigDecimal("72.8800"))
                        .verified(true)
                        .build());
            }
        });

        // 5. Blood Bank
        seedUserIfMissing("bloodbank@sben.com", "Red Cross Central Blood Bank", "9876543240", Role.BLOOD_BANK, encodedPassword, user -> {
            BloodBank bank = bloodBankRepository.findByUserId(user.getId()).orElse(null);
            if (bank == null) {
                bank = bloodBankRepository.save(BloodBank.builder()
                        .userId(user.getId())
                        .bankName("Red Cross Central Blood Bank")
                        .licenseNumber("BANK-300400")
                        .phone("9876543240")
                        .address("88 Lifesaver Ave")
                        .city("Mumbai")
                        .latitude(new BigDecimal("19.0850"))
                        .longitude(new BigDecimal("72.8850"))
                        .verified(true)
                        .build());
            }
            if (bank != null) {
                seedStock(bank.getId());
            }
        });
    }

    private void seedUserIfMissing(String email, String fullName, String phone, Role role, String encodedPassword, java.util.function.Consumer<User> profileSeeder) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .passwordHash(encodedPassword)
                    .role(role)
                    .emailVerified(true)
                    .enabled(true)
                    .build();
            user = userRepository.save(user);
            log.info("Seeded default account for role {}: {} with password 'Pass@123'", role, email);
        }
        // Existing accounts are left exactly as they are: a changed password,
        // a suspension or a deletion must survive a restart.
        profileSeeder.accept(user);
    }

    private void seedStock(Long bloodBankId) {
        List<String> groups = List.of("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-");
        int[] units = {15, 5, 20, 4, 30, 8, 10, 3};
        for (int i = 0; i < groups.size(); i++) {
            String grp = groups.get(i);
            int qty = units[i];
            if (bloodStockRepository.findByBloodBankIdAndBloodGroupAndDeletedFalse(bloodBankId, grp).isEmpty()) {
                bloodStockRepository.save(BloodStock.builder()
                        .bloodBankId(bloodBankId)
                        .bloodGroup(grp)
                        .unitsAvailable(qty)
                        .expiryDate(LocalDate.now().plusDays(35))
                        .build());
            }
        }
    }
}
