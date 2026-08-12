package com.techschool.attendance.config;

import com.techschool.attendance.model.*;
import com.techschool.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final DeviceRepository deviceRepository;
    private final AttendanceRepository attendanceRepository;
    private final HolidayRepository holidayRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-data:true}")
    private boolean seedData;

    @Override
    public void run(String... args) {
        if (!seedData) return;
        if (userRepository.count() > 0) {
            log.info("Data already seeded — fast startup complete");
            return;
        }

        log.info("Seeding demo data...");

        // ── Admin ──
        User admin = new User();
        admin.setName("Super Admin");
        admin.setEmail("admin@techschool.edu");
        admin.setPhone("+234 800 000 0001");
        admin.setPasswordHash(passwordEncoder.encode("Admin@1234"));
        admin.setRole(User.Role.SUPER_ADMIN);
        admin.setEmailVerified(true);
        userRepository.save(admin);

        // ── Facilitators ──
        User fac1 = new User();
        fac1.setName("Mr. James Obi");
        fac1.setEmail("james.obi@techschool.edu");
        fac1.setPhone("+234 801 234 5678");
        fac1.setPasswordHash(passwordEncoder.encode("Fac@1234"));
        fac1.setRole(User.Role.FACILITATOR);
        fac1.setEmailVerified(true);

        User fac2 = new User();
        fac2.setName("Dr. Sarah Mensah");
        fac2.setEmail("sarah.mensah@techschool.edu");
        fac2.setPhone("+234 802 345 6789");
        fac2.setPasswordHash(passwordEncoder.encode("Fac@1234"));
        fac2.setRole(User.Role.FACILITATOR);
        fac2.setEmailVerified(true);

        // Save facilitators first to get IDs
        fac1 = userRepository.save(fac1);
        fac2 = userRepository.save(fac2);

        // ── Cohorts ──
        Cohort c29 = new Cohort();
        c29.setName("Cohort 29");
        c29.setFacilitatorId(fac1.getId());
        c29.setSchedule("7:00 AM – 8:30 AM");
        c29 = cohortRepository.save(c29);

        Cohort c30 = new Cohort();
        c30.setName("Cohort 30");
        c30.setFacilitatorId(fac2.getId());
        c30.setSchedule("7:00 AM – 8:30 AM");
        c30 = cohortRepository.save(c30);

        // Assign cohorts to facilitators
        fac1.setAssignedCohortIds(List.of(c29.getId()));
        fac2.setAssignedCohortIds(List.of(c30.getId()));
        userRepository.save(fac1);
        userRepository.save(fac2);

        // ── Students ──
        String[][] students = {
            {"Ada Okafor",    "ada.okafor@techschool.edu",    "+234 803 111 2222", c29.getId(), "TS-2024-0001"},
            {"Emeka Nwosu",   "emeka.nwosu@techschool.edu",   "+234 804 222 3333", c29.getId(), "TS-2024-0002"},
            {"Tunde Adeyemi", "tunde.adeyemi@techschool.edu", "+234 805 333 4444", c29.getId(), "TS-2024-0003"},
            {"Chioma Eze",    "chioma.eze@techschool.edu",    "+234 806 444 5555", c30.getId(), "TS-2024-0004"},
            {"Kemi Abiola",   "kemi.abiola@techschool.edu",   "+234 807 555 6666", c30.getId(), "TS-2024-0005"},
            {"Dayo Bello",    "dayo.bello@techschool.edu",    "+234 808 666 7777", c29.getId(), "TS-2024-0006"},
        };

        for (String[] s : students) {
            User student = new User();
            student.setName(s[0]);
            student.setEmail(s[1]);
            student.setPhone(s[2]);
            student.setPasswordHash(passwordEncoder.encode("Student@1234"));
            student.setRole(User.Role.STUDENT);
            student.setCohortId(s[3]);
            student.setRegistrationNumber(s[4]);
            student.setEmailVerified(true);
            User saved = userRepository.save(student);

            // Register device for each student
            Device device = new Device();
            device.setStudentId(saved.getId());
            device.setFingerprint("FP-" + saved.getId().substring(0, 8).toUpperCase());
            device.setImei("358240" + (long)(Math.random() * 1e10));
            device.setLocked(true);
            device.setRegisteredBy(admin.getId());
            Device savedDev = deviceRepository.save(device);

            saved.setDeviceId(savedDev.getId());
            userRepository.save(saved);
        }

        // ── Sample attendance records ──
        List<User> allStudents = userRepository.findByRole(User.Role.STUDENT);
        LocalDate[] dates = {
            LocalDate.now().minusDays(4),
            LocalDate.now().minusDays(3),
            LocalDate.now().minusDays(2),
            LocalDate.now().minusDays(1),
        };

        Attendance.AttendanceStatus[][] statuses = {
            {Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.PRESENT,
             Attendance.AttendanceStatus.LATE, Attendance.AttendanceStatus.PRESENT},
            {Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.LATE,
             Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.ABSENT},
            {Attendance.AttendanceStatus.ABSENT, Attendance.AttendanceStatus.PRESENT,
             Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.LATE},
            {Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.PRESENT,
             Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.PRESENT},
            {Attendance.AttendanceStatus.LATE, Attendance.AttendanceStatus.LATE,
             Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.PRESENT},
            {Attendance.AttendanceStatus.PRESENT, Attendance.AttendanceStatus.ABSENT,
             Attendance.AttendanceStatus.EXCUSED, Attendance.AttendanceStatus.PRESENT},
        };

        for (int si = 0; si < allStudents.size(); si++) {
            User student = allStudents.get(si);
            for (int di = 0; di < dates.length; di++) {
                if (dates[di].getDayOfWeek().getValue() >= 6) {
                    continue; // Do not record attendance on Saturdays and Sundays!
                }
                Attendance att = new Attendance();
                att.setStudentId(student.getId());
                att.setCohortId(student.getCohortId());
                att.setDate(dates[di]);
                Attendance.AttendanceStatus status = statuses[si % statuses.length][di];
                att.setStatus(status);
                if (status != Attendance.AttendanceStatus.ABSENT) {
                    att.setMarkedAt(Instant.now().minusSeconds(86400L * (dates.length - di)));
                }
                att.setManual(false);
                attendanceRepository.save(att);
            }
        }

        // ── Sample custom holiday (applies to all cohorts) ──
        Holiday holiday = new Holiday();
        holiday.setName("School Founders' Day");
        holiday.setStartDate(LocalDate.now().plusDays(30));
        holiday.setEndDate(LocalDate.now().plusDays(30));
        holiday.setReason("Annual founders' day celebration — no classes");
        holiday.setAppliesToAll(true);
        holiday.setActive(true);
        holiday.setCreatedById(admin.getId());
        holiday.setCreatedByName(admin.getName());
        holidayRepository.save(holiday);

        log.info("✓ Demo data seeded successfully");
        log.info("  Admin: admin@techschool.edu / Admin@1234");
        log.info("  Facilitator: james.obi@techschool.edu / Fac@1234");
        log.info("  Student: ada.okafor@techschool.edu / Student@1234");
    }
}
