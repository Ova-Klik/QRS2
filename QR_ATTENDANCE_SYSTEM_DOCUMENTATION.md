# QRAttendance — System Documentation Manual

---

### Cover Page
- **System Name**: QRAttendance
- **Title**: Complete System Architecture, Operational Manual & User Guide
- **Version**: 1.0.0
- **Release Date**: August 2026
- **Built By**: Victor Olaomo (IamByte)
- **Technology Stack**: React, Spring Boot 3, MongoDB 7, Brevo SMTP

---

## Glossary of Key Terms

| Term | Definition |
| --- | --- |
| **Cohort** | A designated group or class of students (e.g., Cohort 29) assigned to a structured curriculum and facilitators. |
| **Scheduled Working Day** | Any weekday (Monday through Friday) that is not configured as a national or custom school holiday in the system calendar. |
| **Attendance Rate** | The percentage of scheduled working days on which a student marked attendance (Present or Late), excluding approved excuse days. |
| **Rolling QR Code** | A dynamic QR code payload that refreshes automatically every few seconds to prevent proxy attendance or screenshot sharing. |
| **Attendance Code** | A 6-character alphanumeric backup code generated alongside the QR session, allowing manual entry if a camera is unavailable. |
| **Device Binding** | Security mechanism associating a student's account with their primary mobile device hardware fingerprint. |
| **Excuse Request** | A formal student submission for temporary leave due to medical or official reasons, requiring facilitator approval. |
| **Behaviour Score** | A weighted performance metric (0–100%) evaluating student punctuality, attendance consistency, and absenteeism. |

---

