// Job & Skills Matching System - Layout & Navigation Logic

document.addEventListener('DOMContentLoaded', function() {
    // Initialize all layout components
    initSidebar();
    initBottomNav();
    initMobileMenu();
    initUserMenu();
    initNotifications();
    initPageSpecific();
});

/* ===== SIDEBAR NAVIGATION ===== */

const navigationConfig = {
    job_seeker: [
        { href: 'dashboard.html', icon: 'fa-home', label: 'Home', active: false },
        { href: 'jobs.html', icon: 'fa-search', label: 'Search', active: false },
        { href: 'applications.html', icon: 'fa-file-alt', label: 'Applications', active: false },
        { href: 'profile.html', icon: 'fa-user', label: 'Profile', active: false }
    ],
    employer: [
        { href: 'dashboard.html', icon: 'fa-home', label: 'Dashboard', active: false },
        { href: 'jobs.html', icon: 'fa-briefcase', label: 'My Jobs', active: false },
        { href: 'post-job.html', icon: 'fa-plus', label: 'Post a Job', active: false },
        { href: 'applicants.html', icon: 'fa-user-search', label: 'Applicants', active: false },
        { href: 'profile.html', icon: 'fa-user', label: 'Profile', active: false }
    ],
    admin: [
        { href: 'dashboard.html', icon: 'fa-tachometer-alt', label: 'Dashboard', active: false },
        { href: 'users.html', icon: 'fa-users', label: 'Users', active: false },
        { href: 'employers.html', icon: 'fa-building', label: 'Employers', active: false },
        { href: 'job-posts.html', icon: 'fa-briefcase', label: 'Job Posts', active: false },
        { href: 'reports.html', icon: 'fa-chart-bar', label: 'Reports', active: false }
    ]
};

const bottomNavConfig = {
    job_seeker: [
        { href: 'dashboard.html', icon: 'fa-home', label: 'Home' },
        { href: 'jobs.html', icon: 'fa-search', label: 'Search' },
        { href: 'applications.html', icon: 'fa-file-alt', label: 'Applications' },
        { href: 'profile.html', icon: 'fa-user', label: 'Profile' }
    ],
    employer: [
        { href: 'dashboard.html', icon: 'fa-home', label: 'Dashboard' },
        { href: 'jobs.html', icon: 'fa-briefcase', label: 'Jobs' },
        { href: 'applicants.html', icon: 'fa-user-search', label: 'Applicants' },
        { href: 'profile.html', icon: 'fa-user', label: 'Profile' }
    ],
    admin: [
        { href: 'dashboard.html', icon: 'fa-tachometer-alt', label: 'Dashboard' },
        { href: 'users.html', icon: 'fa-users', label: 'Users' },
        { href: 'job-posts.html', icon: 'fa-briefcase', label: 'Jobs' },
        { href: 'reports.html', icon: 'fa-chart-bar', label: 'Reports' }
    ]
};

function initSidebar() {
    const sidebarNav = document.getElementById('sidebarNav');
    if (!sidebarNav) return;

    // Detect user type from URL or localStorage
    const userType = detectUserType();
    const navItems = navigationConfig[userType] || navigationConfig.job_seeker;

    // Set active state based on current page
    const currentPath = window.location.pathname.split('/').pop() || 'dashboard.html';
    navItems.forEach(item => {
        item.active = item.href === currentPath;
    });

    sidebarNav.innerHTML = navItems.map(item => `
        <a href="${item.href}" class="sidebar-nav-item ${item.active ? 'active' : ''}" data-page="${item.href}">
            <div class="nav-icon">
                <i class="fas ${item.icon}"></i>
            </div>
            <span>${item.label}</span>
        </a>
    `).join('');

    // Handle navigation clicks
    sidebarNav.addEventListener('click', function(e) {
        const link = e.target.closest('a.sidebar-nav-item');
        if (link) {
            // Update active state
            document.querySelectorAll('.sidebar-nav-item').forEach(l => l.classList.remove('active'));
            link.classList.add('active');
        }
    });
}

function initBottomNav() {
    const bottomNav = document.getElementById('bottomNav');
    if (!bottomNav) return;

    const userType = detectUserType();
    const navItems = bottomNavConfig[userType] || bottomNavConfig.job_seeker;
    const currentPath = window.location.pathname.split('/').pop() || 'dashboard.html';

    bottomNav.innerHTML = navItems.map(item => `
        <a href="${item.href}" class="bottom-nav-item ${item.href === currentPath ? 'active' : ''}">
            <div class="nav-icon"><i class="fas ${item.icon}"></i></div>
            <span>${item.label}</span>
        </a>
    `).join('');
}

/* ===== MOBILE MENU ===== */

