# Employee Management System

A REST API and web app for managing employees, departments, attendance and leave requests, with JWT
authentication and role-based access for **Admin**, **HR** and **Employee** users.

**Stack:** Java 17+, Spring Boot 3.5, Spring Security (stateless JWT, BCrypt), Spring Data JPA / Hibernate,
Bean Validation, MySQL (production) / H2 (zero-setup local dev), Maven, JUnit 5 + Mockito + MockMvc.

## Features

- **Employees**: full CRUD, search by name/email (`q`), department, status, job title and hire-date range,
  with pagination and sorting.
- **Departments**: CRUD. Names are unique; a department can't be deleted while it still has employees.
- **Attendance**: employees check in and out once a day. A check-in after 09:30 counts as `LATE` (set
  `app.attendance.late-after` to change it). HR can add or correct records by hand.
- **Leave**: employees submit and cancel their own requests. HR/Admin approve or reject them. The API
  blocks overlapping requests, start dates in the past, requests over 90 days and reviewing your own request.
- **Dashboard**: headcount by department, today's attendance (present / late / checked out / not checked
  in), number of pending leave requests and number of employees on leave today.
- **Users**: Admin creates login accounts, links them to employee records, changes roles, disables
  accounts and resets passwords.
- **Consistent errors**: every failure returns the same JSON shape (see [Error format](#error-format)).
- **Web UI**: a single-page app served by the same server at `http://localhost:8080` (see [Web UI](#web-ui)).

## Web UI

Open `http://localhost:8080` after starting the app, then sign in or click one of the demo account buttons.
The menu adapts to your role:

| Page | Who | What you can do |
|---|---|---|
| Dashboard | Admin, HR | Headcount, today's attendance, pending leave, headcount by department |
| Employees | Admin, HR | Search, filter by department and status, sort, page through results, add, edit, delete |
| Departments | Everyone (Admin edits) | List, add, rename, delete empty departments |
| Attendance | Admin, HR | Filter records by employee, date and status; add or correct records |
| Leave requests | Admin, HR | Approve or reject pending requests with a comment |
| Users | Admin | Create logins, change roles, link employees, disable accounts, reset passwords |
| My attendance | Linked accounts | Check in, check out, view your history |
| My leave | Linked accounts | Request leave, cancel pending requests, track decisions |
| Profile | Everyone | View your details, change your password |

The UI is plain HTML, CSS and JavaScript in `src/main/resources/static`, with no build step and no
Node.js. It calls the same `/api` endpoints documented below, keeps the JWT in `sessionStorage` (so
closing the tab signs you out), shows validation errors next to the matching form fields, and works on
phone-sized screens.

## Running locally

### Prerequisites
- JDK 17 or newer (`java -version`). Set `JAVA_HOME` if `java` is not on your `PATH`.
- You don't need to install Maven: the bundled Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads it.

### Option A: in-memory H2 (no database setup)

```bash
# Windows
mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`. The `dev` profile is active by default. It uses an in-memory H2
database that is recreated with demo data on every start. The H2 console is at
`http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:ems`, user `sa`, no password).

To build a jar and run it instead:

```bash
mvnw.cmd clean package
java -jar target/employee-management-system-1.0.0.jar
```

### Option B: MySQL

Start MySQL 8 with Docker, or use your own server and create a database `ems` with user `ems`/`ems`:

```bash
docker compose up -d
```

Then run with the `mysql` profile:

```bash
# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE="mysql"; .\mvnw.cmd spring-boot:run
# macOS / Linux
SPRING_PROFILES_ACTIVE=mysql ./mvnw spring-boot:run
```

Hibernate creates or updates the schema on startup (`ddl-auto: update`). Demo data is only inserted into an empty database.

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2) or `mysql` |
| `DB_URL` | `jdbc:mysql://localhost:3306/ems?...` | MySQL JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `ems` / `ems` | MySQL credentials |
| `JWT_SECRET` | dev-only key | Base64 HMAC key, at least 256 bits. **Set this outside local dev.** |
| `JWT_EXPIRATION_MINUTES` | `480` | Token lifetime |
| `SEED_DATA` | `true` | Insert demo data on an empty database |
| `PORT` | `8080` | HTTP port |

## Demo accounts

| Role | Username | Password | Linked employee |
|---|---|---|---|
| ADMIN | `admin` | `Admin@123` | none (pure admin account) |
| HR | `hr` | `Hr@12345` | Priya Sharma, HR Manager |
| EMPLOYEE | `employee` | `Employee@123` | Arjun Mehta, Software Engineer |

The seed also creates 5 departments, 9 employees (one inactive), 10 weekdays of attendance history and a
few leave requests. These passwords are public. Change them or set `SEED_DATA=false` for any shared deployment.

## Using the API

Log in to get a token, then send it as `Authorization: Bearer <token>`:

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"hr","password":"Hr@12345"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 28800,
  "user": { "id": 2, "username": "hr", "role": "HR", "employeeId": 1, "employeeName": "Priya Sharma" }
}
```

```bash
TOKEN=...   # accessToken from above
curl -s "http://localhost:8080/api/employees?q=sharma&status=ACTIVE&page=0&size=10&sort=hireDate,desc" \
  -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
```

PowerShell equivalent:

```powershell
$login = Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login -ContentType 'application/json' -Body '{"username":"hr","password":"Hr@12345"}'
$h = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod http://localhost:8080/api/dashboard -Headers $h
```

### Endpoints and who can call them

| Method & path | Admin | HR | Employee | Notes |
|---|:-:|:-:|:-:|---|
| `POST /api/auth/login` | public | public | public | Returns a JWT |
| `GET /api/auth/me` | ✓ | ✓ | ✓ | Current user |
| `POST /api/auth/change-password` | ✓ | ✓ | ✓ | `{currentPassword, newPassword}` |
| `GET /api/users`, `GET /api/users/{id}` | ✓ | | | |
| `POST /api/users` | ✓ | | | `{username, password, role, employeeId?}` |
| `PUT /api/users/{id}` | ✓ | | | `{role, enabled, employeeId?}` |
| `PUT /api/users/{id}/password` | ✓ | | | Reset a password |
| `DELETE /api/users/{id}` | ✓ | | | You can't delete yourself |
| `GET /api/departments`, `GET /api/departments/{id}` | ✓ | ✓ | ✓ | |
| `POST/PUT/DELETE /api/departments[/{id}]` | ✓ | | | |
| `GET /api/employees` | ✓ | ✓ | | Search, see below |
| `GET /api/employees/{id}` | ✓ | ✓ | | |
| `GET /api/employees/me` | ✓* | ✓ | ✓ | Own profile |
| `POST/PUT/DELETE /api/employees[/{id}]` | ✓ | ✓ | | Delete also removes the employee's attendance and leave records and disables their login |
| `POST /api/attendance/check-in` / `check-out` | ✓* | ✓ | ✓ | Optional `{notes}` |
| `GET /api/attendance/me?from=&to=` | ✓* | ✓ | ✓ | Own history |
| `GET /api/attendance?employeeId=&from=&to=&status=` | ✓ | ✓ | | |
| `POST/PUT/DELETE /api/attendance[/{id}]` | ✓ | ✓ | | Manual entry or correction |
| `POST /api/leaves` | ✓* | ✓ | ✓ | `{type, startDate, endDate, reason}` |
| `GET /api/leaves/me?status=` | ✓* | ✓ | ✓ | Own requests |
| `POST /api/leaves/{id}/cancel` | ✓* | ✓ | ✓ | Own pending requests only |
| `GET /api/leaves?employeeId=&status=`, `GET /api/leaves/{id}` | ✓ | ✓ | | |
| `PUT /api/leaves/{id}/approve` / `reject` | ✓ | ✓ | | Optional `{comment}` |
| `GET /api/dashboard` | ✓ | ✓ | | Summary stats |

\* Self-service endpoints need the account to be linked to an employee record. The seeded `admin` account
isn't linked, so it gets a 403 with a clear message.

Every controller method has an explicit `@PreAuthorize` rule. `/api/users/**` and `/api/dashboard/**`
are also restricted at the URL level.

### Employee search

`GET /api/employees` takes these query parameters. All are optional and are combined with AND:

- `q`: matches first name, last name, full name or email (case-insensitive, partial match)
- `departmentId`, `status` (`ACTIVE` | `ON_LEAVE` | `INACTIVE`), `jobTitle` (partial match)
- `hiredFrom`, `hiredTo` (ISO dates)
- `page` (0-based), `size` (default 20, max 100), `sort=field,asc|desc`. You can pass `sort` more than
  once. Sortable fields: `id, firstName, lastName, email, jobTitle, salary, hireDate, status, department`.

Paginated responses look like:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 9, "totalPages": 1, "first": true, "last": true, "sort": "lastName: ASC,firstName: ASC" }
```

### Enums
- Leave type: `ANNUAL`, `SICK`, `CASUAL`, `UNPAID`
- Leave status: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`
- Attendance status: `PRESENT`, `LATE`, `HALF_DAY`, `ABSENT`

### Error format

```json
{
  "timestamp": "2026-11-19T18:21:57.614Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/employees",
  "fieldErrors": [
    { "field": "email", "message": "Email must be a valid email address" },
    { "field": "departmentId", "message": "Department is required" }
  ]
}
```

| Status | When |
|---|---|
| 400 | Validation errors, malformed JSON, bad query parameters, a broken business rule (for example checking out without checking in) |
| 401 | Missing, invalid or expired token, or wrong login |
| 403 | Your role isn't allowed to do this |
| 404 | Unknown id or route |
| 409 | Duplicate (email, username, department name, attendance day) or invalid state change (for example approving a request that was already decided) |

## Tests

```bash
mvnw.cmd verify
```

There are 43 tests:
- **Unit tests** (Mockito, fixed `Clock`): `AttendanceServiceTest`, `LeaveServiceTest`,
  `EmployeeServiceTest`, `JwtServiceTest`.
- **Integration tests** (full Spring context, H2, real JWT login through MockMvc):
  - `SecurityIntegrationTest`: the role matrix, 401 and 403 responses, login validation.
  - `ApiFlowIntegrationTest`: employee CRUD and validation, search/pagination/sorting, the leave
    lifecycle, check-in/out, manual attendance, dashboard numbers, department rules, user creation.
  - `FrontendIntegrationTest`: the web UI files are public while `/api` still requires a token.

## Project layout

```
src/main/java/com/ems
├── config/       Clock bean, demo DataSeeder
├── domain/       JPA entities and enums (Employee, Department, User, Attendance, LeaveRequest)
├── dto/          Request/response records with Bean Validation
├── exception/    ApiError, BusinessException, GlobalExceptionHandler
├── repository/   Spring Data repositories (+ Specifications for search)
├── security/     JWT service and filter, SecurityConfig, JSON 401/403 handler
├── service/      Business logic
└── web/          REST controllers

src/main/resources/static   Web UI (index.html, assets/app.js, assets/styles.css)
```
