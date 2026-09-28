/**
 * API Client for Job & Skills Matching System
 * Connects the frontend UI to the Spring Boot backend
 */

const API_BASE_URL = 'http://localhost:8080/api';

const ApiClient = {
    token: null,
    refreshToken: null,
    userType: null,

    setAuth(token, refreshToken, userType) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userType = userType;
        localStorage.setItem('jsm_token', token);
        localStorage.setItem('jsm_refresh', refreshToken);
        localStorage.setItem('jsm_user_type', userType);
    },

    clearAuth() {
        this.token = null;
        this.refreshToken = null;
        this.userType = null;
        localStorage.removeItem('jsm_token');
        localStorage.removeItem('jsm_refresh');
        localStorage.removeItem('jsm_user_type');
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

        const response = await fetch(`${API_BASE_URL}${url}`, {
            ...options,
            headers
        });

        if (response.status === 401) {
            this.clearAuth();
            window.location.href = '/index.html';
        }

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || data.error || 'Request failed');
        }

        return data;
    },

    async login(email, password) {
        const data = await this.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        this.setAuth(data.token, data.refreshToken, data.userType);
        return data;
    },

    async register(payload) {
        const data = await this.request('/auth/register', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        this.setAuth(data.token, data.refreshToken, data.userType);
        return data;
    },

    async getProfile() {
        return this.request(`/${this.userType.toLowerCase() === 'job_seeker' ? 'job-seekers' : 'employers'}/profile`);
    },

    async getJobs(params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.request(`/jobs${queryString ? '?' + queryString : ''}`);
    },

    async getJobDetails(jobId) {
        return this.request(`/jobs/${jobId}`);
    },

    async applyForJob(jobId) {
        return this.request('/job-seekers/apply', {
            method: 'POST',
            body: JSON.stringify({ jobId })
        });
    },

    async getApplications() {
        return this.request('/job-seekers/applications');
    },

    async getSavedJobs() {
        return this.request('/job-seekers/saved');
    },

    async saveJob(jobId) {
        return this.request('/job-seekers/saved', {
            method: 'POST',
            body: JSON.stringify({ jobId })
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
    }
};

// Load auth on startup
ApiClient.loadAuth();

window.ApiClient = ApiClient;