function initMobileMenu() {
    const mobileMenuBtn = document.getElementById('mobileMenuBtn');
    const sidebar = document.getElementById('sidebar');
    const sidebarOverlay = document.getElementById('sidebarOverlay');

    if (mobileMenuBtn && sidebar) {
        mobileMenuBtn.addEventListener('click', function() {
            sidebar.classList.toggle('mobile-open');
            if (sidebarOverlay) sidebarOverlay.classList.toggle('active');
        });
    }

    if (sidebarOverlay) {
        sidebarOverlay.addEventListener('click', function() {
            sidebar.classList.remove('mobile-open');
            sidebarOverlay.classList.remove('active');
        });
    }

    // Close sidebar when clicking a link on mobile
    document.addEventListener('click', function(e) {
        const link = e.target.closest('.sidebar-nav-item');
        if (link && window.innerWidth <= 1024) {
            sidebar.classList.remove('mobile-open');
            if (sidebarOverlay) sidebarOverlay.classList.remove('active');
        }
    });
}

/* ===== USER MENU ===== */

function initUserMenu() {
    const userMenuBtn = document.getElementById('userMenuBtn');
    const notificationBtn = document.getElementById('notificationBtn');

    // Simple dropdown for user menu
    if (userMenuBtn) {
        userMenuBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            showUserDropdown(this);
        });
    }

    if (notificationBtn) {
        notificationBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            showNotificationsDropdown(this);
        });
    }

    // Close dropdowns when clicking outside
    document.addEventListener('click', function() {
        closeAllDropdowns();
    });
}

function showUserDropdown(btn) {
    closeAllDropdowns();
    const dropdown = document.createElement('div');
    dropdown.className = 'user-dropdown';
    dropdown.style.cssText = `
        position: absolute;
        top: calc(100% + 8px);
        right: 0;
        min-width: 200px;
        background: white;
        border-radius: var(--border-radius);
        box-shadow: var(--box-shadow-lg);
        border: 1px solid var(--gray-200);
        padding: 0.5rem 0;
        z-index: 1000;
        animation: fadeInUp 0.2s ease;
    `;
    dropdown.innerHTML = `
        <div style="padding: 0.75rem 1rem; border-bottom: 1px solid var(--gray-200);">
            <div style="font-weight: 600;">Maria Santos</div>
            <div style="font-size: 0.8rem; color: var(--gray-500);">Job Seeker</div>
        </div>
        <a href="profile.html" class="dropdown-item" style="display: flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1rem; color: var(--dark-color);">
            <i class="fas fa-user" style="width: 20px;"></i> Profile
        </a>
        <a href="settings.html" class="dropdown-item" style="display: flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1rem; color: var(--dark-color);">
            <i class="fas fa-cog" style="width: 20px;"></i> Settings
        </a>
        <div style="border-top: 1px solid var(--gray-200); padding: 0.5rem 0;">
            <a href="../../index.html" class="dropdown-item" style="display: flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1rem; color: var(--danger-color);">
                <i class="fas fa-sign-out-alt" style="width: 20px;"></i> Logout
            </a>
        </div>
    `;
    document.body.appendChild(dropdown);

    const rect = btn.getBoundingClientRect();
    dropdown.style.top = `${rect.bottom + window.scrollY + 8}px`;
    dropdown.style.left = `${rect.left - dropdown.offsetWidth + rect.width}px`;

    // Add dropdown-item hover styles
    const style = document.createElement('style');
    style.textContent = `
        .dropdown-item:hover { background: var(--gray-100); color: var(--primary-color) !important; }
    `;
    document.head.appendChild(style);
}

