#!/bin/bash
# ─────────────────────────────────────────────────────────
#  Tech School QR Attendance System — Deploy Script
# ─────────────────────────────────────────────────────────
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}"
echo "  ╔═══════════════════════════════════════╗"
echo "  ║  Tech School QR Attendance System     ║"
echo "  ║  Deployment Script v1.0               ║"
echo "  ╚═══════════════════════════════════════╝"
echo -e "${NC}"

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
  echo -e "${RED}✗ Docker is not running. Please start Docker Desktop.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"

# Check docker-compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
  echo -e "${RED}✗ docker-compose not found.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ docker-compose found${NC}"

# Create .env if missing
if [ ! -f ".env" ]; then
  echo -e "${YELLOW}⚠  .env not found — copying from .env.example${NC}"
  cp .env.example .env
  echo -e "${YELLOW}   Please edit .env and set a strong JWT_SECRET before going to production!${NC}"
fi

# Create nginx/ssl directory
mkdir -p nginx/ssl

# Stop existing containers gracefully
echo -e "\n${YELLOW}Stopping existing containers...${NC}"
docker-compose down --remove-orphans 2>/dev/null || true

# Build and start
echo -e "\n${YELLOW}Building and starting services (this may take 3-5 minutes on first run)...${NC}"
docker-compose up --build -d

# Wait for backend health
echo -e "\n${YELLOW}Waiting for backend to be healthy...${NC}"
for i in $(seq 1 30); do
  if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Backend is healthy${NC}"
    break
  fi
  if [ $i -eq 30 ]; then
    echo -e "${YELLOW}Backend is still starting up. Check: docker-compose logs backend${NC}"
  fi
  sleep 3
  echo -n "."
done

echo -e "\n"
echo -e "${GREEN}═══════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ Deployment complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════${NC}"
echo ""
echo -e "  App URL:      ${YELLOW}http://localhost${NC}"
echo -e "  API Health:   ${YELLOW}http://localhost/api/health${NC}"
echo ""
echo -e "  Demo accounts:"
echo -e "    Admin:       admin@techschool.edu / Admin@1234"
echo -e "    Facilitator: james.obi@techschool.edu / Fac@1234"
echo -e "    Student:     ada.okafor@techschool.edu / Student@1234"
echo ""
echo -e "  Useful commands:"
echo -e "    Logs:    ${YELLOW}docker-compose logs -f${NC}"
echo -e "    Stop:    ${YELLOW}docker-compose down${NC}"
echo -e "    Restart: ${YELLOW}docker-compose restart backend${NC}"
echo ""
