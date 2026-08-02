// mongo-init.js — runs once on first container start
db = db.getSiblingDB('qrs_db');

db.createCollection('users');
db.createCollection('cohorts');
db.createCollection('devices');
db.createCollection('qr_sessions');
db.createCollection('attendance');
db.createCollection('audit_logs');

print('✓ QRS database initialized');
