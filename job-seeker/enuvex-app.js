/* ==========================================================================
   ENUVEX — Job Seeker shared shell
   Renders the sidebar, top header, notification menu and mobile navigation so
   every job seeker page uses exactly the same layout and identity.
   Requires: ../assets/js/api.js (ApiClient)
   ========================================================================== */

(function () {
    'use strict';

    var NAV = [
        { id: 'dashboard', href: 'dashboard.html', icon: 'fa-house', label: 'Dashboard', short: 'Home', mobileIcon: 'fa-house' },
        { id: 'jobs', href: 'jobs.html', icon: 'fa-magnifying-glass', label: 'Search Jobs', short: 'Search', mobileIcon: 'fa-magnifying-glass' },
        { id: 'map', href: 'map.html', icon: 'fa-map-location-dot', label: 'Job Map', short: 'Map', mobileIcon: 'fa-map' },
        { id: 'applications', href: 'applications.html', icon: 'fa-file-lines', label: 'Applications', short: 'Applications', mobileIcon: 'fa-file-lines' },
        { id: 'skills', href: 'skills.html', icon: 'fa-chart-line', label: 'Skills & Career', short: 'Skills', mobileIcon: 'fa-chart-line' },
        { id: 'saved', href: 'saved-jobs.html', icon: 'fa-bookmark', label: 'Saved Jobs', short: 'Saved', mobileIcon: 'fa-bookmark' },
        { id: 'reports', href: 'reports.html', icon: 'fa-flag', label: 'My Reports', short: 'Reports', mobileIcon: 'fa-flag' },
        { id: 'profile', href: 'profile.html', icon: 'fa-user', label: 'Profile', short: 'Profile', mobileIcon: 'fa-user' }
    ];

    // One official icon file feeds every sidebar, so the mark cannot drift.
    var BRAND_MARK = '<img src="../assets/brand/jsm-icon.svg" alt="" width="40" height="40">';

    var EMPLOYMENT_LABELS = {
        FULL_TIME: 'Full-time',
        PART_TIME: 'Part-time',
        CONTRACT: 'Contract',
        TEMPORARY: 'Temporary',
        INTERNSHIP: 'Internship',
        FREELANCE: 'Freelance',
        OJT: 'OJT'
    };

    var CATEGORY_LABELS = {
        RETAIL_STORE: 'Retail / Store',
        SALES: 'Sales',
        FOOD_SERVICE_RESTAURANT: 'Food Service / Restaurant',
        HOSPITALITY_TOURISM: 'Hospitality / Tourism',
        OFFICE_ADMINISTRATION: 'Office / Administration',
        ACCOUNTING_FINANCE: 'Accounting / Finance',
        HEALTHCARE: 'Healthcare',
        EDUCATION_TUTORING: 'Education / Tutoring',
        CONSTRUCTION_SKILLED_TRADES: 'Construction / Skilled Trades',
        AGRICULTURE: 'Agriculture',
        FISHERIES: 'Fisheries',
        MANUFACTURING: 'Manufacturing',
        LOGISTICS_DELIVERY: 'Logistics / Delivery',
        TRANSPORTATION: 'Transportation',
        AUTOMOTIVE_MECHANICAL: 'Automotive / Mechanical',
        CUSTOMER_SERVICE: 'Customer Service',
        SECURITY: 'Security',
        GENERAL_LABOR: 'General Labor',
        IT_TECHNOLOGY: 'IT / Technology',
        OTHER: 'Other'
    };

    var CATEGORY_ICONS = {
        RETAIL_STORE: 'fa-store',
        SALES: 'fa-tags',
        FOOD_SERVICE_RESTAURANT: 'fa-utensils',
        HOSPITALITY_TOURISM: 'fa-hotel',
        OFFICE_ADMINISTRATION: 'fa-folder-open',
        ACCOUNTING_FINANCE: 'fa-calculator',
        HEALTHCARE: 'fa-pills',
        EDUCATION_TUTORING: 'fa-chalkboard-user',
        CONSTRUCTION_SKILLED_TRADES: 'fa-hard-hat',
        AGRICULTURE: 'fa-tractor',
        FISHERIES: 'fa-fish',
        MANUFACTURING: 'fa-industry',
        LOGISTICS_DELIVERY: 'fa-truck-fast',
        TRANSPORTATION: 'fa-van-shuttle',
        AUTOMOTIVE_MECHANICAL: 'fa-wrench',
        CUSTOMER_SERVICE: 'fa-headset',
        SECURITY: 'fa-shield-halved',
        GENERAL_LABOR: 'fa-briefcase',
        IT_TECHNOLOGY: 'fa-laptop-code',
        OTHER: 'fa-briefcase'
    };

    var SCHEDULE_LABELS = {
        MORNING: 'Morning',
        AFTERNOON: 'Afternoon',
        EVENING: 'Evening',
        NIGHT: 'Night',
        WEEKEND: 'Weekend',
        FLEXIBLE: 'Flexible'
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

    var EDUCATION_LABELS = {
        NO_FORMAL_EDUCATION: 'No Formal Education',
        ELEMENTARY_GRADUATE: 'Elementary Graduate',
        HIGH_SCHOOL_GRADUATE: 'High School Graduate',
        VOCATIONAL_TECHNICAL: 'Vocational / Technical',
        COLLEGE_GRADUATE: 'College Graduate',
        POST_GRADUATE: 'Post Graduate'
    };

    var AVAILABILITY_LABELS = {
        IMMEDIATELY: 'Immediately',
        WITHIN_1_WEEK: 'Within 1 week',
        WITHIN_2_WEEKS: 'Within 2 weeks',
        WITHIN_1_MONTH: 'Within 1 month',
        WITHIN_3_MONTHS: 'Within 3 months',
        MORE_THAN_3_MONTHS: 'More than 3 months'
    };

    var API_ORIGIN = (window.API_BASE_URL || '/api').replace(/\/api\/?$/, '');

    var state = {
        profile: null,
        notifications: [],
        savedIds: [],
        userLoaded: false
    };

    /* ---------- helpers -------------------------------------------------- */

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function initials(name) {
        var parts = String(name || '').trim().split(/\s+/).filter(Boolean);
        if (!parts.length) return 'JS';
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
        return '';
    }

    function titleCase(value) {
        return String(value || '').toLowerCase().replace(/[_-]/g, ' ').replace(/\b\w/g, function (c) {
            return c.toUpperCase();
        });
    }

    function typeLabel(value) {
        if (!value) return '';
        var key = String(value).toUpperCase().replace(/[\s-]/g, '_');
        return EMPLOYMENT_LABELS[key] || titleCase(value);
    }

    function municipalityLabel(value) {
        return value ? titleCase(value) : '';
    }

    function categoryLabel(value) {
        if (!value) return '';
        return CATEGORY_LABELS[String(value).toUpperCase()] || titleCase(value);
    }

    function scheduleLabel(value) {
        if (!value) return '';
        return SCHEDULE_LABELS[String(value).toUpperCase()] || titleCase(value);
    }

    function educationLabel(value) {
        if (!value) return '';
        return EDUCATION_LABELS[String(value).toUpperCase()] || titleCase(value);
    }

    function availabilityLabel(value) {
        if (!value) return '';
        return AVAILABILITY_LABELS[String(value).toUpperCase()] || titleCase(value);
    }

    function statusMeta(value) {
        var key = String(value || 'APPLIED').toUpperCase();
        return STATUS_META[key] || { label: titleCase(key), cls: 'status-applied' };
    }

    function postedLabel(value) {
        var then = new Date(value).getTime();
        if (!value || !isFinite(then)) return '';
        return 'Posted ' + new Date(then).toLocaleDateString('en-PH', { month: 'short', day: 'numeric' });
    }

    function dateLabel(value) {
        var then = new Date(value).getTime();
        if (!value || !isFinite(then)) return '';
        return new Date(then).toLocaleDateString('en-PH', { month: 'short', day: 'numeric', year: 'numeric' });
    }

    function timeAgo(value) {
        if (!value) return '';
        var then = new Date(value).getTime();
        if (!isFinite(then)) return '';
        var mins = Math.round((Date.now() - then) / 60000);
        if (mins < 1) return 'Just now';
        if (mins < 60) return mins + ' min ago';
        var hours = Math.round(mins / 60);
        if (hours < 24) return hours + ' hour' + (hours === 1 ? '' : 's') + ' ago';
        var days = Math.round(hours / 24);
        if (days < 7) return days + ' day' + (days === 1 ? '' : 's') + ' ago';
        return dateLabel(value);
    }

    function photoUrl(photo) {
        if (!photo) return '';
        if (/^(https?:)?\/\//.test(photo) || photo.charAt(0) === '/') return photo;
        return API_ORIGIN + '/uploads/' + encodeURIComponent(photo);
    }

    function avatarHtml(profile, extraClass) {
        var name = profile && profile.fullName;
        var photo = profile && profile.profilePhoto;
        var cls = 'avatar' + (extraClass ? ' ' + extraClass : '');
        if (photo) {
            return '<span class="' + cls + '"><img src="' + esc(photoUrl(photo)) + '" alt="' + esc(name || 'Profile photo') + '"></span>';
        }
        return '<span class="' + cls + '">' + esc(initials(name)) + '</span>';
    }

    function jobIcon(category) {
        return CATEGORY_ICONS[String(category || '').toUpperCase()] || 'fa-briefcase';
    }

    function toast(message, type) {
        var container = document.getElementById('toastContainer');
        if (!container) return;
        var el = document.createElement('div');
        el.className = 'toast toast-' + (type || 'info');
        el.innerHTML = '<i class="fas ' + (type === 'success' ? 'fa-circle-check' : type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-info') + '"></i><span>' + esc(message) + '</span>';
        container.appendChild(el);
        requestAnimationFrame(function () { el.classList.add('show'); });
        setTimeout(function () {
            el.classList.remove('show');
            setTimeout(function () { el.remove(); }, 250);
        }, 3200);
    }

    function safe(promise) {
        return promise.then(function (value) { return value; }).catch(function (error) {
            return { __error: error };
        });
    }

    function failed(result) { return !!(result && result.__error); }

    function isSaved(jobId) {
        return state.savedIds.indexOf(Number(jobId)) !== -1;
    }

    function setSavedIds(list) {
        state.savedIds = (list || []).map(function (entry) {
            if (entry == null) return NaN;
            if (typeof entry === 'number') return entry;
            if (entry.jobPostId != null) return Number(entry.jobPostId);
            if (entry.job && entry.job.id != null) return Number(entry.job.id);
            if (entry.id != null) return Number(entry.id);
            return NaN;
        }).filter(function (id) { return isFinite(id); });
    }

    /* ---------- job card ------------------------------------------------- */

    function jobCard(job, options) {
        var opts = options || {};
        var location = [municipalityLabel(job.municipality), job.barangay].filter(Boolean).join(', ');
        var salary = salaryLabel(job);
        var posted = postedLabel(job.createdAt);
        var type = typeLabel(job.employmentType);
        var schedule = scheduleLabel(job.workSchedule);
        var skills = String(job.requiredSkills || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean).slice(0, 3);
        var match = Number(job.matchPercentage);
        var saved = isSaved(job.id);
        var savedNote = opts.showSavedDate && opts.savedAt ? '<span class="saved-flag"><i class="fas fa-bookmark"></i>Saved ' + esc(dateLabel(opts.savedAt)) + '</span>' : '';

        return `
            <article class="job" data-job-id="${esc(job.id)}">
                <div class="job-icon"><i class="fas ${jobIcon(job.category)}"></i></div>
                <div class="job-body">
                    <div class="job-head">
                        <div>
                            <a class="job-title" href="job-details.html?id=${encodeURIComponent(job.id)}">${esc(job.jobTitle || 'Untitled role')}</a>
                            <div class="job-company">${esc(job.companyName || 'Employer')}</div>
                        </div>
                        ${isFinite(match) && match > 0 && window.JSMMatch ? JSMMatch.pill(match) : ''}
                    </div>
                    <div class="job-meta">
                        ${location ? `<span><i class="fas fa-location-dot"></i>${esc(location)}</span>` : ''}
                        ${salary ? `<span><i class="fas fa-peso-sign"></i>${esc(salary)}</span>` : ''}
                        ${job.distanceKm != null ? `<span><i class="fas fa-route"></i>${Number(job.distanceKm) < 1 ? Math.round(Number(job.distanceKm) * 1000) + ' m' : Number(job.distanceKm).toFixed(Number(job.distanceKm) < 10 ? 1 : 0) + ' km'} away</span>` : ''}
                        ${posted ? `<span><i class="far fa-calendar"></i>${esc(posted)}</span>` : ''}
                    </div>
                    ${type || schedule || skills.length ? `<div class="job-tags">
                        ${type ? `<span class="tag type">${esc(type)}</span>` : ''}
                        ${schedule ? `<span class="tag">${esc(schedule)}</span>` : ''}
                        ${skills.map(function (skill) { return `<span class="tag">${esc(skill)}</span>`; }).join('')}
                    </div>` : ''}
                    ${window.JSMMatch ? JSMMatch.skillsBlock(job) + JSMMatch.explanation(job) : ''}
                </div>
                <div class="job-actions">
                    <button class="btn-solid btn-sm" type="button" data-apply="${esc(job.id)}">Apply Now</button>
                    <button class="btn-soft btn-sm" type="button" data-save="${esc(job.id)}" data-saved="${saved ? '1' : '0'}">
                        <i class="${saved ? 'fas' : 'far'} fa-bookmark"></i> ${saved ? 'Saved' : 'Save'}
                    </button>
                    ${savedNote}
                </div>
            </article>`;
    }

    /* ---------- shell rendering ------------------------------------------ */

    function shellConfig() {
        return window.ENUVEX_PAGE || {};
    }

    function renderSidebar(active) {
        var aside = document.getElementById('sidebar');
        if (!aside) return;

        aside.innerHTML = `
            <a class="side-brand" href="dashboard.html" aria-label="JSM dashboard">
                ${BRAND_MARK}
                <span class="side-brand-text">
                    <strong>JSM</strong>
                    <small>Job &amp; Skills Matching</small>
                </span>
            </a>
            <div>
                <p class="side-label">Menu</p>
                <nav class="side-nav" aria-label="Job seeker navigation">
                    ${NAV.map(function (item) {
                        var isActive = item.id === active;
                        return `<a class="side-link${isActive ? ' active' : ''}" href="${item.href}"${isActive ? ' aria-current="page"' : ''}>
                            <i class="fas ${item.icon}"></i> <span>${item.label}</span>
                        </a>`;
                    }).join('')}
                </nav>
            </div>
            <div class="side-foot">
                <a class="side-user" href="profile.html">
                    <span class="avatar" id="sideAvatar">JS</span>
                    <span class="side-user-text">
                        <span class="side-user-name" id="sideUserName">Job Seeker</span>
                        <span class="side-user-role">Job Seeker</span>
                    </span>
                </a>
                <button class="side-logout" type="button" data-logout>
                    <i class="fas fa-right-from-bracket"></i> <span>Log out</span>
                </button>
            </div>`;
    }

    function renderTopbar(config) {
        var header = document.getElementById('topbar');
        if (!header) return;

        header.innerHTML = `
            <button class="icon-btn collapse-btn" id="collapseBtn" type="button" aria-label="Collapse sidebar" aria-expanded="true">
                <i class="fas fa-bars-staggered"></i>
            </button>

            <button class="icon-btn menu-btn" id="menuBtn" type="button" aria-label="Open navigation" aria-expanded="false">
                <i class="fas fa-bars"></i>
            </button>

            <div class="topbar-title">
                <h1>${esc(config.title || 'Dashboard')}</h1>
                <p>${esc(config.subtitle || 'Job & Skills Matching')}</p>
            </div>

            <form class="top-search" id="globalSearch" role="search">
                <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
                <label class="visually-hidden" for="globalQuery">Search jobs</label>
                <input type="search" id="globalQuery" name="q" placeholder="${esc(config.searchPlaceholder || 'Search jobs, skills, or companies…')}">
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
                        <span class="avatar" id="topAvatar">JS</span>
                        <span class="user-btn-name" id="topUserName">Job Seeker</span>
                        <i class="fas fa-chevron-down" aria-hidden="true"></i>
                    </button>
                    <div class="dropdown" id="userDropdown" hidden>
                        <div class="dropdown-head">
                            <strong id="ddName">Job Seeker</strong>
                            <span>Job Seeker account</span>
                        </div>
                        <a href="profile.html"><i class="fas fa-user"></i> My Profile</a>
                        <a href="applications.html"><i class="fas fa-file-lines"></i> My Applications</a>
                        <a href="saved-jobs.html"><i class="fas fa-bookmark"></i> Saved Jobs</a>
                        <a href="jobs.html"><i class="fas fa-magnifying-glass"></i> Search Jobs</a>
                        <hr>
                        <button type="button" data-logout><i class="fas fa-right-from-bracket"></i> Log out</button>
                    </div>
                </div>
            </div>`;
    }

    function renderBottomNav(active) {
        var nav = document.getElementById('bottomNav');
        if (!nav) return;
        var items = NAV.filter(function (item) { return item.id !== 'saved'; });
        nav.innerHTML = items.map(function (item) {
            return `<a href="${item.href}"${item.id === active ? ' class="active"' : ''}>
                <i class="fas ${item.mobileIcon}"></i> ${esc(item.short)}
            </a>`;
        }).join('');
    }

    /* ---------- identity + notifications --------------------------------- */

    function paintIdentity() {
        var profile = state.profile;
        var name = (profile && profile.fullName) || 'Job Seeker';

        ['sideAvatar', 'topAvatar'].forEach(function (id) {
            var el = document.getElementById(id);
            if (!el) return;
            var photo = profile && profile.profilePhoto;
            if (photo) {
                el.innerHTML = `<img src="${esc(photoUrl(photo))}" alt="">`;
            } else {
                el.textContent = initials(name);
            }
        });

        ['sideUserName', 'topUserName', 'ddName'].forEach(function (id) {
            var el = document.getElementById(id);
            if (el) el.textContent = name;
        });
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

    async function loadIdentity() {
        var authenticated = window.ApiClient && ApiClient.loadAuth();
        if (!authenticated) {
            state.userLoaded = true;
            paintIdentity();
            document.dispatchEvent(new CustomEvent('enuvex:user', { detail: null }));
            return;
        }

        var results = await Promise.all([
            safe(ApiClient.getProfile()),
            safe(ApiClient.request('/notifications')),
            safe(ApiClient.getSavedJobs())
        ]);

        if (!failed(results[0])) state.profile = results[0];
        if (!failed(results[1]) && Array.isArray(results[1])) state.notifications = results[1];
        if (!failed(results[2]) && Array.isArray(results[2])) setSavedIds(results[2]);

        state.userLoaded = true;
        paintIdentity();
        renderNotifications();
        document.dispatchEvent(new CustomEvent('enuvex:user', { detail: state.profile }));
    }

    /** Keeps the bell current without a manual refresh. */
    function watchNotifications() {
        JSMNotify.startPolling(function () {
            if (!window.ApiClient || !ApiClient.loadAuth()) return;
            safe(ApiClient.request('/notifications')).then(function (result) {
                if (failed(result) || !Array.isArray(result)) return;
                state.notifications = result;
                renderNotifications();
            });
        });
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
                var id = row.getAttribute('data-notification-id');
                JSMNotify.markRead(id);
                row.classList.remove('notif-unread');
                (state.notifications || []).forEach(function (n) {
                    if (String(n.id) === id) n.isRead = true;
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
                try { localStorage.clear(); } catch (e) { /* storage unavailable */ }
                if (window.ApiClient) ApiClient.clearAuth();
                window.location.href = '../../login.html';
            });
        });

        var globalSearch = document.getElementById('globalSearch');
        if (globalSearch) {
            globalSearch.addEventListener('submit', function (event) {
                event.preventDefault();
                var query = document.getElementById('globalQuery').value.trim();
                window.location.href = 'jobs.html' + (query ? '?q=' + encodeURIComponent(query) : '');
            });
        }
    }

    /** Wires Apply / Save buttons rendered by jobCard(). */
    function wireJobActions(onChange) {
        document.body.addEventListener('click', async function (event) {
            var saveBtn = event.target.closest('[data-save]');
            if (saveBtn) {
                event.preventDefault();
                var jobId = saveBtn.getAttribute('data-save');
                var wasSaved = isSaved(jobId);
                saveBtn.disabled = true;
                try {
                    if (wasSaved) {
                        await ApiClient.unsaveJob(jobId);
                        state.savedIds = state.savedIds.filter(function (id) { return id !== Number(jobId); });
                        toast('Removed from saved jobs.', 'success');
                    } else {
                        await ApiClient.saveJob(jobId);
                        state.savedIds.push(Number(jobId));
                        toast('Job saved.', 'success');
                    }
                    if (typeof onChange === 'function') onChange('saved', jobId);
                } catch (error) {
                    toast(error.message || 'Could not update saved jobs.', 'error');
                } finally {
                    saveBtn.disabled = false;
                }
                return;
            }

            var applyBtn = event.target.closest('[data-apply]');
            if (applyBtn) {
                event.preventDefault();
                var id = applyBtn.getAttribute('data-apply');
                applyBtn.disabled = true;
                try {
                    await ApiClient.applyForJob(id);
                    toast('Application submitted.', 'success');
                    if (typeof onChange === 'function') onChange('applied', id);
                } catch (error) {
                    toast(error.message || 'Could not submit your application.', 'error');
                } finally {
                    applyBtn.disabled = false;
                }
            }
        });
    }

    /* ---------- public API ------------------------------------------------ */

    function init() {
        var config = shellConfig();
        renderSidebar(config.active || '');
        renderTopbar(config);
        renderBottomNav(config.active || '');
        wireShell();
        loadIdentity();
        watchNotifications();
    }

    window.ENUVEX = {
        state: state,
        nav: NAV,
        esc: esc,
        initials: initials,
        money: money,
        salaryLabel: salaryLabel,
        typeLabel: typeLabel,
        municipalityLabel: municipalityLabel,
        categoryLabel: categoryLabel,
        scheduleLabel: scheduleLabel,
        educationLabel: educationLabel,
        availabilityLabel: availabilityLabel,
        statusMeta: statusMeta,
        postedLabel: postedLabel,
        dateLabel: dateLabel,
        timeAgo: timeAgo,
        photoUrl: photoUrl,
        avatarHtml: avatarHtml,
        jobIcon: jobIcon,
        jobCard: jobCard,
        toast: toast,
        safe: safe,
        failed: failed,
        isSaved: isSaved,
        setSavedIds: setSavedIds,
        wireJobActions: wireJobActions,
        loadIdentity: loadIdentity,
        renderNotifications: renderNotifications
    };

    document.addEventListener('DOMContentLoaded', init);
})();
