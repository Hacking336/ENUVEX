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
        return this.request(`/applications/job/${jobId}/applicants?sortBy=${encodeURIComponent(sortBy)}`);
    },

    async updateApplicationStatus(applicationId, status, message) {
        return this.request(`/applications/${applicationId}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status: status, message: message || null })
        });
    },

    async getApplication(applicationId) {
        return this.request(`/applications/${applicationId}`);
    },

    async getApplicationTimeline(applicationId) {
        return this.request(`/applications/${applicationId}/timeline`);
    },

    async withdrawApplication(applicationId) {
        return this.request(`/applications/${applicationId}/withdraw`, { method: 'POST' });
    },

    async scheduleInterview(applicationId, payload) {
        return this.request(`/applications/${applicationId}/interview`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    async rescheduleInterview(applicationId, payload) {
        return this.request(`/applications/${applicationId}/interview`, {
            method: 'PATCH',
            body: JSON.stringify(payload)
        });
    },

    async cancelInterview(applicationId, reason) {
        return this.request(`/applications/${applicationId}/interview?reason=${encodeURIComponent(reason || '')}`, {
            method: 'DELETE'
        });
    },

    async getUnreadNotificationCount() {
        return this.request('/notifications/unread-count');
    },

    async markNotificationRead(id) {
        return this.request(`/notifications/${id}/read`, { method: 'PATCH' });
    },

    async markAllNotificationsRead() {
        return this.request('/notifications/read-all', { method: 'PATCH' });
    },

    async deleteNotification(id) {
        return this.request(`/notifications/${id}`, { method: 'DELETE' });
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

    async requestEmployerVerification() {
        return this.request('/employers/verification-request', { method: 'POST' });
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

    async rejectEmployerVerification(userId, reason) {
        return this.request('/admin/employers/' + encodeURIComponent(userId) + '/reject-verification?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async requestEmployerInformation(userId, reason) {
        return this.request('/admin/employers/' + encodeURIComponent(userId) + '/info-request?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async getModerationOverview() {
        return this.request('/admin/moderation-overview');
    },

    async getRecentAuditLogs() {
        return this.request('/admin/audit-logs');
    },

    async getAuditLogs(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request('/admin/audit-logs' + (query ? '?' + query : ''));
    },

    async suspendUser(userId, reason) {
        return this.request('/admin/users/' + encodeURIComponent(userId) + '/suspend?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async restoreUser(userId) {
        return this.request('/admin/users/' + encodeURIComponent(userId) + '/restore', { method: 'PUT' });
    },

    async getModerationReports(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request('/admin/moderation-reports' + (query ? '?' + query : ''));
    },

    async markModerationReportUnderReview(reportId) {
        return this.request('/admin/moderation-reports/' + encodeURIComponent(reportId) + '/review', { method: 'PUT' });
    },

    async resolveModerationReport(reportId, reason) {
        return this.request('/admin/moderation-reports/' + encodeURIComponent(reportId) + '/resolve?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async dismissModerationReport(reportId, reason) {
        return this.request('/admin/moderation-reports/' + encodeURIComponent(reportId) + '/dismiss?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async getReportStats() {
        return this.request('/admin/moderation-reports/stats');
    },

    async getSuspiciousListings(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request('/admin/suspicious-listings' + (query ? '?' + query : ''));
    },

    async dismissSuspiciousListing(listingId, reason) {
        return this.request('/admin/suspicious-listings/' + encodeURIComponent(listingId) + '/dismiss?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async runSuspiciousDetection() {
        return this.request('/admin/suspicious-listings/detect', { method: 'POST' });
    },

    async deleteAdminJobPost(jobId) {
        return this.request(`/admin/job-posts/${encodeURIComponent(jobId)}`, { method: 'DELETE' });
    },

    async approveAdminJobPost(jobId) {
        return this.request('/admin/job-posts/' + encodeURIComponent(jobId) + '/approve', { method: 'PUT' });
    },

    async rejectAdminJobPost(jobId, reason) {
        return this.request('/admin/job-posts/' + encodeURIComponent(jobId) + '/reject?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async changesRequestedAdminJobPost(jobId, reason) {
        return this.request('/admin/job-posts/' + encodeURIComponent(jobId) + '/changes-requested?reason=' + encodeURIComponent(reason), { method: 'PUT' });
    },

    async restoreAdminJobPost(jobId) {
        return this.request('/admin/job-posts/' + encodeURIComponent(jobId) + '/restore', { method: 'PUT' });
    },

    /* ---------- reporting a posting (seeker / employer) --------------- */

    async submitReport(payload) {
        return this.request('/moderation/reports', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    async getMyReports() {
        return this.request('/moderation/reports/mine');
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
    },

    /* ---------- skills gap and career guidance ----------------------- */

    async getSkillsAnalysis(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request(`/job-seekers/skills-analysis${query ? '?' + query : ''}`);
    },

    async getSkillsDemand(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request(`/job-seekers/skills-demand${query ? '?' + query : ''}`);
    },

    async simulateSkills(skills, threshold) {
        return this.request('/job-seekers/skills-simulation', {
            method: 'POST',
            body: JSON.stringify({ skills: skills, threshold: threshold })
        });
    },

    async getCareerOpportunities(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request(`/job-seekers/career-opportunities${query ? '?' + query : ''}`);
    },

    async getLearningInterests() {
        return this.request('/job-seekers/learning-interests');
    },

    async addLearningInterest(skill) {
        return this.request(`/job-seekers/learning-interests?skill=${encodeURIComponent(skill)}`, {
            method: 'POST'
        });
    },

    async removeLearningInterest(skill) {
        return this.request(`/job-seekers/learning-interests?skill=${encodeURIComponent(skill)}`, {
            method: 'DELETE'
        });
    },

    async getSkillCatalog(q, limit) {
        const params = new URLSearchParams();
        if (q) params.set('q', q);
        if (limit) params.set('limit', String(limit));
        const query = params.toString();
        return this.request(`/skills/catalog${query ? '?' + query : ''}`);
    },

    async getSkillResources(skill) {
        return this.request(`/skills/resources?skill=${encodeURIComponent(skill)}`);
    },

    async getAdminSkillsAnalytics(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request(`/admin/skills-analytics${query ? '?' + query : ''}`);
    },

    async getAdminSkillsDemand(params = {}) {
        const query = new URLSearchParams(params).toString();
        return this.request(`/admin/skills-demand${query ? '?' + query : ''}`);
    },

    async getAdminLearningResources() {
        return this.request('/admin/learning-resources');
    },

    async createLearningResource(payload) {
        return this.request('/admin/learning-resources', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    async updateLearningResource(id, payload) {
        return this.request(`/admin/learning-resources/${encodeURIComponent(id)}`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
    },

    async deleteLearningResource(id) {
        return this.request(`/admin/learning-resources/${encodeURIComponent(id)}`, {
            method: 'DELETE'
        });
    }
};

// Load auth on startup
ApiClient.loadAuth();

window.ApiClient = ApiClient;

/* ==========================================================================
   Notification presentation shared by the bell and the inbox page.
   ========================================================================== */
(function () {
    'use strict';

    var ICONS = {
        APPLICATION_SUBMITTED: 'fa-paper-plane',
        APPLICATION_STATUS: 'fa-arrows-rotate',
        APPLICATION_WITHDRAWN: 'fa-arrow-rotate-left',
        APPLICATION_RECEIVED: 'fa-user-plus',
        APPLICANT_WITHDRAWN: 'fa-user-minus',
        INTERVIEW_INVITE: 'fa-calendar-plus',
        INTERVIEW_RESCHEDULED: 'fa-calendar-day',
        INTERVIEW_CANCELLED: 'fa-calendar-xmark',
        JOB_RECOMMENDATION: 'fa-wand-magic-sparkles',
        EMPLOYER_VERIFICATION: 'fa-building-circle-check'
    };

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function isRead(notification) {
        return notification.isRead === true || notification.read === true;
    }

    function linkFor(notification) {
        if (notification.link) return notification.link;
        if (notification.referenceType === 'APPLICATION' && notification.referenceId) {
            return '/job-seeker/application.html?id=' + encodeURIComponent(notification.referenceId);
        }
        if (notification.referenceType === 'JOB_POST' && notification.referenceId) {
            return '/job-seeker/job-details.html?id=' + encodeURIComponent(notification.referenceId);
        }
        return null;
    }

    function icon(type) {
        return ICONS[String(type || '').toUpperCase()] || 'fa-bell';
    }

    function timeAgo(value) {
        if (!value) return '';
        var then = new Date(value);
        if (isNaN(then.getTime())) return '';
        var seconds = Math.max(0, Math.floor((Date.now() - then.getTime()) / 1000));
        if (seconds < 60) return 'Just now';
        var minutes = Math.floor(seconds / 60);
        if (minutes < 60) return minutes + ' min ago';
        var hours = Math.floor(minutes / 60);
        if (hours < 24) return hours + ' hour' + (hours === 1 ? '' : 's') + ' ago';
        var days = Math.floor(hours / 24);
        if (days < 7) return days + ' day' + (days === 1 ? '' : 's') + ' ago';
        return then.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
    }

    /** One dropdown row. Rendered as a link when the notice has a target. */
    function itemHtml(notification, options) {
        var unread = !isRead(notification);
        var href = linkFor(notification);
        var tag = href ? 'a' : 'div';
        var attrs = href ? ' href="' + esc(href) + '"' : '';
        var title = notification.title ? '<strong>' + esc(notification.title) + '</strong>' : '';
        var body = '<div>' + title + '<p>' + esc(notification.message || '') + '</p><time>' +
            esc(timeAgo(notification.createdAt)) + '</time></div>';
        var action = options && options.deletable
            ? '<button class="notif-delete" type="button" data-delete-notification="' +
                esc(notification.id) + '" aria-label="Delete notification"><i class="fas fa-trash"></i></button>'
            : '';
        return '<' + tag + ' class="notif' + (unread ? ' notif-unread' : '') + '"' + attrs +
            ' data-notification-id="' + esc(notification.id) + '">' +
            '<div class="notif-icon"><i class="fas ' + icon(notification.type) + '"></i></div>' +
            body + action + '</' + tag + '>';
    }

    function markRead(id) {
        if (!window.ApiClient || !id) return Promise.resolve();
        return ApiClient.markNotificationRead(id).catch(function () { /* non-blocking */ });
    }

    /**
     * Polls the unread count so new notices appear without a page reload. One
     * lightweight request every 45 seconds, paused while the tab is hidden.
     */
    function startPolling(onCount) {
        var last = -1;
        function tick() {
            if (document.hidden || !window.ApiClient || !ApiClient.loadAuth()) return;
            ApiClient.getUnreadNotificationCount().then(function (result) {
                var count = Number(result && result.count);
                if (!isFinite(count) || count === last) return;
                last = count;
                onCount(count);
            }).catch(function () { /* keep the last known count */ });
        }
        tick();
        var timer = setInterval(tick, 45000);
        document.addEventListener('visibilitychange', function () {
            if (!document.hidden) tick();
        });
        return timer;
    }

    window.JSMNotify = {
        esc: esc,
        isRead: isRead,
        linkFor: linkFor,
        icon: icon,
        timeAgo: timeAgo,
        itemHtml: itemHtml,
        markRead: markRead,
        startPolling: startPolling
    };
})();