## 1. Table of Contents
1. [Introduction](#2-introduction)
2. [System Overview & Workflow](#3-system-overview--workflow)
3. [User Roles & Security Permissions Matrix](#4-user-roles--security-permissions-matrix)
4. [Administrator Guide](#5-administrator-guide)
5. [Facilitator Guide](#6-facilitator-guide)
6. [Student Guide](#7-student-guide)
7. [Attendance Rules & Business Logic](#8-attendance-rules--business-logic)
8. [Behaviour Analytics & Rating Model](#9-behaviour-analytics--rating-model)
9. [Reports & Export Standards](#10-reports--export-standards)
10. [Device Management & Security Binding](#11-device-management--security-binding)
11. [Calendar & Holiday Management](#12-calendar--holiday-management)
12. [Email Verification & Brevo Integration](#13-email-verification--brevo-integration)
13. [Frequently Asked Questions (FAQs)](#14-frequently-asked-questions-faqs)
14. [Troubleshooting Guide](#15-troubleshooting-guide)
15. [Deployment Guide](#16-deployment-guide)
16. [System Architecture](#17-system-architecture)
17. [API Specification Documentation](#18-api-specification-documentation)
18. [Database Schema Documentation](#19-database-schema-documentation)
19. [System Maintenance Guide](#20-system-maintenance-guide)
20. [Best Practices](#21-best-practices)
21. [Security Model](#22-security-model)
22. [Credits & Licensing](#23-credits--licensing)

---

## 2. Introduction

### What is QRAttendance?
**QRAttendance** is an enterprise-grade Smart Attendance & Behaviour Analytics Management System built specifically for educational institutions, academies, and technical bootcamps. It leverages dynamic rolling QR codes, geolocation/network validation, device fingerprinting, and automated working day calculations to provide fraud-proof attendance tracking.

### Core Purpose
Traditional paper sign-in sheets and static attendance registers are prone to proxy signing ("buddy scanning"), lost records, unverified attendance timestamps, and tedious manual calculations. QRAttendance replaces manual processes with real-time digital verification, automated attendance rate calculations, and predictive student behaviour analytics.

### Target Users
- **Super Administrators**: Oversee institutional settings, cohorts, facilitators, system logs, global calendar, and full student analytics.
- **Facilitators**: Generate live QR attendance sessions, monitor real-time check-ins, review excuse requests, mark manual overrides, and export cohort reports.
- **Students**: Self-register into cohorts, scan dynamic QR codes or enter backup attendance codes, track personal attendance history, and manage excuse requests.
- **System Administrators & Developers**: Deploy, monitor, maintain, and scale the application stack.

### Key Benefits
- **Zero Proxy Attendance**: Dynamic QR codes refresh every 5 seconds; device binding prevents account sharing across multiple phones.
- **Automated Working Day Tracking**: Evaluates student attendance strictly against valid school days (Monday–Friday), excluding holidays and weekends.
- **Two-Tier Facilitator Reports**: Display attended students ordered by arrival time (earliest first), followed by absent/unattended students sorted alphabetically (A–Z).
- **Executive CSV & Excel Exports**: Clean 7-column export format formatted specifically for administrative presentation.

---

## 3. System Overview & Workflow

```
[ Super Administrator ]
          │
          ├─► 1. Configures Cohorts & Facilitators
          ├─► 2. Sets Calendar Holidays & Attendance Rules
          └─► 3. Monitors Global System Audit Logs & Analytics

[ Facilitator ]
          │
          ├─► 1. Launches QR Generation Session (or Code)
          ├─► 2. Monitors Live Attendance Feed (Real-time updates)
          ├─► 3. Approves / Rejects Student Excuse Requests
          └─► 4. Exports Clean CSV/XLSX Reports

[ Student ]
          │
          ├─► 1. Scans Dynamic QR Code via Mobile Camera (or Enters Code)
          ├─► 2. System Validates Time Window, Device Binding, & Cohort
          └─► 3. Attendance Status (PRESENT / LATE / EXCUSED) Recorded
```

---

## 4. User Roles & Security Permissions Matrix

| Feature / Action | Super Admin | Facilitator | Student |
| --- | :---: | :---: | :---: |
| System Settings & Rule Configuration | ✅ | ❌ | ❌ |
| Create / Edit Cohorts & Assign Facilitators | ✅ | ❌ | ❌ |
| Manage System Users (Deactivate / Edit) | ✅ | ❌ | ❌ |
| View System Audit Logs & Security Traces | ✅ | ❌ | ❌ |
| Calendar & Holiday Management | ✅ | ❌ | ❌ |
| Generate QR Session & Display Code | ✅ | ✅ | ❌ |
| Live Attendance View & Manual Override | ✅ | ✅ | ❌ |
| Review & Process Excuse Requests | ✅ | ✅ | ❌ |
| Export Cohort Attendance Reports (CSV / XLSX) | ✅ | ✅ | ❌ |
| Scan QR Code / Submit Backup Code | ❌ | ❌ | ✅ |
| Submit Personal Excuse Request | ❌ | ❌ | ✅ |
| View Personal Attendance History & Rating | ❌ | ❌ | ✅ |

---

## 5. Administrator Guide

### 5.1 Admin Dashboard (`/admin`)
- **Purpose**: Provides high-level metrics across the institution, including total active cohorts, total registered students, overall attendance rate, and daily check-in volume.
- **Key Cards & Indicators**: Total Students, Active Cohorts, Today's Attendance Rate, Total Absences.
- **Navigation Shortcuts**: Quick links to Cohort Management, Student Records, Facilitator Assignments, and System Settings.

### 5.2 Student Management (`/admin/students`)
- **Purpose**: Manage all student records across all cohorts.
- **Search & Filters**: Search by student full name, email, or registration number (`TS-YYYY-XXXX`). Filter by cohort assignment or active/deactivated status.
- **Actions**:
  - **Edit Student**: Update full name, registration number, assigned cohort, or email.
  - **Deactivate / Activate Account**: Revoke access for withdrawn students.
  - **Reset Password**: Generate a secure temporary password.
- **Pagination**: Supports 10, 20, 50, or 100 items per page with instant server-side query filtering.

### 5.3 Facilitator Management (`/admin/facilitators`)
- **Purpose**: Register and manage faculty members and facilitators.
- **Cohort Assignment**: Assign one or multiple cohorts to a facilitator so they gain access to live attendance feeds and cohort-specific reporting.

### 5.4 Cohort Management (`/admin/cohorts`)
- **Purpose**: Create and manage academic classes/cohorts (e.g., Cohort 29).
- **Fields**: Cohort Name, Start Date, Expected End Date, Description, Status (Active/Archived).

### 5.5 Device Security & Unlocking (`/admin/devices`)
- **Purpose**: Oversee registered mobile devices bound to student accounts.
- **Actions**: Unlock or unbind a student's registered device if they upgrade or replace their phone.

### 5.6 Audit Logs (`/admin/audit-logs`)
- **Purpose**: Immutable security audit trail recording every significant system event.
- **Tracked Actions**: `LOGIN`, `USER_CREATED`, `ATTENDANCE_MARKED`, `ATTENDANCE_MANUAL_OVERRIDE`, `DEVICE_REGISTERED`, `DEVICE_UNLOCKED`, `EXCUSE_REVIEWED`, `PASSWORD_RESET`.
- **Fields Captured**: Timestamp, Actor Name, Actor Role, Action Type, Target Student, Description, Client IP Address.

### 5.7 Calendar & Holiday Configuration (`/admin/calendar`)
- **Purpose**: Configure school operational dates, national holidays, and custom vacation breaks.
- **Behavior**: Any day designated as a Holiday is automatically excluded from total scheduled working days calculations.

### 5.8 System Settings (`/admin/settings`)
- **Purpose**: Tune application business rules.
- **Configurable Rules**:
  - **QR Session Window**: Start Time (e.g., `07:00`) and End Time (e.g., `12:00`).
  - **Late Threshold Time**: Time after which attendance is classified as LATE (e.g., `08:31`).
  - **QR Code Refresh Rate**: Interval in seconds for rolling QR payload updates (default: `5` seconds).
  - **Network Enforcement**: Toggle restriction requiring check-ins from campus Wi-Fi / IP ranges.

---

## 6. Facilitator Guide

### 6.1 Facilitator Dashboard (`/facilitator`)
- **Purpose**: Central hub for facilitators managing assigned cohorts. Displays assigned cohorts, today's attendance summary, and pending excuse requests.

### 6.2 QR Generation & Backup Code (`/facilitator/qr`)
- **Purpose**: Launch an interactive attendance session in the classroom.
- **Features**:
  - **Dynamic Rolling QR Code**: Visual QR code displayed on screen that auto-refreshes every 5 seconds.
  - **6-Digit Backup Attendance Code**: Displayed alongside the QR code for students with camera or hardware issues.
  - **Session Timer**: Live timer displaying remaining valid session time.

### 6.3 Live Attendance Feed (`/facilitator/live`)
- **Purpose**: Monitor student check-ins in real time during class.
- **Sorting Logic**:
  - **Tier 1**: Attended students (`PRESENT`, `LATE`) ordered by check-in timestamp **Ascending** (earliest check-in at the top).
  - **Tier 2**: Unattended / Absent students displayed below attended students, sorted **Alphabetically (A–Z)**.

### 6.4 Manual Attendance Override (`/facilitator/manual`)
- **Purpose**: Manually mark attendance for a student who cannot scan or enter a code.
- **Required Inputs**: Target Student, Status (`PRESENT`, `LATE`, `EXCUSED`), Override Reason (e.g., "Device camera damaged").
- **Audit Requirement**: All manual overrides are logged with facilitator ID and reason.

### 6.5 Facilitator Reports & Exports (`/facilitator/reports`)
- **Purpose**: View and export comprehensive attendance registers.
- **Search & Filters**: Filter by Cohort, Date, Status (`PRESENT`, `LATE`, `ABSENT`, `EXCUSED`), or search by student name/reg number.
- **Export Formats**: CSV and Excel (`.xlsx`).
- **Standardized Export Columns**:
  1. `Student Name`
  2. `Cohort`
  3. `Attendance Status`
  4. `Attendance Date`
  5. `Attendance Time`
  6. `Registration Number`
  7. `Excuse Status`
- **Formatting**: Dark blue centered bold headers, auto-sized columns, clean compatibility with Excel, LibreOffice Calc, and Google Sheets.

---

## 7. Student Guide

### 7.1 Student Registration & Verification (`/register`)
1. Visit the registration link and select your designated Cohort number (e.g., `29`).
2. Enter your Full Name, Email, Phone Number, and Password.
3. Upon registration, check your email for the verification link sent via Brevo.
4. Click the verification link to activate your account.

### 7.2 QR Scanning Check-In (`/student/scan`)
1. Log in to your student portal on your primary mobile device.
2. Allow camera permissions when prompted.
3. Align your camera with the dynamic QR code displayed on the classroom screen.
4. Upon successful scan, the system confirms your check-in time and status (`PRESENT` or `LATE`).

### 7.3 Backup Attendance Code Check-In (`/student/code`)
1. If your mobile camera is damaged or unable to scan, click **"Use Attendance Code"**.
2. Enter the 6-character alphanumeric backup code displayed by the facilitator.
3. Click **Submit** to register your check-in.

### 7.4 Submitting Excuse Requests (`/student/excuses`)
1. Go to the **Excuse Requests** tab in your portal.
2. Select Start Date, Number of Days, Category (`Medical`, `Personal`, `Official`), and provide a brief explanation.
3. Track approval status (`PENDING`, `APPROVED`, `REJECTED`). When approved, affected days are marked as `EXCUSED`.

---

## 8. Attendance Rules & Business Logic

### Working Days vs. Excluded Days
- **Scheduled Working Days**: Monday through Friday.
- **Excluded Days**: Saturdays, Sundays, configured Public Holidays, and Custom School Vacations.

### Attendance Status Criteria

| Status | Code Criteria / Condition | Impact on Attendance Rate |
| --- | --- | --- |
| **PRESENT** | Check-in timestamp recorded between Session Start (07:00) and Late Threshold (08:30:59). | Counts as Attended (+1) |
| **LATE** | Check-in timestamp recorded at or after Late Threshold (08:31:00) until Session End (12:00). | Counts as Attended (+1) |
| **ABSENT** | Working day concluded with no check-in recorded and no approved excuse request. | Counts as Missed (0) |
| **EXCUSED** | Student has an approved excuse request covering the specified date. | Subtracted from Total Working Days |
| **HOLIDAY** | Date configured as a holiday in the system calendar. | Excluded from calculations |
| **WEEKEND** | Saturday or Sunday. | Excluded from calculations |

### Standardized Attendance Rate Formula

$$\text{Attendance Rate (\%)} = \left( \frac{\text{Present Count} + \text{Late Count}}{\text{Total Scheduled Working Days} - \text{Approved Excused Days}} \right) \times 100$$

> [!IMPORTANT]
> Excused days reduce the denominator so students are not penalized for authorized medical or official leave.

---

## 9. Behaviour Analytics & Rating Model

### Behaviour Score Calculation
The system evaluates student punctuality and consistency over time using a composite 100-point scoring model:

- **Punctuality Ratio**: Weighting for arriving on time (`PRESENT`) versus arriving `LATE`.
- **Absence Penalty**: Deductions for unexcused `ABSENT` days.
- **Streak Factor**: Bonus points for consecutive attended days without tardiness.

### Student Rating Tiers

| Score Range | Rating Tier | Visual Badge | Guidance / Action |
| --- | --- | --- | --- |
| 90% – 100% | **EXCELLENT** | 🟢 Emerald Green | Outstanding commitment & punctuality |
| 75% – 89% | **GOOD** | 🔵 Blue | Solid attendance performance |
| 60% – 74% | **AVERAGE** | 🟡 Amber Yellow | Attendance monitor warning |
| 45% – 59% | **POOR** | 🟠 Orange | Facilitator intervention recommended |
| 0% – 44% | **CRITICAL** | 🔴 Crimson Red | Administrator review required |

---

## 10. Reports & Export Standards

### Data Formatting Guidelines
All exported reports follow strict administrative formatting standards:
- **Encoding**: UTF-8 with Byte Order Mark (BOM) to ensure instant character display in Microsoft Excel.
- **Excel Styling**: Header row formatted in **Bold White Text** with `IndexedColors.DARK_BLUE` background fill, centered alignment, and padding buffers on all columns.

### Export Header Specification

```csv
Student Name,Cohort,Attendance Status,Attendance Date,Attendance Time,Registration Number,Excuse Status
Tunde Adeyemi,Cohort 29,PRESENT,2026-08-06,08:01:15,TS-2024-0003,N/A
Emeka Nwosu,Cohort 29,PRESENT,2026-08-06,08:03:42,TS-2024-0002,N/A
Dayo Bello,Cohort 29,LATE,2026-08-06,08:07:19,TS-2024-0006,N/A
Ada Okafor,Cohort 29,ABSENT,2026-08-06,—,TS-2024-0001,N/A
```

---

## 11. Device Management & Security Binding

To prevent proxy attendance where a student checks in for absent classmates using their phones:
1. **Device Binding on First Login**: Upon first login on a mobile device, the application registers a unique device hardware fingerprint.
2. **Single Device Limit**: A student account is bound to exactly **one device fingerprint**.
3. **Mismatch Prevention**: Attempting to log in or scan from a secondary device blocks the check-in and triggers a security alert.
4. **Facilitator Reset**: If a student changes or replaces their phone, an Administrator or Facilitator must click **"Unlock Device"** in the Device Management console.

---

## 12. Calendar & Holiday Management

Administrators can add, update, or remove school calendar entries.

```
                  [ Calendar Engine ]
                           │
       ┌───────────────────┴───────────────────┐
       ▼                                       ▼
[ Weekend Check ]                       [ Holiday Check ]
Is Day = Sat/Sun?                     Matches System Holiday?
       │                                       │
       ├── YES ──► Exclude                     ├── YES ──► Exclude
       └── NO  ──► Evaluate as Working Day    └── NO  ──► Include in Working Days
```

---

## 13. Email Verification & Brevo Integration

QRAttendance integrates with **Brevo SMTP** for transactional email delivery:
- **Trigger**: Account creation (Student self-registration or Administrator registration).
- **Verification Email**: Contains a unique cryptographic link valid for 24 hours.
- **Login Enforcement**: Unverified accounts cannot log in until email ownership is confirmed.

---

## 14. Frequently Asked Questions (FAQs)

#### Q1: What should I do if my phone camera will not scan the QR code?
**Answer**: Ask your facilitator for the 6-character backup Attendance Code displayed on the classroom screen, click **"Use Attendance Code"** on your portal, and type the code.

#### Q2: Why does my login say "User does not exist. Kindly register below."?
**Answer**: This message indicates that no account with that email address exists in the system. Check for typos or complete the self-registration form.

#### Q3: Why am I marked ABSENT even though I was in class?
**Answer**: Attendance must be marked within the active QR session window. If you arrived after the window closed, contact your facilitator for a manual override.

#### Q4: What happens if I get a new mobile phone?
**Answer**: Inform your facilitator or administrator. They will reset your bound device fingerprint from the Device Management dashboard so you can bind your new phone.

---

## 15. Troubleshooting Guide

| Issue / Error | Probable Cause | Recommended Resolution |
| --- | --- | --- |
| **"User does not exist. Kindly register below."** | Unregistered email address used at login. | Re-check email spelling or proceed to `/register`. |
| **"Invalid email or password"** | Incorrect password for existing account. | Use password reset or re-type password carefully. |
| **"Device mismatch" Error** | Account logged in from a second phone. | Request facilitator to unlock device in `/admin/devices`. |
| **Camera Feed Black / Blank** | Browser camera permissions denied. | Open browser settings $\rightarrow$ Site Permissions $\rightarrow$ Allow Camera. |
| **Slow Initial Page Load** | Cloud free-tier cold start. | Wait 15–30 seconds for server instance awakening. |

---

## 16. Deployment Guide

### Prerequisites
- JDK 21 (Eclipse Temurin or OpenJDK)
- Node.js 18+ & npm
- MongoDB 7.0+
- Maven 3.9+

### Backend Environment Variables (`application.properties` / `.env`)

```ini
server.port=8080
server.servlet.context-path=/api

# MongoDB Configuration
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/qrs_db

# Security & JWT
JWT_SECRET=dev_secret_change_in_production_min_256_bits_long
JWT_EXPIRY_MS=86400000

# CORS Allowed Origins
ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000

# Brevo SMTP Configuration
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_brevo_email@domain.com
SPRING_MAIL_PASSWORD=your_brevo_smtp_key
```

### Docker Compose Deployment
To launch the full containerized stack (MongoDB, Redis, Spring Boot Backend, React Frontend, Nginx Reverse Proxy):

```bash
docker-compose up -d --build
```

---

## 17. System Architecture

```
┌───────────────────────────────────────────────────────────┐
│                    React 18 Frontend                      │
│            (Vite, TailwindCSS, Lucide Icons)              │
└─────────────────────────────┬─────────────────────────────┘
                              │ REST API / JSON (Port 8080)
┌─────────────────────────────▼─────────────────────────────┐
│                 Spring Boot 3 Backend                     │
│      (Spring Security, JWT Filter, Audit Logger)          │
└──────────────┬─────────────────────────────┬──────────────┘
               │                             │
┌──────────────▼──────────────┐  ┌───────────▼──────────────┐
│       MongoDB 7.0 DB        │  │     Brevo SMTP Service    │
│ (Users, Cohorts, Attendance)│  │ (Transactional Email)   │
└─────────────────────────────┘  └──────────────────────────┘
```

---

## 18. API Specification Documentation

### 18.1 Authentication Endpoints

#### `POST /api/auth/login`
- **Purpose**: Authenticate user credentials and issue JWT token.
- **Request Body**:
  ```json
  {
    "email": "james.obi@techschool.edu",
    "password": "Fac@1234"
  }
  ```
- **Responses**:
  - `200 OK`: Returns JWT token, user role, user ID, name, email, and cohort ID.
  - `401 Unauthorized`: Returns `"User does not exist. Kindly register below."` (if email not found) or `"Invalid email or password"` (if password incorrect).

#### `POST /api/auth/register/student`
- **Purpose**: Self-registration endpoint for new students.
- **Request Body**: Name, Email, Phone, Password, Cohort Number.
- **Response**: `200 OK` with JWT token and student record.

---

### 18.2 Facilitator & Report Endpoints

#### `GET /api/facilitator/attendance/reports`
- **Purpose**: Fetch paginated attendance report rows for assigned cohorts.
- **Parameters**: `cohortId`, `date` (`YYYY-MM-DD`), `q` (search query), `status`, `page`, `size`.
- **Response**: `PageResponse` containing two-tier sorted attendance records.

#### `GET /api/facilitator/attendance/reports/export`
- **Purpose**: Download formatted CSV or Excel attendance report.
- **Parameters**: `cohortId`, `date`, `q`, `status`, `format` (`csv` or `xlsx`).
- **Response**: File download stream formatted with 7 standard columns.

---

## 19. Database Schema Documentation

### 19.1 `users` Collection

| Field Name | Type | Description | Index |
| --- | --- | --- | --- |
| `_id` | ObjectId | Primary Key | Default |
| `name` | String | User full name | Text Index |
| `email` | String | Unique login email | Unique Index |
| `passwordHash` | String | BCrypt encrypted password | — |
| `role` | Enum | `SUPER_ADMIN`, `FACILITATOR`, `STUDENT` | Indexed |
| `cohortId` | String | Cohort reference ID for students | Indexed |
| `assignedCohortIds` | Array | List of cohort IDs assigned to facilitator | — |
| `active` | Boolean | Account active status flag | Indexed |

### 19.2 `attendance` Collection

| Field Name | Type | Description | Index |
| --- | --- | --- | --- |
| `_id` | ObjectId | Primary Key | Default |
| `studentId` | String | Reference to `users._id` | Compound Index |
| `cohortId` | String | Reference to `cohorts._id` | Compound Index |
| `date` | LocalDate | Attendance date (`YYYY-MM-DD`) | Compound Index |
| `markedAt` | Instant | Exact check-in timestamp (UTC) | Compound Index |
| `status` | Enum | `PRESENT`, `LATE`, `ABSENT`, `EXCUSED` | Indexed |
| `manual` | Boolean | Override flag | — |
| `manualReason` | String | Facilitator override notes | — |

---

## 20. System Maintenance Guide

### Automated Database Backups
Execute daily MongoDB dump scripts:

```bash
mongodump --db=qrs_db --out=/backups/qrs_db_$(date +%Y%m%d)
```

### Log Rotation & Monitoring
Spring Boot application logs are formatted in JSON/Standard layout and written to system stdout. Inspect application logs via Docker:

```bash
docker logs -f --tail=200 qrs_backend
```

---

## 21. Best Practices

### For Administrators
- Review Audit Logs weekly for unauthorized override attempts or device unlocking spikes.
- Configure public holidays in advance before academic terms begin.

### For Facilitators
- Keep the QR Generation screen visible to the classroom during the entry window.
- Verify manual override requests against physical attendance or official documentation.

### For Students
- Maintain single-device usage to avoid lockout errors.
- Submit excuse requests at least 24 hours prior to planned absences when possible.

---

## 22. Security Model

- **Stateless Authentication**: Signed JWT tokens containing user ID, role, and expiration timestamp.
- **Anti-Replay QR Security**: Rolling dynamic QR payload with cryptographic signature refreshed every 5 seconds.
- **Password Security**: BCrypt password hashing with high salt work factors.
- **Audit Logging**: Comprehensive non-repudiation event tracking.

---

## 23. Credits & Licensing

- **Built By**: Victor Olaomo (IamByte)
- **Application Name**: QRAttendance System
- **Core Stack**: React, Spring Boot 3, MongoDB 7, Brevo SMTP, Apache POI
- **Copyright**: © 2026 QRAttendance. All rights reserved.
