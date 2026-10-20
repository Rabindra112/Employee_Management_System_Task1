import { useEffect, useState } from 'react';
import Dashboard from './App';
import EmployeeDashboard from './EmployeeDashboard';
import { login, signup, me } from './api/auth';
import { encryptPassword } from './utils/rsaEncryption';

function dashboardPath(user) {
    return user.role === 'ADMIN' ? '/admin/dashboard' : '/employee/dashboard';
}

function authenticatedUser(profile) {
    const role = profile?.role === 'USER' ? 'EMPLOYEE' : profile?.role;
    if (!['ADMIN', 'EMPLOYEE'].includes(role)) {
        throw new Error('Your account does not have a supported role. Please contact an administrator.');
    }
    return { ...profile, role };
}

export default function AuthApp() {

    const [user, setUser] = useState(null);
    const [checking, setChecking] = useState(true);

    const [mode, setMode] = useState(() => window.location.pathname === '/signup' ? 'signup' : 'login');

    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [busy, setBusy] = useState(false);

    const [form, setForm] = useState({
        fullName: '',
        email: '',
        password: '',
        confirmPassword: ''
    });

    // =====================================================
    // LOGOUT
    // =====================================================

    const logout = () => {

        sessionStorage.removeItem('ems_token');

        setUser(null);
        setMode('login');
        setError('');
        setNotice('');

        setForm({
            fullName: '',
            email: '',
            password: '',
            confirmPassword: ''
        });

        window.history.replaceState({}, '', '/login');
    };

    // =====================================================
    // CHECK EXISTING AUTHENTICATION
    // =====================================================

    useEffect(() => {

        const controller = new AbortController();

        const listener = () => logout();

        window.addEventListener('ems:unauthorized', listener);

        const token = sessionStorage.getItem('ems_token');

        if (token) {

            me({ signal: controller.signal })
                .then(response => {

                    if (controller.signal.aborted) return;
                    const profile = authenticatedUser(response.data);

                    setUser(profile);

                    window.history.replaceState(
                        {},
                        '',
                        dashboardPath(profile)
                    );

                })
                .catch(err => {

                    if (controller.signal.aborted) return;

                    sessionStorage.removeItem('ems_token');
                    setError(err.response?.status === 401
                        ? 'Your session expired. Please sign in again.'
                        : err.message || 'Unable to verify your account. Please sign in again.');

                })
                .finally(() => {

                    if (!controller.signal.aborted) setChecking(false);

                });

        } else {

            setChecking(false);

        }

        return () => {

            controller.abort();

            window.removeEventListener(
                'ems:unauthorized',
                listener
            );

        };

    }, []);

    // Keep direct links and browser navigation aligned with the authenticated role.
    useEffect(() => {
        if (checking) return;
        const synchronizePath = () => {
            window.history.replaceState({}, '', user ? dashboardPath(user) : `/${mode}`);
        };
        synchronizePath();
        window.addEventListener('popstate', synchronizePath);
        return () => window.removeEventListener('popstate', synchronizePath);
    }, [checking, user, mode]);

    // =====================================================
    // CHANGE LOGIN / SIGNUP MODE
    // =====================================================

    const changeMode = (newMode) => {

        setMode(newMode);

        setError('');
        setNotice('');

        setForm({
            fullName: '',
            email: '',
            password: '',
            confirmPassword: ''
        });

        window.history.replaceState(
            {},
            '',
            `/${newMode}`
        );
    };

    // =====================================================
    // HANDLE LOGIN AND SIGNUP
    // =====================================================

    async function submit(e) {

        e.preventDefault();

        if (busy) return;

        setBusy(true);
        setError('');
        setNotice('');

        try {

            // =============================================
            // SIGNUP WITH RSA ENCRYPTION
            // =============================================

            if (mode === 'signup') {

                // Validate password matching
                if (form.password !== form.confirmPassword) {

                    throw new Error(
                        'Passwords do not match'
                    );
                }

                // Validate password length
                if (form.password.length < 8) {

                    throw new Error(
                        'Password must contain at least 8 characters'
                    );
                }

                // Encrypt password using RSA public key
                const encryptedPassword =
                    await encryptPassword(form.password);

                // Encrypt confirm password
                const encryptedConfirmPassword =
                    await encryptPassword(form.confirmPassword);

                // Send encrypted data to backend
                await signup({

                    fullName: form.fullName.trim(),

                    email: form.email.trim().toLowerCase(),

                    encryptedPassword,

                    encryptedConfirmPassword

                });

                // Clear form and redirect to login
                changeMode('login');

                setNotice(
                    'Employee account created. Log in to your dashboard. An administrator will link your employee record.'
                );

            }

            // =============================================
            // LOGIN WITH RSA ENCRYPTION
            // =============================================

            else {

                // Encrypt password before API request
                const encryptedPassword =
                    await encryptPassword(form.password);

                // Send encrypted password
                const response = await login({

                    email: form.email.trim().toLowerCase(),

                    encryptedPassword

                });

                const profile = authenticatedUser(response.data.user);

                // Store JWT token
                sessionStorage.setItem(
                    'ems_token',
                    response.data.accessToken
                );

                // Clear password from React state
                setForm(previous => ({
                    ...previous,
                    password: '',
                    confirmPassword: ''
                }));

                // Set authenticated user
                setUser(profile);

                // Redirect to dashboard
                window.history.replaceState(
                    {},
                    '',
                    dashboardPath(profile)
                );
            }

        } catch (err) {

            console.error(
                'Authentication request failed:',
                err.response?.status || err.name
            );

            setError(
                err.response?.data?.detail ||
                err.response?.data?.message ||
                err.message ||
                'Something went wrong. Please try again.'
            );

        } finally {

            setBusy(false);

        }
    }

    // =====================================================
    // AUTHENTICATION LOADING SCREEN
    // =====================================================

    if (checking) {

        return (

            <main className="auth-shell">

                <p>Checking authentication...</p>

            </main>

        );
    }

    // =====================================================
    // DASHBOARD
    // =====================================================

    if (user) {

        const RoleDashboard = user.role === 'ADMIN' ? Dashboard : EmployeeDashboard;

        return (

            <RoleDashboard
                user={user}
                onLogout={logout}
            />

        );
    }

    // =====================================================
    // LOGIN AND SIGNUP UI
    // =====================================================

    return (

        <main className="auth-shell">

            <section className="auth-card">

                <div className="auth-brand">

                    EMS <span>Employee Management</span>

                </div>

                <h1>

                    {mode === 'login'
                        ? 'Welcome back'
                        : 'Create an employee account'}

                </h1>

                <p>

                    {mode === 'login'
                        ? 'Sign in to access your dashboard'
                        : 'Register to view your own profile and documents once your administrator links your employee record.'}

                </p>

                {/* ERROR MESSAGE */}

                {error && (

                    <div
                        className="auth-error"
                        role="alert"
                    >
                        {error}
                    </div>

                )}

                {/* SUCCESS MESSAGE */}

                {notice && (

                    <div className="auth-success">

                        {notice}

                    </div>

                )}

                <form onSubmit={submit}>

                    {/* FULL NAME - SIGNUP ONLY */}

                    {mode === 'signup' && (

                        <label>

                            Full Name

                            <input
                                required
                                maxLength={100}
                                autoComplete="name"
                                value={form.fullName}
                                onChange={e =>
                                    setForm(previous => ({
                                        ...previous,
                                        fullName: e.target.value
                                    }))
                                }
                            />

                        </label>

                    )}

                    {/* EMAIL ADDRESS */}

                    <label>

                        Email Address

                        <input
                            required
                            type="email"
                            autoComplete="email"
                            value={form.email}
                            onChange={e =>
                                setForm(previous => ({
                                    ...previous,
                                    email: e.target.value
                                }))
                            }
                        />

                    </label>

                    {/* PASSWORD */}

                    <label>

                        Password

                        <input
                            required
                            type="password"
                            minLength={
                                mode === 'signup'
                                    ? 8
                                    : undefined
                            }
                            autoComplete={
                                mode === 'login'
                                    ? 'current-password'
                                    : 'new-password'
                            }
                            value={form.password}
                            onChange={e =>
                                setForm(previous => ({
                                    ...previous,
                                    password: e.target.value
                                }))
                            }
                        />

                    </label>

                    {/* CONFIRM PASSWORD - SIGNUP ONLY */}

                    {mode === 'signup' && (

                        <label>

                            Confirm Password

                            <input
                                required
                                type="password"
                                minLength={8}
                                autoComplete="new-password"
                                value={form.confirmPassword}
                                onChange={e =>
                                    setForm(previous => ({
                                        ...previous,
                                        confirmPassword: e.target.value
                                    }))
                                }
                            />

                        </label>

                    )}

                    {/* SUBMIT BUTTON */}

                    <button
                        disabled={busy}
                        type="submit"
                    >

                        {busy
                            ? 'Please wait...'
                            : mode === 'login'
                                ? 'Login'
                                : 'Sign Up'}

                    </button>

                </form>

                {/* SWITCH LOGIN / SIGNUP */}

                <p className="auth-switch">

                    {mode === 'login'
                        ? "Don't have an account? "
                        : 'Already have an account? '}

                    <button
                        type="button"
                        disabled={busy}
                        onClick={() =>
                            changeMode(
                                mode === 'login'
                                    ? 'signup'
                                    : 'login'
                            )
                        }
                    >

                        {mode === 'login'
                            ? 'Sign up'
                            : 'Login'}

                    </button>

                </p>

            </section>

        </main>
    );
}
