# Authentication and Authorization

## Implementation
`SecurityConfig.java` configures `BCryptPasswordEncoder`, `UserDetailsService`, `DaoAuthenticationProvider`, JWT encoder/decoder, CORS, stateless sessions, and Spring Security's OAuth2 resource server.

## Public and protected routes
- Public: `POST /api/auth/signup`, `POST /api/auth/login`.
- Protected: **all other requests**, including `/api/auth/me` and every employee endpoint.
- **Current limitation:** no `.hasRole(...)`, `.hasAuthority(...)`, or method-level permission rule was found. Although `User.role` and JWT `role` claim exist, **role-based employee permissions are not enforced**. A logged-in `USER` can call create/update/delete unless other protections are added.

## Signup
`SignupRequest` validates full name, email, password, and confirmation; controller checks matching passwords, normalizes email, checks duplicates, encodes password with BCrypt, saves user, returns 201. New users default to role `USER` via `User` field initializer; signup does not accept a role.

## Login and JWT
`AuthenticationManager` checks email/password. The controller signs a JWT with HS256; claims include issuer `employee-management`, issue/expiry timestamps, subject=email, and role. Default expiration is 3600 seconds. Response returns bearer token and user profile.

## Frontend behavior
`frontend/src/api/client.js` adds JWT from `sessionStorage` under `ems_token` to requests; on a non-auth 401 it clears the token and dispatches `ems:unauthorized`. `AuthApp.jsx` handles login/signup and logout; logout removes token from browser session storage.

## Login email
`EmailService.sendSuccessfulLoginEmail()` uses `@Async("loginAlertExecutor")` and Spring Mail to send a successful-login alert to the registered email. Failure is logged and does not reject successful login. SMTP delivery was **not independently verified** by reviewing code.

## Security findings requiring action before sharing/deployment
1. **High priority: the supplied `application.properties` contains hardcoded SMTP credentials and a hardcoded JWT signing secret.** Remove those values from source control, rotate/revoke exposed credentials, and load them exclusively from environment variables or a secret manager. Review Git history too.
2. The JWT decoder checks for a secret of at least 32 bytes, but the checked-in property is still a secret exposure.
3. RBAC is **not yet implemented** despite presence of a role field; define and enforce an explicit ADMIN/USER permission matrix.
4. `sessionStorage` tokens are exposed to successful XSS attacks; apply standard XSS protections and HTTPS in production.
5. CSRF is disabled; this matches a stateless bearer-token approach but should be reviewed if authentication transport changes.
