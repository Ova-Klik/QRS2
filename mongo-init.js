// mongo-init.js — runs once on first container start
db = db.getSiblingDB('qrs_db');

db.createCollection('users');
db.createCollection('cohorts');
db.createCollection('devices');
db.createCollection('qr_sessions');
db.createCollection('attendance');
db.createCollection('audit_logs');

// Indexes
db.users.createIndex({ email: 1 }, { unique: true });
db.users.createIndex({ role: 1 });
db.devices.createIndex({ studentId: 1 });
db.devices.createIndex({ fingerprint: 1 }, { unique: true, sparse: true });
db.qr_sessions.createIndex({ cohortId: 1, date: 1 });
db.qr_sessions.createIndex({ token: 1 }, { unique: true });
db.qr_sessions.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
db.attendance.createIndex({ studentId: 1, date: 1 }, { unique: true });
db.attendance.createIndex({ cohortId: 1, date: 1 });
db.audit_logs.createIndex({ createdAt: -1 });
db.audit_logs.createIndex({ actorId: 1 });

print('✓ QRS database initialized');
