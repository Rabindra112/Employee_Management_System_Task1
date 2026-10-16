# Implementation Details

## Backend package layout
- `controller/AuthController.java`: `/api/auth/signup`, `/login`, `/me`; issues JWT; schedules login alert.
- `controller/EmployeeController.java`: employee CRUD, paginated search, stats; uses `@Valid`, `@Positive`, `@Min`, `@Max`.
- `service/EmployeeService.java`: create/update/delete/get/list/stats; transactional boundaries; trims names and departments.
- `service/EmailService.java`: async successful-login email using `JavaMailSender` and configurable enable flag.
- `config/SecurityConfig.java`: BCrypt, authentication manager, JWT HS256, CORS, stateless authenticated endpoints.
- `config/MailAsyncConfig.java`: async execution configuration for mail notifications.
- `dto/EmployeeRequest.java`: `name`, `department`, `salary` — **no address**.
- `dto/LoginRequest.java`: email/password.
- `dto/SignupRequest.java`: fullName/email/password/confirmPassword.
- `entity/Employee.java`: employees JPA table; IDENTITY id and 3 persisted attributes.
- `entity/User.java`: `app_users` JPA table; default role USER and createdAt.
- `repository/EmployeeRepository.java`: JPA persistence and derived search method.
- `repository/UserRepository.java`: lookup and duplicate-email checks.
- `exception/GlobalExceptionHandler.java`: standardized 400/404/500 and ResponseStatusException handling.

## Frontend layout
- `src/AuthApp.jsx`: login/signup form, session check via `/auth/me`, logout and dashboard selection.
- `src/App.jsx`: employee dashboard and CRUD interactions.
- `src/components/EmployeeForm.jsx`: employee data entry UI.
- `src/api/client.js`: Axios base URL, JWT injection, unauthorized-response event.
- `src/api/auth.js`: auth API functions.
- `src/api/employees.js`: employee API functions.
- `src/styles.css`: UI styling.

## Running locally
1. Create MySQL database with `database.sql`.
2. Supply environment settings: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FRONTEND_ORIGIN`, **secure JWT secret**, and SMTP variables if mail alerts enabled.
3. From `backend/`: `mvn spring-boot:run` (requires appropriate Java/Maven versions).
4. From `frontend/`: `npm install`, then `npm run dev`.
5. Backend default port 8080; frontend origin configured as 5174. Check Vite configuration for actual port.

## Source-backed implementation details
- Employee create responds 201; delete responds 204.
- Search matches partial name and department ignoring case; page sorted by ID descending.
- Stats compute aggregates in Java over `findAll()`.
- JWT expiry configured to 3600 seconds by default.
- Login email is nonblocking and delivery failures do not fail login.
- Frontend stores access token in `sessionStorage`, not an HTTP-only cookie.

