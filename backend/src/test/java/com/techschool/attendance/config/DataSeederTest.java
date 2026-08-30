package com.techschool.attendance.config;

import com.techschool.attendance.data.model.*;
import com.techschool.attendance.data.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataSeeder dataSeeder;

    private final List<User> savedUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(
                userRepository,
                cohortRepository,
                deviceRepository,
                attendanceRepository,
                holidayRepository,
                passwordEncoder
        );
        ReflectionTestUtils.setField(dataSeeder, "seedData", true);
        savedUsers.clear();
    }

    @Test
    void testDataSeeder_seedsAllEntitiesSuccessfully() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "encoded_" + inv.getArgument(0));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("user-id-" + (savedUsers.size() + 1));
            }
            savedUsers.add(u);
            return u;
        });

        when(cohortRepository.save(any(Cohort.class))).thenAnswer(inv -> {
            Cohort c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("cohort-id-" + System.currentTimeMillis());
            }
            return c;
        });

        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId("device-id-" + System.currentTimeMillis());
            }
            return d;
        });

        when(userRepository.findByRole(User.Role.STUDENT)).thenAnswer(inv -> {
            List<User> students = new ArrayList<>();
            for (User u : savedUsers) {
                if (u.getRole() == User.Role.STUDENT) {
                    students.add(u);
                }
            }
            return students;
        });

        // Execute seeding
        assertDoesNotThrow(() -> dataSeeder.run());

        // Verify Users saved (1 Admin + 2 Facilitators + 6 Students = 9 Users)
        verify(userRepository, atLeast(9)).save(any(User.class));

        // Verify Cohorts saved (Cohort 29 and Cohort 30 = 2 Cohorts)
        verify(cohortRepository, times(2)).save(any(Cohort.class));

        // Verify Devices saved (1 for each student = 6 Devices)
        verify(deviceRepository, times(6)).save(any(Device.class));

        // Verify Attendance records saved
        verify(attendanceRepository, atLeastOnce()).save(any(Attendance.class));

        // Verify Holiday saved
        verify(holidayRepository, times(1)).save(any(Holiday.class));
    }

    @Test
    void testDataSeeder_alreadySeeded_skipsExecution() {
        when(userRepository.count()).thenReturn(10L);

        dataSeeder.run();

        verify(userRepository, never()).save(any(User.class));
        verify(cohortRepository, never()).save(any(Cohort.class));
    }
}
