/**
 * Landing page behaviour for JSM (index.html).
 *
 * The featured listings are read from the live job table, so the marketing page
 * and the in-app job list always show the same openings.
 */
(function () {
    'use strict';

    var token = localStorage.getItem('jsm_token');

    /* ---------- Navigation: point job links at the right place ---------- */

    document.querySelectorAll('.js-jobs-link').forEach(function (link) {
        link.setAttribute('href', token ? 'job-seeker/jobs.html' : 'login.html');
        if (!token) {
            link.setAttribute('title', 'Sign in to browse all job posts');
        }
    });

    var navToggle = document.getElementById('navToggle');
    var siteNav = document.getElementById('siteNav');

    if (navToggle && siteNav) {
        navToggle.addEventListener('click', function () {
            var open = siteNav.classList.toggle('is-open');
            navToggle.setAttribute('aria-expanded', String(open));
        });
    }

    var careerDropdown = document.getElementById('careerDropdown');
    if (careerDropdown) {
        var careerButton = careerDropdown.querySelector('button');
        careerButton.addEventListener('click', function () {
            var open = careerDropdown.classList.toggle('is-open');
            careerButton.setAttribute('aria-expanded', String(open));
        });
    }

    /* ---------- Featured jobs ---------- */

    // The cards are built from the live job table, newest first, so the landing
    // page can never advertise a vacancy that does not exist. /api/jobs is a
    // public read-only endpoint and only returns active posts.
    var CATEGORY_ICONS = {
        RETAIL_STORE: 'fa-store', SALES: 'fa-tags', FOOD_SERVICE_RESTAURANT: 'fa-utensils',
        HOSPITALITY_TOURISM: 'fa-hotel', OFFICE_ADMINISTRATION: 'fa-folder-open',
        ACCOUNTING_FINANCE: 'fa-calculator', HEALTHCARE: 'fa-pills',
        EDUCATION_TUTORING: 'fa-chalkboard-user', CONSTRUCTION_SKILLED_TRADES: 'fa-hard-hat',
        AGRICULTURE: 'fa-leaf', FISHERIES: 'fa-fish', MANUFACTURING: 'fa-industry',
        LOGISTICS_DELIVERY: 'fa-truck-fast', TRANSPORTATION: 'fa-van-shuttle',
        AUTOMOTIVE_MECHANICAL: 'fa-wrench', CUSTOMER_SERVICE: 'fa-headset',
        SECURITY: 'fa-shield-halved', GENERAL_LABOR: 'fa-briefcase',
        IT_TECHNOLOGY: 'fa-laptop-code', OTHER: 'fa-briefcase'
    };

    var TONES = ['', 'tone-b', 'tone-c', 'tone-d'];

    var EMPLOYMENT_LABELS = {
        FULL_TIME: 'Full-time', PART_TIME: 'Part-time', CONTRACT: 'Contract', TEMPORARY: 'Temporary'
    };

    var SCHEDULE_LABELS = {
        MORNING: 'Morning', AFTERNOON: 'Afternoon', EVENING: 'Evening',
        NIGHT: 'Night', WEEKEND: 'Weekend', FLEXIBLE: 'Flexible'
    };

    function peso(value) {
        var n = Number(value);
        return isFinite(n) && n > 0 ? '\u20B1' + n.toLocaleString('en-PH', { maximumFractionDigits: 0 }) : '';
    }

    function salaryText(job) {
        var min = peso(job.salaryMin);
        var max = peso(job.salaryMax);
        if (min && max) { return min + ' \u2013 ' + max + ' / month'; }
        if (min) { return 'From ' + min + ' / month'; }
        if (max) { return 'Up to ' + max + ' / month'; }
        return 'Salary not stated';
    }

    function place(job) {
        var parts = [job.municipality ? titleCase(job.municipality) : '', job.barangay || ''].filter(Boolean);
        return parts.length ? parts.join(', ') : 'Camarines Norte';
    }

    function fromApi(job, index) {
        return {
            company: job.companyName || 'Employer',
            icon: CATEGORY_ICONS[String(job.category || '').toUpperCase()] || 'fa-briefcase',
            tone: TONES[index % TONES.length],
            title: job.jobTitle || 'Untitled role',
            location: place(job),
            salary: salaryText(job),
            type: EMPLOYMENT_LABELS[String(job.employmentType || '').toUpperCase()] || 'Open position',
            mode: SCHEDULE_LABELS[String(job.workSchedule || '').toUpperCase()] || titleCase(job.municipality || '')
        };
    }

    var grid = document.getElementById('featuredGrid');

    function buildCard(job) {
        var card = document.createElement('article');
        card.className = 'job-card';

        var top = document.createElement('div');
        top.className = 'job-card-top';

        var avatar = document.createElement('span');
        avatar.className = 'company-avatar ' + (job.tone || '');
        avatar.innerHTML = '<i class="fas ' + job.icon + '"></i>';

        var company = document.createElement('span');
        company.className = 'company-name';
        company.textContent = job.company;

        var fav = document.createElement('button');
        fav.type = 'button';
        fav.className = 'fav-btn';
        fav.setAttribute('aria-label', 'Save ' + job.title);
        fav.innerHTML = '<i class="far fa-heart"></i>';
        fav.addEventListener('click', function () {
            if (!token) {
                showToast('Create a free account to save jobs.');
                return;
            }
            var saved = fav.classList.toggle('is-saved');
            fav.innerHTML = saved ? '<i class="fas fa-heart"></i>' : '<i class="far fa-heart"></i>';
            fav.setAttribute('aria-pressed', String(saved));
        });

        top.appendChild(avatar);
        top.appendChild(company);
        top.appendChild(fav);

        var title = document.createElement('h3');
        title.className = 'job-title';
        title.textContent = job.title;

        var meta = document.createElement('p');
        meta.className = 'job-meta';

        var loc = document.createElement('span');
        loc.innerHTML = '<i class="fas fa-location-dot"></i>';
        loc.appendChild(document.createTextNode(job.location));

        var pay = document.createElement('span');
        pay.innerHTML = '<i class="fas fa-wallet"></i>';
        pay.appendChild(document.createTextNode(job.salary));

        meta.appendChild(loc);
        meta.appendChild(pay);

        var tags = document.createElement('div');
        tags.className = 'job-tags';

        var type = document.createElement('span');
        type.className = 'pill pill-type';
        type.textContent = job.type;

        var mode = document.createElement('span');
        mode.className = 'pill pill-mode';
        mode.textContent = job.mode;

        tags.appendChild(type);
        tags.appendChild(mode);

        card.appendChild(top);
        card.appendChild(title);
        card.appendChild(meta);
        card.appendChild(tags);
        return card;
    }

    if (grid) {
        fetch('/api/jobs?page=0&size=4&sortBy=createdAt&sortDir=DESC')
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (page) {
                var jobs = (page && page.content) || [];
                grid.innerHTML = '';
                if (!jobs.length) {
                    grid.innerHTML = '<p class="featured-empty">No positions are open right now. '
                        + '<a href="register.html">Create an account</a> and your profile is ready when local employers post.</p>';
                    return;
                }
                jobs.slice(0, 4).forEach(function (job, index) {
                    grid.appendChild(buildCard(fromApi(job, index)));
                });
            })
            .catch(function () {
                grid.innerHTML = '<p class="featured-empty">Featured jobs could not be loaded. Please refresh in a moment.</p>';
            });
    }

    /* ---------- Locations dropdown ---------- */

    var locationSelect = document.getElementById('jobLocation');

    function addLocationOption(value) {
        var option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        locationSelect.appendChild(option);
    }

    function titleCase(value) {
        return value.charAt(0) + value.slice(1).toLowerCase();
    }

    if (locationSelect) {
        fetch('/api/municipalities')
            .then(function (response) { return response.ok ? response.json() : null; })
            .then(function (list) {
                if (!Array.isArray(list) || !list.length) { return; }
                list.forEach(function (municipality) {
                    if (typeof municipality === 'string') { addLocationOption(titleCase(municipality)); }
                });
            })
            .catch(function () {
                // The select keeps its "Any Location" default if the API is unreachable.
            });
    }

    /* ---------- Search ---------- */

    var searchForm = document.getElementById('heroSearch');

    if (searchForm) {
        searchForm.addEventListener('submit', function (event) {
            event.preventDefault();

            if (!token) {
                showToast('Sign in or create a free account to search jobs.');
                window.setTimeout(function () { window.location.href = 'login.html'; }, 900);
                return;
            }

            var params = new URLSearchParams();
            var query = document.getElementById('jobQuery').value.trim();
            var location = locationSelect ? locationSelect.value : '';

            // jobs.html reads the keyword as "q" and the municipality as the
            // API enum value, so the landing search lands on a filtered list
            // instead of an unfiltered one.
            if (query) { params.set('q', query); }
            if (location) { params.set('municipality', location.toUpperCase()); }

            window.location.href = 'job-seeker/jobs.html' + (params.toString() ? '?' + params.toString() : '');
        });
    }

    /* ---------- Toast ---------- */

    var toastTimer = null;

    function showToast(message) {
        var toast = document.getElementById('landingToast');
        if (!toast) { return; }

        toast.textContent = message;
        toast.classList.add('is-visible');

        window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(function () {
            toast.classList.remove('is-visible');
        }, 3200);
    }

    // Exposed for inline handlers and easy console testing.
    window.landingShowToast = showToast;
})();
