/* ==========================================================================
   ENUVEX — Employer & Admin shared shell
   One implementation of the sidebar, top header, notification menu, mobile
   navigation and identity for both consoles, so the two roles share the Job
   Seeker design system while keeping their own navigation and permissions.
   Requires: ../assets/js/api.js (ApiClient) and ../job-seeker/enuvex-app.css
   ========================================================================== */

(function () {
    'use strict';

    // The mark is the one official app icon, so every console shows the same
    // artwork instead of an inline redraw that can drift.
    var BRAND_MARK = '<img src="../assets/brand/jsm-icon.svg" alt="" width="40" height="40">';

    var NAVS = {
        employer: [
            { id: 'dashboard', href: 'dashboard.html', icon: 'fa-gauge-high', label: 'Dashboard', short: 'Dashboard' },
            { id: 'jobs', href: 'jobs.html', icon: 'fa-briefcase', label: 'My Job Posts', short: 'Jobs' },
            { id: 'post-job', href: 'post-job.html', icon: 'fa-circle-plus', label: 'Post a Job', short: 'Post' },
            { id: 'applicants', href: 'applicants.html', icon: 'fa-user-check', label: 'Applicants', short: 'Applicants' },
            { id: 'profile', href: 'profile.html', icon: 'fa-building', label: 'Company Profile', short: 'Company' }
        ],
        admin: [
            { id: 'dashboard', href: 'dashboard.html', icon: 'fa-gauge-high', label: 'Dashboard', short: 'Dashboard' },
            { id: 'users', href: 'users.html', icon: 'fa-users', label: 'User Management', short: 'Users' },
            { id: 'employers', href: 'employers.html', icon: 'fa-building-circle-check', label: 'Employer Verification', short: 'Verify' },
            { id: 'job-posts', href: 'job-posts.html', icon: 'fa-briefcase', label: 'Job Management', short: 'Jobs' },
            { id: 'geography', href: 'geography.html', icon: 'fa-map-location-dot', label: 'Geographic Map', short: 'Map' },
            { id: 'reports', href: 'reports.html', icon: 'fa-chart-column', label: 'Reports & Analytics', short: 'Reports' }
        ]
    };

    var ROLE_META = {
        employer: {
            label: 'Employer',
            accountLabel: 'Employer account',
            searchPlaceholder: 'Search your job posts…',
            searchHref: 'jobs.html'
        },
        admin: {
            label: 'Platform Admin',
            accountLabel: 'Administrator account',
            searchPlaceholder: 'Search users, employers, or jobs…',
            searchHref: 'users.html'
        }
    };

    var EMPLOYMENT_LABELS = {
        FULL_TIME: 'Full-time', PART_TIME: 'Part-time', CONTRACT: 'Contract',
        TEMPORARY: 'Temporary', INTERNSHIP: 'Internship', FREELANCE: 'Freelance', OJT: 'OJT'
    };

    var SCHEDULE_LABELS = {
        MORNING: 'Morning', AFTERNOON: 'Afternoon', EVENING: 'Evening',
        NIGHT: 'Night', WEEKEND: 'Weekend', FLEXIBLE: 'Flexible'
    };

    var CATEGORY_LABELS = {
        RETAIL_STORE: 'Retail / Store', SALES: 'Sales',
        FOOD_SERVICE_RESTAURANT: 'Food Service / Restaurant',
        HOSPITALITY_TOURISM: 'Hospitality / Tourism',
        OFFICE_ADMINISTRATION: 'Office / Administration',
        ACCOUNTING_FINANCE: 'Accounting / Finance', HEALTHCARE: 'Healthcare',
        EDUCATION_TUTORING: 'Education / Tutoring',
        CONSTRUCTION_SKILLED_TRADES: 'Construction / Skilled Trades',
        AGRICULTURE: 'Agriculture', FISHERIES: 'Fisheries', MANUFACTURING: 'Manufacturing',
        LOGISTICS_DELIVERY: 'Logistics / Delivery', TRANSPORTATION: 'Transportation',
        AUTOMOTIVE_MECHANICAL: 'Automotive / Mechanical', CUSTOMER_SERVICE: 'Customer Service',
        SECURITY: 'Security', GENERAL_LABOR: 'General Labor',
        IT_TECHNOLOGY: 'IT / Technology', OTHER: 'Other'
    };

    var ICONS = {
        RETAIL_STORE: 'fa-store', SALES: 'fa-tags', FOOD_SERVICE_RESTAURANT: 'fa-utensils',
        HOSPITALITY_TOURISM: 'fa-hotel', OFFICE_ADMINISTRATION: 'fa-folder-open',
        ACCOUNTING_FINANCE: 'fa-calculator', HEALTHCARE: 'fa-pills',
        EDUCATION_TUTORING: 'fa-chalkboard-user', CONSTRUCTION_SKILLED_TRADES: 'fa-hard-hat',
        AGRICULTURE: 'fa-tractor', FISHERIES: 'fa-fish', MANUFACTURING: 'fa-industry',
        LOGISTICS_DELIVERY: 'fa-truck-fast', TRANSPORTATION: 'fa-van-shuttle',
        AUTOMOTIVE_MECHANICAL: 'fa-wrench', CUSTOMER_SERVICE: 'fa-headset',
        SECURITY: 'fa-shield-halved', GENERAL_LABOR: 'fa-briefcase',
        IT_TECHNOLOGY: 'fa-laptop-code', OTHER: 'fa-briefcase'
    };

    var BUSINESS_TYPES = {
        RETAIL_STORE: 'Retail / Store', RESTAURANT_FOOD_SERVICE: 'Restaurant / Food Service',
        HOSPITALITY: 'Hospitality', OFFICE_ADMINISTRATION: 'Office / Administration',
        HEALTHCARE: 'Healthcare', EDUCATION: 'Education', CONSTRUCTION: 'Construction',
        AGRICULTURE: 'Agriculture', FISHERIES: 'Fisheries', MANUFACTURING: 'Manufacturing',
        LOGISTICS_DELIVERY: 'Logistics / Delivery', TRANSPORTATION: 'Transportation',
        AUTOMOTIVE: 'Automotive', SECURITY: 'Security', IT_TECHNOLOGY: 'IT / Technology',
        OTHER: 'Other'
    };

    var STATUS_META = {
        SUBMITTED: { label: 'Submitted', cls: 'status-applied' },
        REVIEWING: { label: 'Reviewing', cls: 'status-review' },
        SHORTLISTED: { label: 'Shortlisted', cls: 'status-shortlisted' },
        INTERVIEW: { label: 'Interview', cls: 'status-interview' },
        ACCEPTED: { label: 'Accepted', cls: 'status-accepted' },
        REJECTED: { label: 'Rejected', cls: 'status-rejected' },
        WITHDRAWN: { label: 'Withdrawn', cls: 'status-withdrawn' },
        // Pre-V6 values stay routable for cached pages.
        APPLIED: { label: 'Submitted', cls: 'status-applied' },
        UNDER_REVIEW: { label: 'Reviewing', cls: 'status-review' }
    };

    var API_ORIGIN = (window.API_BASE_URL || '/api').replace(/\/api\/?$/, '');

    var state = {
        role: 'employer',
        profile: null,
        account: null,
        notifications: []
    };

    /* ---------- helpers -------------------------------------------------- */

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function initials(name) {
        var parts = String(name || '').trim().split(/\s+/).filter(Boolean);
        if (!parts.length) return 'EN';
        return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
    }

    function money(value) {
        var n = Number(value);
        if (!isFinite(n) || n <= 0) return '';
        return '₱' + n.toLocaleString('en-PH', { maximumFractionDigits: 0 });
    }

    function salaryLabel(job) {
        var min = money(job.salaryMin);
        var max = money(job.salaryMax);
        if (min && max) return min + ' – ' + max;
        if (min) return 'From ' + min;
        if (max) return 'Up to ' + max;
        return 'Salary not stated';
    }

    function typeLabel(value) {
        if (!value) return '';
        return EMPLOYMENT_LABELS[String(value).toUpperCase()] || String(value).replace(/_/g, ' ');
    }

    function scheduleLabel(value) {
        if (!value) return '';
        return SCHEDULE_LABELS[String(value).toUpperCase()] || String(value).replace(/_/g, ' ');
    }

    function categoryLabel(value) {
        if (!value) return 'Uncategorised';
        return CATEGORY_LABELS[String(value).toUpperCase()] || String(value).replace(/_/g, ' ');
    }

    function businessTypeLabel(value) {
        if (!value) return 'Not set';
        return BUSINESS_TYPES[String(value).toUpperCase()] || String(value).replace(/_/g, ' ');
    }

    function categoryIcon(value) {
        return ICONS[String(value || '').toUpperCase()] || 'fa-briefcase';
    }

    function municipalityLabel(value) {
        if (!value) return '';
        var raw = String(value).replace(/_/g, ' ').toLowerCase();
        return raw.charAt(0).toUpperCase() + raw.slice(1);
    }

    function statusMeta(value) {
        return STATUS_META[String(value || 'SUBMITTED').toUpperCase()] || STATUS_META.SUBMITTED;
    }

    function dateLabel(value) {
        if (!value) return '—';
        var then = new Date(value).getTime();
        if (isNaN(then)) return '—';
        return new Date(then).toLocaleDateString('en-PH', { month: 'short', day: 'numeric', year: 'numeric' });
    }

    function timeAgo(value) {
        if (!value) return 'Just now';
        var then = new Date(value).getTime();
        if (isNaN(then)) return 'Just now';
        var mins = Math.round((Date.now() - then) / 60000);
        if (mins < 1) return 'Just now';
        if (mins < 60) return mins + 'm ago';
        var hours = Math.round(mins / 60);
        if (hours < 24) return hours + 'h ago';
        var days = Math.round(hours / 24);
        if (days < 30) return days + 'd ago';
        return dateLabel(value);
    }

    function photoUrl(path) {
        if (!path) return '';
        if (/^https?:/i.test(path)) return path;
        return API_ORIGIN + (path.charAt(0) === '/' ? path : '/' + path);
    }

    function toast(message, type) {
        var container = document.getElementById('toastContainer');
        if (!container) return;
        var el = document.createElement('div');
        el.className = 'toast ' + (type === 'error' ? 'toast-error' : 'toast-success');
        el.innerHTML = '<i class="fas ' + (type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-check') + '"></i><span>' + esc(message) + '</span>';
        container.appendChild(el);
        setTimeout(function () { el.remove(); }, 3600);
    }

    function safe(promise) {
        return promise.then(function (data) { return data; }, function (error) {
            return { __error: error && error.message ? error.message : 'Request failed' };
        });
    }

    function failed(result) { return !!(result && result.__error); }

    /* ---------- shell ---------------------------------------------------- */

    function pageConfig() { return window.ENUVEX_PAGE || {}; }

    function renderSidebar(active) {
        var aside = document.getElementById('sidebar');
        if (!aside) return;
        var meta = ROLE_META[state.role];
        var nav = NAVS[state.role];

        aside.innerHTML = `
            <a class="side-brand" href="dashboard.html" aria-label="JSM ${meta.label} dashboard">
                ${BRAND_MARK}
                <span class="side-brand-text">
                    <strong>JSM</strong>
                    <small>Job &amp; Skills Matching</small>
                </span>
            </a>
            <div>
                <p class="side-label">Menu</p>
                <nav class="side-nav" aria-label="${meta.label} navigation">
                    ${nav.map(function (item) {
                        var isActive = item.id === active;
                        return `<a class="side-link${isActive ? ' active' : ''}" href="${item.href}"${isActive ? ' aria-current="page"' : ''}>
                            <i class="fas ${item.icon}"></i> <span>${item.label}</span>
                        </a>`;
                    }).join('')}
                </nav>
            </div>
            <div class="side-foot">
                <a class="side-user" id="sideUserCard" href="${state.role === 'employer' ? 'profile.html' : 'dashboard.html'}">
                    <span class="avatar" id="sideAvatar">EN</span>
                    <span class="side-user-text">
                        <span class="side-user-name" id="sideUserName">${meta.label}</span>
                        <span class="side-user-role" id="sideUserRole">${meta.label}</span>
                    </span>
                </a>
                <button class="side-logout" type="button" data-logout>
                    <i class="fas fa-right-from-bracket"></i> <span>Log out</span>
                </button>
            </div>`;

        if (state.role === 'admin') {
            var card = document.getElementById('sideUserCard');
            if (card) { card.removeAttribute('href'); card.style.cursor = 'default'; }
        }
    }

    function renderTopbar(config) {
        var header = document.getElementById('topbar');
        if (!header) return;
        var meta = ROLE_META[state.role];

        var dropdownLinks = state.role === 'employer'
            ? `<a href="profile.html"><i class="fas fa-building"></i> Company Profile</a>
               <a href="jobs.html"><i class="fas fa-briefcase"></i> My Job Posts</a>
               <a href="applicants.html"><i class="fas fa-user-check"></i> Applicants</a>
               <a href="post-job.html"><i class="fas fa-circle-plus"></i> Post a Job</a>`
            : `<a href="users.html"><i class="fas fa-users"></i> User Management</a>
               <a href="employers.html"><i class="fas fa-building-circle-check"></i> Employer Verification</a>
               <a href="job-posts.html"><i class="fas fa-briefcase"></i> Job Management</a>
               <a href="geography.html"><i class="fas fa-map-location-dot"></i> Geographic Map</a>
               <a href="reports.html"><i class="fas fa-chart-column"></i> Reports &amp; Analytics</a>`;

        header.innerHTML = `
            <button class="icon-btn collapse-btn" id="collapseBtn" type="button" aria-label="Collapse sidebar" aria-expanded="true">
                <i class="fas fa-bars-staggered"></i>
            </button>
            <button class="icon-btn menu-btn" id="menuBtn" type="button" aria-label="Open navigation" aria-expanded="false">
                <i class="fas fa-bars"></i>
            </button>

            <div class="topbar-title">
                <h1>${esc(config.title || 'Dashboard')}</h1>
                <p>${esc(config.subtitle || meta.label + ' console')}</p>
            </div>

            <form class="top-search" id="globalSearch" role="search">
                <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
                <label class="visually-hidden" for="globalQuery">Search</label>
                <input type="search" id="globalQuery" name="q" placeholder="${esc(meta.searchPlaceholder)}">
                <button class="search-go" type="submit">Search</button>
            </form>

            <div class="top-actions">
                <div class="bell-wrap">
                    <button class="icon-btn" id="bellBtn" type="button" aria-label="Notifications" aria-expanded="false">
                        <i class="fas fa-bell"></i>
                    </button>
                    <span class="bell-badge" id="bellBadge" hidden>0</span>
                    <div class="dropdown" id="bellDropdown" hidden>
                        <div class="dropdown-head">
                            <strong>Notifications</strong>
                            <span id="notifSummary">No new notifications</span>
                        </div>
                        <div class="dropdown-list" id="notifList"></div>
                        <a class="dropdown-footer" href="notifications.html">View all notifications</a>
                    </div>
                </div>

                <div class="bell-wrap">
                    <button class="user-btn" id="userBtn" type="button" aria-haspopup="true" aria-expanded="false">
                        <span class="avatar" id="topAvatar">EN</span>
                        <span class="user-btn-name" id="topUserName">${meta.label}</span>
                        <i class="fas fa-chevron-down" aria-hidden="true"></i>
                    </button>
                    <div class="dropdown" id="userDropdown" hidden>
                        <div class="dropdown-head">
                            <strong id="ddName">${meta.label}</strong>
                            <span>${meta.accountLabel}</span>
                        </div>
                        ${dropdownLinks}
                        <hr>
                        <button type="button" data-logout><i class="fas fa-right-from-bracket"></i> Log out</button>
                    </div>
                </div>
            </div>`;
    }

    function renderBottomNav(active) {
        var nav = document.getElementById('bottomNav');
        if (!nav) return;
        nav.innerHTML = NAVS[state.role].map(function (item) {
            return `<a href="${item.href}"${item.id === active ? ' class="active"' : ''}>
                <i class="fas ${item.icon}"></i> ${esc(item.short)}
            </a>`;
        }).join('');
    }

    /* ---------- identity + notifications --------------------------------- */

    /** The last identity painted, so a page shows it on the first frame. */
    var PROFILE_CACHE_KEY = 'enuvex_employer_profile';

    function readCache(key) {
        try {
            var raw = sessionStorage.getItem(key);
            return raw ? JSON.parse(raw) : null;
        } catch (e) { return null; }
    }

    function writeCache(key, value) {
        try { sessionStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* storage unavailable */ }
    }

    function storedName() {
        try { return localStorage.getItem('jsm_full_name') || ''; } catch (e) { return ''; }
    }

    function displayName() {
        if (state.role === 'employer' && state.profile) {
            return state.profile.businessName || state.profile.employerName || 'Employer';
        }
        if (state.account && state.account.email) return state.account.email;
        // Written at login, so the shell never waits on a request to draw a name.
        if (storedName()) return storedName();
        return state.role === 'admin' ? 'Administrator' : 'Employer';
    }

    function paintCachedIdentity() {
        if (state.role === 'employer') {
            var cached = readCache(PROFILE_CACHE_KEY);
            if (cached) state.profile = cached;
        } else if (storedName()) {
            state.account = { email: storedName() };
        }
        paintIdentity();
    }

    function paintIdentity() {
        var name = displayName();
        var avatarPath = state.role === 'employer' && state.profile ? state.profile.logo : null;
        var roleText = state.role === 'admin'
            ? 'Administrator'
            : 'Employer' + (state.profile && state.profile.verified ? ' · Verified' : '');

        ['sideAvatar', 'topAvatar'].forEach(function (id) {
            var el = document.getElementById(id);
            if (!el) return;
            if (avatarPath) {
                el.innerHTML = '<img src="' + esc(photoUrl(avatarPath)) + '" alt="">';
            } else {
                el.textContent = initials(name);
            }
        });

        ['sideUserName', 'topUserName', 'ddName'].forEach(function (id) {
            var el = document.getElementById(id);
            if (el) el.textContent = name;
        });

        var roleEl = document.getElementById('sideUserRole');
        if (roleEl) roleEl.textContent = roleText;
    }

    function renderNotifications() {
        var list = document.getElementById('notifList');
        var badge = document.getElementById('bellBadge');
        var summary = document.getElementById('notifSummary');
        if (!list || !badge || !summary) return;

        var items = (state.notifications || []).slice().sort(function (a, b) {
            return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
        });
        var unread = items.filter(function (n) { return !JSMNotify.isRead(n); });

        if (unread.length) {
            badge.hidden = false;
            badge.textContent = unread.length > 9 ? '9+' : String(unread.length);
            summary.textContent = unread.length + ' unread';
        } else {
            badge.hidden = true;
            summary.textContent = 'No new notifications';
        }

        if (!items.length) {
            list.innerHTML = '<p class="muted" style="padding: 12px;">You have no notifications yet.</p>';
            return;
        }

        list.innerHTML = items.slice(0, 8).map(function (n) {
            return JSMNotify.itemHtml(n);
        }).join('');
    }

    /**
     * Refreshes the account shown in the shell. It deliberately runs after the
     * page has been released, so a slow profile request never holds up the
     * page's own data. The admin console reads the name stored at login instead
     * of downloading the user table just to draw a header.
     */
    async function loadIdentity() {
        if (!window.ApiClient || !ApiClient.loadAuth()) return null;

        if (state.role === 'employer') {
            var profileResult = await safe(ApiClient.getProfile());
            if (!failed(profileResult)) {
                state.profile = profileResult;
                writeCache(PROFILE_CACHE_KEY, profileResult);
                paintIdentity();
            }
        } else if (!storedName()) {
            // Only for sessions that logged in before the name was stored.
            var users = await safe(ApiClient.getAdminUsers());
            if (!failed(users) && Array.isArray(users)) {
                var myId = Number((function () {
                    try { return localStorage.getItem('jsm_user_id') || 0; } catch (e) { return 0; }
                })());
                state.account = users.filter(function (user) { return myId && user.id === myId; })[0] || null;
                paintIdentity();
            }
        }
        return state;
    }

    /** Notifications are secondary: fetched once the first render is done. */
    function loadNotifications() {
        function run() {
            if (!window.ApiClient || !ApiClient.loadAuth()) return;
            safe(ApiClient.request('/notifications')).then(function (result) {
                if (failed(result) || !Array.isArray(result)) return;
                state.notifications = result;
                renderNotifications();
            });
        }
        if (window.requestIdleCallback) window.requestIdleCallback(run, { timeout: 1200 });
        else setTimeout(run, 200);

        // New notices arrive on their own, without a full page refresh.
        JSMNotify.startPolling(function () { run(); });
    }

    /* ---------- interactions --------------------------------------------- */

    function wireShell() {
        var sidebar = document.getElementById('sidebar');
        var overlay = document.getElementById('sideOverlay');
        var menuBtn = document.getElementById('menuBtn');

        function closeSidebar() {
            if (!sidebar || !menuBtn) return;
            sidebar.classList.remove('mobile-open');
            if (overlay) overlay.classList.remove('is-visible');
            menuBtn.setAttribute('aria-expanded', 'false');
        }

        if (menuBtn && sidebar) {
            menuBtn.addEventListener('click', function () {
                var open = sidebar.classList.toggle('mobile-open');
                if (overlay) overlay.classList.toggle('is-visible', open);
                menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
            });
        }
        if (overlay) overlay.addEventListener('click', closeSidebar);
        if (sidebar) {
            sidebar.addEventListener('click', function (event) {
                if (event.target.closest('a') && window.innerWidth <= 1024) closeSidebar();
            });
        }

        var collapseBtn = document.getElementById('collapseBtn');
        function setCollapsed(collapsed) {
            document.body.classList.toggle('nav-collapsed', collapsed);
            if (!collapseBtn) return;
            collapseBtn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
            collapseBtn.setAttribute('aria-label', collapsed ? 'Expand sidebar' : 'Collapse sidebar');
            try { localStorage.setItem('enuvex_nav_collapsed', collapsed ? '1' : '0'); } catch (e) { /* storage unavailable */ }
        }
        if (collapseBtn) {
            collapseBtn.addEventListener('click', function () {
                setCollapsed(!document.body.classList.contains('nav-collapsed'));
            });
            try {
                if (localStorage.getItem('enuvex_nav_collapsed') === '1') setCollapsed(true);
            } catch (e) { /* storage unavailable */ }
        }

        var bellBtn = document.getElementById('bellBtn');
        var bellDropdown = document.getElementById('bellDropdown');
        var userBtn = document.getElementById('userBtn');
        var userDropdown = document.getElementById('userDropdown');

        function closeDropdowns() {
            if (bellDropdown) bellDropdown.hidden = true;
            if (userDropdown) userDropdown.hidden = true;
            if (bellBtn) bellBtn.setAttribute('aria-expanded', 'false');
            if (userBtn) userBtn.setAttribute('aria-expanded', 'false');
        }

        function toggle(dropdown, btn) {
            var willOpen = dropdown.hidden;
            closeDropdowns();
            dropdown.hidden = !willOpen;
            btn.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
        }

        var notifList = document.getElementById('notifList');
        if (notifList) {
            notifList.addEventListener('click', function (event) {
                var row = event.target.closest('[data-notification-id]');
                if (!row) return;
                JSMNotify.markRead(row.getAttribute('data-notification-id'));
                row.classList.remove('notif-unread');
                state.notifications.forEach(function (n) {
                    if (String(n.id) === row.getAttribute('data-notification-id')) n.isRead = true;
                });
                renderNotifications();
            });
        }

        if (bellBtn && bellDropdown) {
            bellBtn.addEventListener('click', function (event) {
                event.stopPropagation();
                toggle(bellDropdown, bellBtn);
            });
        }
        if (userBtn && userDropdown) {
            userBtn.addEventListener('click', function (event) {
                event.stopPropagation();
                toggle(userDropdown, userBtn);
            });
        }

        document.addEventListener('click', closeDropdowns);
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') { closeDropdowns(); closeSidebar(); }
        });

        document.querySelectorAll('[data-logout]').forEach(function (button) {
            button.addEventListener('click', function () {
                if (window.ApiClient) ApiClient.clearAuth();
                try {
                    ['jsm_token', 'jsm_refresh', 'jsm_user_type', 'jsm_user_id', 'jsm_full_name',
                        'accessToken', 'refreshToken', 'userType', 'userName', 'userId'].forEach(function (key) {
                        localStorage.removeItem(key);
                    });
                } catch (e) { /* storage unavailable */ }
                window.location.href = '../../login.html';
            });
        });

        var globalSearch = document.getElementById('globalSearch');
        if (globalSearch) {
            globalSearch.addEventListener('submit', function (event) {
                event.preventDefault();
                var input = document.getElementById('globalQuery');
                var query = input ? input.value.trim() : '';
                window.location.href = ROLE_META[state.role].searchHref + (query ? '?q=' + encodeURIComponent(query) : '');
            });
        }
    }

    /* ---------- employer data -------------------------------------------- */

    /**
     * The API lists applicants per job, so the consoles read the employer's job
     * posts once and then fetch each job's applicants. Nothing is invented: a
     * job whose applicants cannot be read simply contributes no rows.
     */
    async function loadEmployerData() {
        var jobsResult = await safe(ApiClient.getMyJobs());
        if (failed(jobsResult)) return { jobs: [], applicants: [], error: jobsResult.__error };

        var jobs = Array.isArray(jobsResult) ? jobsResult : [];
        var lists = await Promise.all(jobs.map(function (job) {
            return safe(ApiClient.getApplicants(job.id, 'match'));
        }));

        var applicants = [];
        lists.forEach(function (result, index) {
            if (failed(result) || !Array.isArray(result)) return;
            var job = jobs[index];
            result.forEach(function (applicant) {
                applicants.push(Object.assign({}, applicant, {
                    jobId: job.id,
                    jobTitle: job.jobTitle || applicant.jobTitle
                }));
            });
        });

        return { jobs: jobs, applicants: applicants, error: null };
    }

    function applicantRow(applicant) {
        var name = applicant.fullName || 'Applicant';
        var match = Number(applicant.matchPercentage);
        var matchCls = !isFinite(match) ? 'low' : match >= 75 ? '' : match >= 55 ? 'mid' : 'low';
        var status = statusMeta(applicant.applicationStatus);
        return `<div class="list-row">
            <span class="avatar">${esc(initials(name))}</span>
            <div class="list-body">
                <h4><a href="applicant-profile.html?applicationId=${encodeURIComponent(applicant.applicationId || '')}">${esc(name)}</a></h4>
                <div class="list-meta">
                    <span><i class="fas fa-briefcase"></i>${esc(applicant.jobTitle || '—')}</span>
                    <span><i class="fas fa-location-dot"></i>${esc(municipalityLabel(applicant.location) || 'Location not set')}</span>
                    <span><i class="fas fa-calendar"></i>Applied ${esc(dateLabel(applicant.appliedAt))}</span>
                </div>
            </div>
            <div class="list-side">
                ${isFinite(match) ? `<span class="match-pill ${matchCls}">${Math.round(match)}%</span>` : ''}
                <span class="status ${status.cls}">${esc(status.label)}</span>
                <a class="btn-ghost btn-sm" href="applicant-profile.html?applicationId=${encodeURIComponent(applicant.applicationId || '')}">Review</a>
            </div>
        </div>`;
    }

    /* ---------- public surface ------------------------------------------- */

    window.ROLE = {
        esc: esc,
        initials: initials,
        money: money,
        salaryLabel: salaryLabel,
        typeLabel: typeLabel,
        scheduleLabel: scheduleLabel,
        categoryLabel: categoryLabel,
        categoryIcon: categoryIcon,
        businessTypeLabel: businessTypeLabel,
        municipalityLabel: municipalityLabel,
        statusMeta: statusMeta,
        dateLabel: dateLabel,
        timeAgo: timeAgo,
        photoUrl: photoUrl,
        toast: toast,
        safe: safe,
        failed: failed,
        loadEmployerData: loadEmployerData,
        applicantRow: applicantRow,
        state: state
    };

    var HOME_BY_ROLE = {
        JOB_SEEKER: '../../job-seeker/dashboard.html',
        EMPLOYER: '../../employer/dashboard.html',
        ADMIN: '../../admin/dashboard.html'
    };

    /**
     * The server rejects every call a role is not allowed to make; this only
     * stops the wrong console from rendering at all when someone edits the URL.
     * Returns true when navigation was started.
     */
    function redirectForWrongRole() {
        var type = '';
        try { type = String(localStorage.getItem('jsm_user_type') || '').toUpperCase(); } catch (e) { return false; }
        if (!type) return false;

        var expected = state.role === 'admin' ? 'ADMIN' : 'EMPLOYER';
        if (type === expected) return false;

        window.location.replace(HOME_BY_ROLE[type] || '../../login.html');
        return true;
    }

    function boot() {
        state.role = document.body.getAttribute('data-role') || 'employer';
        if (redirectForWrongRole()) return;
        var config = pageConfig();
        renderSidebar(config.active);
        renderTopbar(config);
        renderBottomNav(config.active);
        wireShell();
        paintCachedIdentity();

        // Hand the page over immediately: its own API calls then run alongside
        // the identity and notification calls instead of queueing behind them.
        setTimeout(function () {
            document.dispatchEvent(new CustomEvent('enuvex:ready', { detail: state }));
        }, 0);

        loadIdentity().then(function (identity) {
            document.dispatchEvent(new CustomEvent('enuvex:identity', { detail: identity }));
        });
        loadNotifications();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
