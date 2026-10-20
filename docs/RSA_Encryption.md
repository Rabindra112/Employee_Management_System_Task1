# Employee Management System — RSA Password Encryption

**Work date:** 19 October 2026  
**Project:** Employee Management System  
**Stack:** Java, Spring Boot 3.5.5, Spring Security, JWT, BCrypt, React, MySQL, OpenSSL

## 1. Overview

Today's work focused on adding RSA-OAEP encryption to the login and signup request payloads. The React frontend retrieves the server's RSA public key, encrypts password fields, and sends Base64-encoded ciphertext to Spring Boot. The backend decrypts the fields using the RSA private key, then performs normal password verification or BCrypt hashing.

> **Security:** This additional browser-side encryption does **not** replace HTTPS. It does not hide passwords from the browser owner or malicious scripts, and encrypted requests may be replayed without further controls. Always use HTTPS, BCrypt, rate limiting, and secure key management.

## 2. Goals

- Generate a 3072-bit RSA key pair using OpenSSL.
- Store key files securely and exclude them from Git.
- Configure the backend to load the key files.
- Expose `GET /api/auth/public-key` without requiring login.
- Encrypt login and signup passwords in React with RSA-OAEP SHA-256.
- Decrypt passwords in Spring Boot before BCrypt verification or hashing.
- Preserve existing JWT authentication, login notifications, and protected APIs.
- Diagnose build, key-path, and HTTP 401 errors.

## 3. RSA Key Generation

Open **Git Bash** and run:

```bash
openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:3072
openssl pkey -in private_key.pem -pubout -out public_key.pem
```

Generated files:

| File | Purpose | Visibility |
| --- | --- | --- |
| `private_key.pem` | Backend decryption | **Secret; backend only** |
| `public_key.pem` | Browser encryption | May be shared publicly |

The key files were generated and their existence verified. Do not paste the private key into source code, chat, logs, or screenshots.

## 4. Project Structure

```text
Employee_Management_System_Task1/
├── backend/
│   └── src/main/
│       ├── java/com/example/ems/
│       │   ├── config/SecurityConfig.java
│       │   ├── controller/AuthController.java
│       │   └── security/RsaService.java
│       └── resources/application.properties
├── frontend/
│   └── src/
│       ├── AuthApp.jsx
│       └── utils/rsaEncryption.js
├── docs/
├── secrets/
│   ├── private_key.pem
│   └── public_key.pem
└── .gitignore
```

Add this to the root `.gitignore`:

```gitignore
/secrets/
*.pem
```

**Note:** `.gitignore` does not untrack previously committed files. If a private key was committed or exposed, rotate it and remove it from Git history using an appropriate remediation process.

## 5. Spring Boot Configuration

In `backend/src/main/resources/application.properties`, prefer environment variables:

```properties
app.rsa.private-key-path=${RSA_PRIVATE_KEY_PATH}
app.rsa.public-key-path=${RSA_PUBLIC_KEY_PATH}
```

For local development, absolute paths also work:

```properties
app.rsa.private-key-path=C:/Users/III S I/Desktop/Employee_Management_System_Task1/secrets/private_key.pem
app.rsa.public-key-path=C:/Users/III S I/Desktop/Employee_Management_System_Task1/secrets/public_key.pem
```

Adjust the paths to match your machine. Relative paths such as `../secrets/private_key.pem` only work when the application's **working directory** is `backend`. IntelliJ run configurations can use a different working directory.

## 6. Backend Changes

### 6.1 RSA Service

**File:** `backend/src/main/java/com/example/ems/security/RsaService.java`

The service loads PEM-encoded RSA keys, provides the Base64-encoded public key, and decrypts Base64 ciphertext using **RSA-OAEP with SHA-256**. The frontend and backend must agree on OAEP and MGF1 hash parameters.

### 6.2 Public Key Endpoint

**File:** `backend/src/main/java/com/example/ems/controller/AuthController.java`

```java
@GetMapping("/public-key")
public Map<String, String> getPublicKey() {
    return Map.of("publicKey", rsaService.getPublicKey());
}
```

With the controller's `@RequestMapping("/api/auth")`, the endpoint is:

```http
GET /api/auth/public-key
```

Example response (illustrative, truncated):

```json
{
  "publicKey": "MIIB..."
}
```

### 6.3 Login and Signup

The login DTO accepts `email` and `encryptedPassword`. Signup accepts `fullName`, `email`, `encryptedPassword`, and `encryptedConfirmPassword`.

