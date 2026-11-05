/**
 * Landing page behaviour for ENUVEX (index.html).
 *
 * The featured listings mirror the demo data used by job-seeker/jobs.html, so the
 * marketing page and the in-app job list stay consistent until real posts exist.
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

    var sampleJobs = [
        {
            company: 'AgriTech Solutions',
            icon: 'fa-leaf',
            tone: '',
            title: 'Agricultural Field Specialist',
            location: 'Davao City, PH',
            salary: '\u20B125,000 \u2013 \u20B135,000 / month',
            type: 'Full-time',
            mode: 'On-site'
        },
        {
            company: 'GreenHarvest Farms',
            icon: 'fa-seedling',
            tone: 'tone-b',
            title: 'Livestock Farm Technician',
            location: 'Cagayan de Oro City, PH',
            salary: '\u20B118,000 \u2013 \u20B125,000 / month',
            type: 'Full-time',
            mode: 'On-site'
        },
        {
            company: 'EcoBuild Construction',
            icon: 'fa-mountain-sun',
            tone: 'tone-c',
            title: 'Environmental Compliance Officer',
            location: 'Quezon City, PH',
            salary: '\u20B130,000 \u2013 \u20B145,000 / month',
            type: 'Full-time',
            mode: 'Hybrid'
        },
        {
            company: 'Solaris Energy',
            icon: 'fa-bolt',
            tone: 'tone-d',
            title: 'Renewable Energy Technician',
            location: 'Iloilo City, PH',
            salary: '\u20B120,000 \u2013 \u20B130,000 / month',
            type: 'Full-time',
            mode: 'On-site'
        }
    ];

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
        sampleJobs.forEach(function (job) {
            grid.appendChild(buildCard(job));
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

            if (query) { params.set('skills', query); }
            if (location) { params.set('municipality', location); }

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
