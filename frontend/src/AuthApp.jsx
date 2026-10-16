import { useEffect, useState } from 'react';
import Dashboard from './App';
import { login, signup, me } from './api/auth';

export default function AuthApp() {

    const [user, setUser] = useState(null);
    const [checking, setChecking] = useState(true);
    const [mode, setMode] = useState('login');
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [busy, setBusy] = useState(false);

    const [form, setForm] = useState({
        fullName: '',
        email: '',
        password: '',
        confirmPassword: ''
    });

    //Logout function

    const logout = () => {
        sessionStorage.removeItem('ems_token');
        setUser(null);
        setMode('login');
        setError('');
        window.history.replaceState({}, '', '/login');
    };

    useEffect(() => {

        const listener = () => logout();

        window.addEventListener('ems:unauthorized', listener);

        const token = sessionStorage.getItem('ems_token');

        if (token) {
            me()
                .then(r => {
                    setUser(r.data);
                    window.history.replaceState({}, '', '/dashboard');
                })
                .catch(() => {
                    sessionStorage.removeItem('ems_token');
                })
                .finally(() => setChecking(false));
        } else {
            setChecking(false);
        }

        return () =>
            window.removeEventListener('ems:unauthorized', listener);

    }, []);

    const changeMode = m => {
        setMode(m);
        setError('');
        setNotice('');

        setForm({
            fullName: '',
            email: '',
            password: '',
            confirmPassword: ''
        });

        window.history.replaceState({}, '', `/${m}`);
    };

    async function submit(e) {

        e.preventDefault();
        setBusy(true);
        setError('');

        try {

            if (mode === 'signup') {

                if (form.password !== form.confirmPassword) {
                    throw new Error('Passwords do not match');
                }

                await signup(form);

                changeMode('login');

                setNotice('Account created successfully. Please log in.');

            } else {

                const r = await login({
                    email: form.email,
                    password: form.password
                });

                sessionStorage.setItem(
                    'ems_token',
                    r.data.accessToken
                );

                setUser(r.data.user);

                window.history.replaceState({}, '', '/dashboard');
            }

        } catch (err) {

            setError(
                err.response?.data?.detail ||
                err.response?.data?.message ||
                err.message ||
                'Something went wrong'
            );

        } finally {
            setBusy(false);
        }
    }

    if (checking) {
        return (
            <main className="auth-shell">
                <p>Checking authentication...</p>
            </main>
        );
    }

    if (user) {
        return (
            <Dashboard
                user={user}
                onLogout={logout}
            />
        );
    }

    //login and signup ui

    return (
        <main className="auth-shell">

            <section className="auth-card">

                <div className="auth-brand">
                    EMS <span>• Employee Management</span>
                </div>

                <h1>
                    {mode === 'login'
                        ? 'Welcome back'
                        : 'Create your account'}
                </h1>

                <p>
                    {mode === 'login'
                        ? 'Sign in to access your dashboard'
                        : 'Register to manage employee records'}
                </p>

                {error && (
                    <div className="auth-error" role="alert">
                        {error}
                    </div>
                )}

                {notice && (
                    <div className="auth-success">
                        {notice}
                    </div>
                )}

                <form onSubmit={submit}>

                    {mode === 'signup' && (
                        <label>
                            Full Name

                            <input
                                required
                                maxLength="100"
                                autoComplete="name"
                                value={form.fullName}
                                onChange={e =>
                                    setForm({
                                        ...form,
                                        fullName: e.target.value
                                    })
                                }
                            />
                        </label>
                    )}

                    <label>
                        Email Address

                        <input
                            required
                            type="email"
                            autoComplete="email"
                            value={form.email}
                            onChange={e =>
                                setForm({
                                    ...form,
                                    email: e.target.value
                                })
                            }
                        />
                    </label>

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
                                setForm({
                                    ...form,
                                    password: e.target.value
                                })
                            }
                        />
                    </label>

                    {mode === 'signup' && (
                        <label>
                            Confirm Password

                            <input
                                required
                                type="password"
                                minLength="8"
                                autoComplete="new-password"
                                value={form.confirmPassword}
                                onChange={e =>
                                    setForm({
                                        ...form,
                                        confirmPassword: e.target.value
                                    })
                                }
                            />
                        </label>
                    )}

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

                <p className="auth-switch">

                    {mode === 'login'
                        ? "Don't have an account? "
                        : 'Already have an account? '}

                    <button
                        type="button"
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