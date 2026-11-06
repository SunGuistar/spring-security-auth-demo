# Spring Security Auth Demo

A minimal REST API project demonstrating **session-based authentication** with
Spring Boot 3.2 and Spring Security 6: login with username/password, a protected
endpoint, CSRF protection, and logout. No database, no JWT — users are stored
in memory and passwords are hashed with BCrypt.

## Project Overview

This project implements:

| Endpoint | Method | Auth Required | Description |
|---|---|---|---|
| `/api/hello` | GET | Yes | Returns `Hello World`; 401 when not logged in |
| `/api/login` | POST | No | JSON username/password login; creates a session |
| `/api/csrf` | GET | No | Returns the current CSRF token (also set as `XSRF-TOKEN` cookie) |
| `/api/logout` | POST | Yes | Invalidates the session and clears authentication |

Authentication state is kept in an HTTP session (cookie `SESSION`). All POST
requests (login/logout) require a valid CSRF token, sent via the
`X-CSRF-TOKEN` header — Spring Security's CSRF protection is **enabled**.

## Technology Stack

- Java 17
- Spring Boot 3.2.0 (exact version)
- Spring Security 6
- Maven
- Spring Web / Spring Boot Test / Spring Security Test
- JUnit 5 + MockMvc for integration tests

## Project Structure

```
src
├── main
│   ├── java/com/example/authdemo
│   │   ├── AuthDemoApplication.java          # Spring Boot entry point
│   │   ├── config
│   │   │   └── SecurityConfig.java           # SecurityFilterChain, in-memory users, BCrypt, AuthenticationManager
│   │   ├── controller
│   │   │   ├── AuthController.java           # POST /api/login, POST /api/logout
│   │   │   ├── CsrfController.java           # GET /api/csrf
│   │   │   └── HelloController.java          # GET /api/hello
│   │   ├── dto
│   │   │   ├── LoginRequest.java             # {username, password}
│   │   │   └── ApiResponse.java              # {status, message}
│   │   ├── security
│   │   │   └── JsonAuthenticationEntryPoint.java  # JSON 401 instead of login-page redirect
│   │   └── service
│   │       └── AuthService.java              # AuthenticationManager + SecurityContext persistence
│   └── resources
│       └── application.properties
└── test
    └── java/com/example/authdemo
        └── AuthIntegrationTest.java          # Integration tests with MockMvc
```

Key points of the security implementation (`SecurityConfig.java`):

- `SecurityFilterChain` (modern Spring Security 6 style, no `WebSecurityConfigurerAdapter`).
- `InMemoryUserDetailsManager` for the single test user.
- `BCryptPasswordEncoder` — the password is stored as a BCrypt hash, never in plain text.
- `AuthenticationManager` bean used by `AuthService.login()`.
- After a successful authentication, the `SecurityContext` is explicitly saved via
  `SecurityContextRepository` (`HttpSessionSecurityContextRepository`). Setting only
  `SecurityContextHolder.getContext().setAuthentication(...)` is **not enough** in
  Spring Security 6 — without `securityContextRepository.saveContext(...)` the session
  would not retain the login state.
- `CookieCsrfTokenRepository` keeps CSRF tokens in the `XSRF-TOKEN` cookie.
- A custom `AuthenticationEntryPoint` returns HTTP 401 JSON for unauthenticated
  requests instead of redirecting to `/login`.

## Prerequisites

- JDK 17+
- Maven 3.6+

## How to Run

```bash
mvn clean spring-boot:run
```

or build and run the jar:

```bash
mvn clean package
java -jar target/spring-security-auth-demo-1.0.0.jar
```

The application listens on `http://localhost:8080`.

## API Documentation

### 1. GET /api/hello

Protected endpoint.

- **Without session:** `401 Unauthorized`

```json
{"status":401,"error":"Unauthorized","message":"Authentication required"}
```

- **With session:** `200 OK`, body: `Hello World`

### 2. POST /api/login

Public endpoint. Requires a CSRF token (see `GET /api/csrf`).

**Request:**

```http
POST /api/login
Content-Type: application/json
X-CSRF-TOKEN: <csrf-token>

{"username":"test","password":"123456"}
```

**Success `200 OK`:**

```json
{"status":200,"message":"Login successful"}
```

**Bad credentials `401 Unauthorized`:**

```json
{"status":401,"message":"Invalid username or password"}
```

A session cookie (`SESSION`) is returned. Send it on subsequent requests.

### 3. GET /api/csrf

Public endpoint. Forces generation of a CSRF token, stores it in the
`XSRF-TOKEN` cookie and returns it as JSON:

```json
{"headerName":"X-CSRF-TOKEN","parameterName":"_csrf","token":"..."}
```

Send the token back in the `X-CSRF-TOKEN` header on every POST request.

### 4. POST /api/logout

Requires authentication **and** a CSRF token. Invalidates the session and clears
the security context.

**Success `200 OK`:**

```json
{"status":200,"message":"Logout successful"}
```

After logout, `GET /api/hello` returns `401` again.

## Test Credentials

| Username | Password |
|---|---|
| `test` | `123456` |

The password is stored in memory as a BCrypt hash via `BCryptPasswordEncoder`.

## How to Test

### Run the automated test suite

```bash
mvn clean test
```

`AuthIntegrationTest` covers: anonymous access → 401, wrong password → 401,
correct login → 200, session-based access to `/api/hello` → `Hello World`,
logout → 200, access after logout → 401, and CSRF-less POST → 403.

> Note: `spring-security-test` decorates the `CsrfFilter` repository in MockMvc
> once the `csrf()` post-processor is used, so the integration test only asserts
> that `/api/csrf` returns a token. The runtime header is `X-XSRF-TOKEN`, as
> verified with the curl commands below.

### Manual end-to-end test with curl (Git Bash / Linux / macOS)

The commands below save cookies (session + CSRF) in `cookies.txt` and reuse them.

**1. Get a CSRF token and save cookies:**

```bash
curl -s -c cookies.txt -b cookies.txt http://localhost:8080/api/csrf
```

Extract the token from the JSON and store it in a variable:

```bash
CSRF_TOKEN=$(curl -s -c cookies.txt -b cookies.txt http://localhost:8080/api/csrf \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "$CSRF_TOKEN"
```

**2. Verify /api/hello is protected (expect 401):**

```bash
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/hello
```

**3. Log in with the test account (expect 200):**

```bash
curl -s -w "\nHTTP %{http_code}\n" -c cookies.txt -b cookies.txt \
  -H "Content-Type: application/json" \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
  -d '{"username":"test","password":"123456"}' \
  http://localhost:8080/api/login
```

**4. Access /api/hello reusing the session cookie (expect `Hello World`, 200):**

```bash
curl -s -w "\nHTTP %{http_code}\n" -b cookies.txt http://localhost:8080/api/hello
```

**5. Log out (expect 200):**

```bash
curl -s -w "\nHTTP %{http_code}\n" -b cookies.txt \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
  -X POST http://localhost:8080/api/logout
```

**6. Verify access is denied after logout (expect 401):**

```bash
curl -s -w "\nHTTP %{http_code}\n" -b cookies.txt http://localhost:8080/api/hello
```

**7. Bonus: CSRF is enforced — POST /api/logout without the token header is rejected (expect 403):**

```bash
curl -s -w "\nHTTP %{http_code}\n" -b cookies.txt -X POST http://localhost:8080/api/logout
```

### Manual end-to-end test with Windows PowerShell

PowerShell has no `cookies.txt`; use a `WebRequestSession` object, which holds
the cookies for you. Run the following commands **in order in one session**:

```powershell
# 1. Get a CSRF token and keep the session (cookies are stored in $session)
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri http://localhost:8080/api/csrf -WebSession $session
$headers = @{ "X-CSRF-TOKEN" = $csrf.token }

# 2. Verify /api/hello is protected (expect 401)
try { Invoke-RestMethod -Uri http://localhost:8080/api/hello -WebSession $session }
catch { "HTTP " + [int]$_.Exception.Response.StatusCode }

# 3. Log in (expect status 200 / "Login successful")
Invoke-RestMethod -Uri http://localhost:8080/api/login -Method Post -WebSession $session `
    -Headers $headers -ContentType "application/json" `
    -Body '{"username":"test","password":"123456"}'

# 4. Access /api/hello reusing the session (expect "Hello World")
Invoke-RestMethod -Uri http://localhost:8080/api/hello -WebSession $session

# 5. Log out (expect "Logout successful")
Invoke-RestMethod -Uri http://localhost:8080/api/logout -Method Post -WebSession $session -Headers $headers

# 6. Verify access is denied after logout (expect 401)
try { Invoke-RestMethod -Uri http://localhost:8080/api/hello -WebSession $session }
catch { "HTTP " + [int]$_.Exception.Response.StatusCode }
```

> Tip: in PowerShell 7+ you can also use `curl.exe` (not the `curl` alias, which
> is `Invoke-WebRequest`) together with `-c`/`-b` cookie files exactly like Git Bash.