For login, the backend decrypts the password and passes the plaintext value to Spring Security's authentication manager, which checks the stored BCrypt hash. Upon successful authentication, the application issues a JWT.

For signup, the backend decrypts both password fields, validates equality and password requirements, hashes the password with BCrypt, and stores **only the hash**.

## 7. React Changes

### 7.1 Encryption Utility

**File:** `frontend/src/utils/rsaEncryption.js`

The utility fetches `/api/auth/public-key`, imports the SPKI public key using `window.crypto.subtle.importKey`, encrypts with RSA-OAEP SHA-256, and returns Base64 ciphertext.

### 7.2 AuthApp.jsx

**File:** `frontend/src/AuthApp.jsx`

Login example:

```javascript
const encryptedPassword = await encryptPassword(form.password);

const response = await login({
  email: form.email.trim().toLowerCase(),
  encryptedPassword
});
```

Signup example:

```javascript
if (form.password !== form.confirmPassword) {
  throw new Error('Passwords do not match');
}

const encryptedPassword = await encryptPassword(form.password);
const encryptedConfirmPassword = await encryptPassword(form.confirmPassword);

await signup({
  fullName: form.fullName.trim(),
  email: form.email.trim().toLowerCase(),
  encryptedPassword,
  encryptedConfirmPassword
});
```

## 8. Spring Security Fix

**File:** `backend/src/main/java/com/example/ems/config/SecurityConfig.java`

A 401 error occurred because the public-key endpoint was included in a matcher limited to `POST`, while the frontend uses `GET`.

Correct authorization configuration:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        HttpMethod.POST,
        "/api/auth/login",
        "/api/auth/signup"
    ).permitAll()
    .requestMatchers(
        HttpMethod.GET,
        "/api/auth/public-key"
    ).permitAll()
    .anyRequest().authenticated()
)
```

Keep any existing role-based rules for employee APIs in the correct order before `.anyRequest().authenticated()`.

## 9. Authentication Flow

```text
React login/signup form
        |
        v
GET /api/auth/public-key
        |
        v
RSA public key returned
        |
        v
Browser encrypts password (RSA-OAEP SHA-256)
        |
        v
POST /api/auth/login or /api/auth/signup
        |
        v
Spring Boot decrypts using private key
        |
        +--> Login: verify against BCrypt hash --> issue JWT
        |
        +--> Signup: validate --> BCrypt hash --> save user
```

## 10. Troubleshooting Performed

| Issue | Observation | Action / Resolution |
| --- | --- | --- |
| OpenSSL unavailable in CMD | `'openssl' is not recognized` | Used Git Bash, which had OpenSSL installed. |
| Invalid compiled class | `ClassFormatError` for `User` | Maven clean succeeded; subsequent logs progressed beyond this error. |
| RSA key not found | `NoSuchFileException: ..\\secrets\\private_key.pem` | Identified relative-path/working-directory mismatch; use a verified absolute path or correct working directory. |
| Public key returned 401 | `GET /api/auth/public-key` returned Unauthorized | Corrected Spring Security matcher to allow `GET`, not `POST`, for this endpoint. |

Useful Maven commands:

```bash
mvn clean
mvn compile
```

## 11. Verification Checklist

- [x] Confirm OpenSSL is available in Git Bash.
- [x] Generate RSA private and public keys.
- [x] Verify both generated key files exist.
- [x] Run Maven clean successfully.
- [x] Identify missing-key path exception.
- [x] Identify cause of public-key endpoint HTTP 401.
- [ ] Confirm copied keys are present in the project's `secrets/` directory.
- [ ] Confirm the application starts with the final key paths.
- [ ] Confirm `GET /api/auth/public-key` returns HTTP 200.
- [ ] Confirm signup succeeds with encrypted fields.
- [ ] Confirm login succeeds with encrypted fields.
- [ ] Confirm JWT-protected dashboard access and logout work.
- [ ] Confirm the private key is untracked by Git.

The unchecked items require final verification; they should not be reported as passed until tested.

### Expected Network Payload

```json
{
  "email": "user@example.com",
  "encryptedPassword": "BASE64_RSA_CIPHERTEXT"
}
```

## 12. Next Steps

1. Finish verifying the RSA key paths and application startup.
2. Retest the public-key endpoint after the Spring Security change.
3. Test successful and failed login/signup requests.
4. Test protected API access with and without JWT.
5. Review replay protection, authentication rate limits, HTTPS deployment, and key rotation procedures before production use.


