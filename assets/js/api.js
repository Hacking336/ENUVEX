/**
 * API Client for Job & Skills Matching System
 * Connects the frontend UI to the Spring Boot backend
 */

// The Spring Boot backend serves both these pages and /api, so the API is
// same-origin: one relative base works on localhost, on Render and on the
// custom domain, and it never depends on a host being listed in CORS.
// A page packaged inside the Android/iOS shell is not served by the backend,
// so it keeps the deployed API address instead. Either case can be overridden
// by setting window.JSM_API_BASE before this file loads.
const JSM_REMOTE_API = 'https://enuvex.onrender.com/api';
const JSM_PACKAGED_SHELL = !/^https?:$/.test(window.location.protocol)
    || (window.location.hostname === 'localhost' && window.location.protocol === 'https:');
const API_BASE_URL = window.JSM_API_BASE || (JSM_PACKAGED_SHELL ? JSM_REMOTE_API : '/api');

// Exposed so the shared shells can resolve uploaded photo URLs.
window.API_BASE_URL = API_BASE_URL;

// The API runs on a free Render instance that sleeps when idle, and the first
// request after it sleeps waits through the container boot. That is slow but it
// is not an error, so the wait is bounded rather than instant: the request is
// only given up on after two minutes.
const API_TIMEOUT_MS = 120000;