function showNotificationsDropdown(btn) {
    closeAllDropdowns();
    const dropdown = document.createElement('div');
    dropdown.className = 'notifications-dropdown';
    dropdown.style.cssText = `
        position: absolute;
        top: calc(100% + 8px);
        right: 0;
        width: 320px;
        background: white;
        border-radius: var(--border-radius);
        box-shadow: var(--box-shadow-lg);
        border: 1px solid var(--gray-200);
        z-index: 1000;
        animation: fadeInUp 0.2s ease;
    `;
    dropdown.innerHTML = `
        <div style="padding: 1rem; border-bottom: 1px solid var(--gray-200); display: flex; justify-content: space-between; align-items: center;">
            <h4 style="font-size: 0.95rem; font-weight: 600;">Notifications</h4>
            <a href="#" style="font-size: 0.8rem; color: var(--primary-color);">Mark all read</a>
        </div>
        <div style="max-height: 300px; overflow-y: auto;">
            <div class="notification-item" style="padding: 1rem; border-bottom: 1px solid var(--gray-100); display: flex; gap: 0.75rem;">
                <div class="notification-icon" style="width: 36px; height: 36px; border-radius: 50%; background: rgba(67, 97, 238, 0.1); display: flex; align-items: center; justify-content: center; color: var(--primary-color);"><i class="fas fa-briefcase"></i></div>
                <div class="flex-1">
                    <div style="font-weight: 600; font-size: 0.85rem;">New job match found</div>
                    <div style="font-size: 0.75rem; color: var(--gray-500);">Restaurant Crew at Jollybee Foods - 88% match</div>
                    <div style="font-size: 0.7rem; color: var(--gray-400); margin-top: 0.25rem;">2 hours ago</div>
                </div>
            </div>
            <div class="notification-item" style="padding: 1rem; border-bottom: 1px solid var(--gray-100); display: flex; gap: 0.75rem;">
                <div class="notification-icon" style="width: 36px; height: 36px; border-radius: 50%; background: rgba(42, 157, 143, 0.1); display: flex; align-items: center; justify-content: center; color: #2a9d8f;"><i class="fas fa-check-circle"></i></div>
                <div class="flex-1">
                    <div style="font-weight: 600; font-size: 0.85rem;">Application accepted</div>
                    <div style="font-size: 0.75rem; color: var(--gray-500);">You were accepted for Pharmacy Assistant at Daet Health Pharmacy</div>
                    <div style="font-size: 0.7rem; color: var(--gray-400); margin-top: 0.25rem;">Yesterday</div>
                </div>
            </div>
            <div class="notification-item" style="padding: 1rem; display: flex; gap: 0.75rem;">
                <div class="notification-icon" style="width: 36px; height: 36px; border-radius: 50%; background: rgba(248, 150, 30, 0.1); display: flex; align-items: center; justify-content: center; color: var(--warning-color);"><i class="fas fa-star"></i></div>
                <div class="flex-1">
                    <div style="font-weight: 600; font-size: 0.85rem;">Shortlisted for interview</div>
                    <div style="font-size: 0.75rem; color: var(--gray-500);">Driver / Delivery at Daet Logistics</div>
                    <div style="font-size: 0.7rem; color: var(--gray-400); margin-top: 0.25rem;">3 days ago</div>
                </div>
            </div>
        </div>
        <div style="padding: 1rem; text-align: center; border-top: 1px solid var(--gray-200);">
            <a href="notifications.html" style="font-size: 0.85rem; color: var(--primary-color);">View all notifications</a>
        </div>
    `;
    document.body.appendChild(dropdown);

    const rect = btn.getBoundingClientRect();
    dropdown.style.top = `${rect.bottom + window.scrollY + 8}px`;
    dropdown.style.left = `${rect.left - dropdown.offsetWidth + rect.width}px`;
}

function closeAllDropdowns() {
    document.querySelectorAll('.user-dropdown, .notifications-dropdown').forEach(d => d.remove());
}

/* ===== NOTIFICATIONS ===== */

function initNotifications() {
    // Notification system is initialized via showToast function
    window.showToast = function(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.innerHTML = `
            <i class="fas ${type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle'}"></i>
            <span>${message}</span>
        `;
        container.appendChild(toast);

        // Trigger reflow
        toast.offsetHeight;

        toast.classList.add('show');

        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    };
}

/* ===== PAGE SPECIFIC INIT ===== */

function initPageSpecific() {
    // Password toggles
    document.querySelectorAll('.password-toggle').forEach(toggle => {
        toggle.addEventListener('click', function() {
            const input = this.previousElementSibling;
            const type = input.getAttribute('type') === 'password' ? 'text' : 'password';
            input.setAttribute('type', type);
            this.classList.toggle('fa-eye');
            this.classList.toggle('fa-eye-slash');
        });
    });

    // Form validation
    document.querySelectorAll('form.validate').forEach(form => {
        form.addEventListener('submit', function(e) {
            let isValid = true;
            this.querySelectorAll('[required]').forEach(field => {
                if (!field.value.trim()) {
                    isValid = false;
                    field.classList.add('is-invalid');
                    if (!field.nextElementSibling?.classList.contains('invalid-feedback')) {
                        const errorDiv = document.createElement('div');
                        errorDiv.className = 'invalid-feedback';
                        errorDiv.textContent = 'This field is required';
                        field.parentNode.insertBefore(errorDiv, field.nextSibling);
                    }
                } else {
                    field.classList.remove('is-invalid');
                    const errorDiv = field.nextElementSibling;
                    if (errorDiv?.classList.contains('invalid-feedback')) {
                        errorDiv.remove();
                    }
                }
            });
            if (!isValid) e.preventDefault();
        });
    });

    // Match percentage circle styling
    document.querySelectorAll('.match-circle').forEach(circle => {
        const percent = parseInt(circle.textContent);
        if (percent >= 80) circle.classList.add('high');
        else if (percent >= 60) circle.classList.add('medium');
        else circle.classList.add('low');
    });
}

/* ===== UTILITY FUNCTIONS ===== */

function detectUserType() {
    // Try to get from URL path
    const path = window.location.pathname;
    if (path.includes('/employer/')) return 'employer';
    if (path.includes('/admin/')) return 'admin';
    return 'job_seeker';
}

function getCurrentPage() {
    return window.location.pathname.split('/').pop() || 'dashboard.html';
}

// Export for use in other scripts
window.LayoutUtils = {
    detectUserType,
    getCurrentPage,
    showToast: window.showToast
};