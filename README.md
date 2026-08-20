# Tech School QR Attendance System

A production-ready, full-stack attendance management system built with:

- **Backend**: Java 17 + Spring Boot 3 + Spring Security (JWT)
- **Database**: MongoDB 7
- **Frontend**: React 18 + Vite
- **Proxy**: Nginx
- **Deployment**: Docker Compose

---

## Quick Start (5 minutes)

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- Ports **80** and **27017** free on your machine

### 1. Clone / copy this project
```bash
# If you have git:
git clone <your-repo-url>
cd qr-attendance-system

# Or just place all files in a folder called qr-attendance-system
```

### 2. Configure environment
```bash
cp .env.example .env
# Edit .env and set a strong JWT_SECRET (required for production)
```

### 3. Start everything
```bash
docker-compose up --build
```

The first build takes ~3–5 minutes (downloads Java + Node images, compiles code).

### 4. Open the app
Visit **http://localhost** in your browser.


## Architecture

```
Browser
  │
  ▼
Nginx :80          ← reverse proxy + rate limiting
  ├── /api/*  →  Spring Boot :8080   ← REST API + JWT auth
  └── /*      →  React (serve) :3000 ← SPA frontend
                         │
                         ▼
                   MongoDB :27017     ← persistent data
```

---

## API Endpoints

### Auth
| Method | Path                        | Role   | Description           |
|--------|-----------------------------|--------|-----------------------|
| POST   | /api/auth/login             | Public | Login, returns JWT    |
| GET    | /api/auth/me                | Any    | Get current user      |
| POST   | /api/auth/change-password   | Any    | Change own password   |

### Admin (SUPER_ADMIN only)
| Method | Path                              | Description               |
|--------|-----------------------------------|---------------------------|
| GET    | /api/admin/users?role=STUDENT     | List students             |
| POST   | /api/admin/users                  | Create user               |
| PUT    | /api/admin/users/:id              | Update user               |
| POST   | /api/admin/users/reset-password   | Reset user password       |
| POST   | /api/admin/devices/register       | Register student device   |
| POST   | /api/admin/devices/unlock/:id     | Unlock student device     |
| GET    | /api/admin/cohorts                | List cohorts              |
| POST   | /api/admin/cohorts                | Create cohort             |
| PATCH  | /api/admin/cohorts/:id/toggle     | Toggle cohort active      |
| GET    | /api/admin/audit                  | Get audit logs            |
| GET    | /api/admin/analytics/school       | School-wide stats         |

### Facilitator
| Method | Path                                   | Description           |
|--------|----------------------------------------|-----------------------|
| POST   | /api/facilitator/qr/generate           | Generate QR session   |
| GET    | /api/facilitator/qr/active/:cohortId   | Get active QR         |
| POST   | /api/facilitator/qr/expire/:sessionId  | End QR session        |
| POST   | /api/facilitator/attendance/manual     | Manual attendance     |
| GET    | /api/facilitator/attendance/today/:id  | Today's summary       |
| GET    | /api/facilitator/cohorts               | My cohorts            |
| GET    | /api/facilitator/dashboard             | Dashboard stats       |

### Student
| Method | Path                          | Description                |
|--------|-------------------------------|----------------------------|
| POST   | /api/student/attendance/scan  | Scan QR and mark attendance|
| GET    | /api/student/attendance/history | Attendance history       |
| GET    | /api/student/dashboard        | Student dashboard stats    |
| POST   | /api/student/device/register  | Register own device        |

---

## Features

### Security
- JWT authentication with configurable expiry
- Role-based access control (STUDENT / FACILITATOR / SUPER_ADMIN)
- BCrypt password hashing (strength 12)
- QR tokens are cryptographically random (UUID + timestamp hex)
- One-time QR scan per student per day
- Device fingerprinting (browser fingerprint)
- HTTPS-ready (add SSL cert to nginx/ssl/)
- Rate limiting via Nginx

