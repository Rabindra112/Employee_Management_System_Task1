# Employee Management System — Project Overview

**Prepared for:** Trainer review | **Project:** Employee Management System 

## Purpose
A React + Spring Boot + MySQL application for managing employees, with signup, login, JWT-protected endpoints, and a login-alert email service.

## Implemented functionality (verified in source)
- Create, retrieve, update and delete employee records.
- Case-insensitive name search and department filtering, paginated results ordered by descending employee ID.
- Dashboard statistics: employee count, unique department count, and total salary.
- Register users, log in, obtain JWT, and retrieve current user profile.
- Asynchronous successful-login email notification, configurable by application property.
- Request validation, centralized exception mapping, and unit/integration test source files.

## Technology
- Backend: Java 21, Spring Boot, Spring Security, OAuth2 JWT resource server, Spring Data JPA, Hibernate, Bean Validation, Lombok, Maven.
- Frontend: React, Vite, Axios, JavaScript, CSS.
- Storage: MySQL. Email: Spring Mail with SMTP.

## Scope and gaps
The **uploaded version does not implement an address field** in `Employee`, `EmployeeRequest`, or `EmployeeService`. It also does **not** implement role-based endpoint restrictions: every authenticated user can call employee endpoints. No document type/version history, file operations, or batch-job feature was identified in the supplied application source. These should not be claimed as delivered.


