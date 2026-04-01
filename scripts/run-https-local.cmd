@echo off
setlocal
call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=https-local