const ApiClient = {
    token: null,
    refreshToken: null,
    userType: null,

    setAuth(token, refreshToken, userType, fullName, userId) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userType = userType;
        localStorage.setItem('jsm_token', token);
        localStorage.setItem('jsm_refresh', refreshToken);
        localStorage.setItem('jsm_user_type', userType);
        // Kept so a console that has no profile endpoint (admin) can still show
        // the real signed-in account instead of a placeholder.
        if (fullName) localStorage.setItem('jsm_full_name', fullName);
        if (userId !== undefined && userId !== null) localStorage.setItem('jsm_user_id', String(userId));
    },

    clearAuth() {
        this.token = null;
        this.refreshToken = null;
        this.userType = null;
        localStorage.removeItem('jsm_token');
        localStorage.removeItem('jsm_refresh');
        localStorage.removeItem('jsm_user_type');
        localStorage.removeItem('jsm_full_name');
        localStorage.removeItem('jsm_user_id');
    },

    loadAuth() {
        this.token = localStorage.getItem('jsm_token');
        this.refreshToken = localStorage.getItem('jsm_refresh');
        this.userType = localStorage.getItem('jsm_user_type');
        return !!this.token;
    },

    async request(url, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), API_TIMEOUT_MS);

        let response;
        try {
            response = await fetch(`${API_BASE_URL}${url}`, {
                ...options,
                headers,
                signal: controller.signal
            });
        } catch (error) {
            // A rejected fetch used to leave the page silent, which looks like a
            // dead button; say what happened instead.
            throw new Error(error && error.name === 'AbortError'
                ? 'The server took too long to answer. Please try again.'
                : 'Cannot reach the JSM server. Check your connection and try again.');
        } finally {
            clearTimeout(timer);
        }

        // Read the body as text first: an error response may be empty or HTML,
        // and response.json() would then fail with a confusing syntax error.
        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = null;
            }
        }

        if (response.status === 401) {
            this.clearAuth();
            window.location.href = '/login.html';
            throw new Error('Your session expired. Please log in again.');
        }

        if (!response.ok) {
            const message = (data && (data.message || data.error)) || `Request failed (${response.status})`;
            throw new Error(message);
        }

        return data;
    },

    // Starts the sleeping instance waking while the user is still typing, so the
    // sign-in click does not pay the whole cold-start cost. Failure is ignored:
    // this is a hint, never a requirement for the page to work.
    warmUp() {
        fetch(`${API_BASE_URL}/health`, { method: 'GET', cache: 'no-store' }).catch(() => {});
    },

    async login(email, password) {
        const data = await this.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        this.setAuth(data.token, data.refreshToken, data.userType, data.fullName, data.userId);
        return data;
    },

    async register(payload) {
        const formData = new FormData();
        formData.append('profile', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
        if (payload.profilePhoto) formData.append('profilePhoto', payload.profilePhoto);
        if (payload.logo) formData.append('logo', payload.logo);

        const response = await fetch(`${API_BASE_URL}/auth/register`, {
            method: 'POST',
            body: formData
        });

        const raw = await response.text();
        let data = {};
        try {
            data = raw ? JSON.parse(raw) : {};
        } catch (e) {
            data = {};
        }
        if (!response.ok) {
            throw new Error(data.message || data.error || `Registration failed (${response.status})`);
        }
        this.setAuth(data.token, data.refreshToken, data.userType, data.fullName, data.userId);
        return data;
    },

    async getProfile() {
        return this.request(`/${this.userType.toLowerCase() === 'job_seeker' ? 'job-seekers' : 'employers'}/profile`);
    },

    /**
     * Updates the signed-in job seeker's profile. The backend reads the record
     * as a multipart part named "profile" plus an optional "profilePhoto".
     */
    async updateJobSeekerProfile(payload, photoFile) {
        const formData = new FormData();
        formData.append('profile', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
        if (photoFile) formData.append('profilePhoto', photoFile);

        const response = await fetch(`${API_BASE_URL}/job-seekers/profile`, {
            method: 'PUT',
            headers: this.token ? { Authorization: `Bearer ${this.token}` } : {},
            body: formData
        });

        const raw = await response.text();
        let data = null;
        if (raw) {
            try { data = JSON.parse(raw); } catch (e) { data = null; }
        }

        if (response.status === 401) {
            this.clearAuth();
            window.location.href = '/login.html';
            throw new Error('Your session expired. Please log in again.');
        }

        if (!response.ok) {
            const message = (data && (data.message || data.error)) || `Could not save your profile (${response.status})`;
            throw new Error(message);
        }

        return data;
    },

    async getJobs(params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.request(`/jobs${queryString ? '?' + queryString : ''}`);
    },

    async getJobDetails(jobId) {
        return this.request(`/jobs/${jobId}`);
    },

    async applyForJob(jobId) {
        // The backend reads jobId as a query parameter, not from the body.
        return this.request(`/job-seekers/apply?jobId=${encodeURIComponent(jobId)}`, {
            method: 'POST'
        });
    },

    async getApplications() {
        return this.request('/job-seekers/applications');
    },

    async getSavedJobs() {
        return this.request('/job-seekers/saved');
    },

    async saveJob(jobId) {
        return this.request(`/job-seekers/saved?jobId=${encodeURIComponent(jobId)}`, {
            method: 'POST'
        });
    },

    async unsaveJob(jobId) {
        return this.request(`/job-seekers/saved/${jobId}`, {
            method: 'DELETE'
        });
    },

    async getProfileCompletion() {
        return this.request('/job-seekers/profile/completion');
    },

    async getEmployerDashboard() {
        return this.request('/employers/dashboard');
    },

    async getMyJobs() {
        return this.request('/employers/my-jobs');
    },

    async postJob(jobData) {
        return this.request('/employers/jobs', {
            method: 'POST',
            body: JSON.stringify(jobData)
        });
    },

    async getApplicants(jobId, sortBy = 'match') {
        return this.request(`/employers/applicants?jobId=${jobId}&sortBy=${sortBy}`);
    },

    async updateApplicationStatus(applicationId, status) {
        return this.request(`/employers/applicants/${applicationId}/status?status=${status}`, {
            method: 'PUT'
        });
    },

    async getAdminDashboard() {
        return this.request('/admin/dashboard');
    },

    async getAdminUsers() {
        return this.request('/admin/users');
    },

    async getAdminJobPosts() {
        return this.request('/admin/job-posts');
    },

    async getAdminApplications() {
        return this.request('/admin/applications');
    },

    /* ---------- employer console ------------------------------------- */

    /** Multipart update: the backend reads "profile" plus an optional "logo". */
    async updateEmployerProfile(payload, logoFile) {
        const formData = new FormData();
        formData.append('profile', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
        if (logoFile) formData.append('logo', logoFile);

        const response = await fetch(`${API_BASE_URL}/employers/profile`, {
            method: 'PUT',
            headers: this.token ? { Authorization: `Bearer ${this.token}` } : {},
            body: formData
        });

        const raw = await response.text();
        let data = null;
        if (raw) {
            try { data = JSON.parse(raw); } catch (e) { data = null; }
        }

        if (response.status === 401) {
            this.clearAuth();
            window.location.href = '/login.html';
            throw new Error('Your session expired. Please log in again.');
        }
        if (!response.ok) {
            const message = (data && (data.message || data.error)) || `Could not save the company profile (${response.status})`;
            throw new Error(message);
        }
        return data;
    },

    async updateJob(jobId, jobData) {
        return this.request(`/employers/jobs/${encodeURIComponent(jobId)}`, {
            method: 'PUT',
            body: JSON.stringify(jobData)
        });
    },

    async deleteJob(jobId) {
        return this.request(`/employers/jobs/${encodeURIComponent(jobId)}`, { method: 'DELETE' });
    },

    async getEmployerProfile() {
        return this.request('/employers/profile');
    },

    /* ---------- admin console ---------------------------------------- */

    async getAdminEmployers() {
        return this.request('/admin/employers');
    },

    async verifyEmployer(userId) {
        return this.request(`/admin/employers/${encodeURIComponent(userId)}/verify`, { method: 'PUT' });
    },

    async setUserActive(userId) {
        return this.request(`/admin/users/${encodeURIComponent(userId)}/deactivate`, { method: 'PUT' });
    },

    async deleteAdminJobPost(jobId) {
        return this.request(`/admin/job-posts/${encodeURIComponent(jobId)}`, { method: 'DELETE' });
    },

    async getAdminGeoStats(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request(`/admin/geo/stats${query ? '?' + query : ''}`);
    },

    async getAdminReports() {
        return this.request('/admin/reports');
    },

    async getAdminCategories() {
        return this.request('/admin/categories');
    }
};

// Load auth on startup
ApiClient.loadAuth();

window.ApiClient = ApiClient;