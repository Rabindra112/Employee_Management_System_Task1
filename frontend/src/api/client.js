import axios from "axios";

export const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api"
});

// Request Interceptor
api.interceptors.request.use(config => {
    const token = sessionStorage.getItem("ems_token");

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

// Response Interceptor
api.interceptors.response.use(
    r => r,
    error => {
        if (
            error.response?.status === 401 &&
            !["/auth/login", "/auth/signup"].includes(error.config?.url)
        ) {
            sessionStorage.removeItem("ems_token");

            window.dispatchEvent(new Event("ems:unauthorized"));
        }

        return Promise.reject(error);
    }
);