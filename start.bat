@echo off
cd /d %~dp0
start "Backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080"
start "Frontend" cmd /k "cd /d %~dp0frontend && npm run dev"