### Attendance Rules
- QR active window: **7:00 AM – 8:30 AM** (configurable via .env)
- Late threshold: **7:30 AM** (configurable)
- Auto-expire QR sessions via scheduled job (every minute)
- One attendance record per student per day (enforced at DB level)
- Manual attendance with mandatory reason + audit log

### Data & Reporting
- Full audit trail for all actions
- Export attendance as CSV (from facilitator reports page)
- Per-cohort daily summaries
- Per-student attendance history with rate calculation
- School-wide analytics dashboard with charts

---

## Production Deployment

### On a VPS / Ubuntu server

```bash
# 1. Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 2. Copy project files to server
scp -r ./qr-attendance-system user@your-server-ip:~/

# 3. Set production env
cd ~/qr-attendance-system
cp .env.example .env
nano .env   # Set strong JWT_SECRET and passwords

# 4. Start
docker-compose up -d --build

# 5. View logs
docker-compose logs -f backend
```

### Adding HTTPS (Let's Encrypt)

```bash
# Install certbot
sudo apt install certbot

# Get certificate
sudo certbot certonly --standalone -d yourdomain.com

# Copy to nginx ssl folder
sudo cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem ./nginx/ssl/cert.pem
sudo cp /etc/letsencrypt/live/yourdomain.com/privkey.pem ./nginx/ssl/key.pem

# Update nginx.conf to enable HTTPS block (uncomment the ssl server block)
```

---

## Useful Commands

```bash
# Start in background
docker-compose up -d --build

# Stop
docker-compose down

# View logs
docker-compose logs -f             # all services
docker-compose logs -f backend     # backend only

# Restart a service
docker-compose restart backend

# Access MongoDB shell
docker exec -it qrs_mongo mongosh -u admin -p secret123 --authenticationDatabase admin qrs_db

# Reset all data (⚠️ destructive)
docker-compose down -v
docker-compose up --build

# Backup MongoDB
docker exec qrs_mongo mongodump --uri="mongodb://admin:secret123@localhost:27017/qrs_db?authSource=admin" --out=/tmp/backup
docker cp qrs_mongo:/tmp/backup ./mongo-backup
```

---

## Project Structure

```
qr-attendance-system/
├── docker-compose.yml
├── .env.example
├── mongo-init.js
├── nginx/
│   └── nginx.conf
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/techschool/attendance/
│       ├── AttendanceApplication.java
│       ├── config/          SecurityConfig, DataSeeder
│       ├── controller/      AuthController, AdminController,
│       │                    FacilitatorController, StudentController
│       ├── dto/             AuthDto, UserDto, CohortDto, QrDto,
│       │                    AttendanceDto, DashboardDto
│       ├── exception/       AppException, GlobalExceptionHandler
│       ├── model/           User, Cohort, Device, QrSession,
│       │                    Attendance, AuditLog
│       ├── repository/      (all MongoDB repositories)
│       ├── security/        JwtUtils, JwtAuthFilter
│       └── service/         AuthService, UserService, QrService,
│                            AttendanceService, CohortService, AuditService
└── frontend/
    ├── Dockerfile
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.jsx
        ├── main.jsx
        ├── api/             client.js
        ├── context/         AuthContext.jsx
        ├── components/
        │   ├── common/      UI.jsx (Button, Card, Table, Modal…)
        │   └── layout/      AppLayout.jsx (sidebar, topbar)
        └── pages/
            ├── auth/        LoginPage.jsx
            ├── student/     StudentPages.jsx
            ├── facilitator/ FacilitatorPages.jsx
            └── admin/       AdminPages.jsx
```

---

## Troubleshooting

**Backend won't start**: Check MongoDB is healthy first — `docker-compose logs mongodb`

**"Port 80 already in use"**: Stop Apache/Nginx on the host: `sudo systemctl stop nginx`

**JWT errors after restart**: Ensure `JWT_SECRET` in `.env` hasn't changed between restarts

**QR not generating**: Ensure the cohort is active and no active session already exists for today

**Attendance blocked**: Check device is registered (locked=true) and network validation settings
