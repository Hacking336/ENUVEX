/* Shared full notifications page for the seeker, employer and admin consoles. */
(function () {
    'use strict';

    var state = { filter: 'all', type: '', items: [], loaded: false };
    var list, empty, errorBox, errorText;

    function esc(value) { return JSMNotify.esc(value); }

    function when(value) {
        if (!value) return '';
        var date = new Date(value);
        if (isNaN(date.getTime())) return '';
        return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) +
            ' — ' + date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    }

    function typeLabel(type) {
        var labels = {
            APPLICATION_SUBMITTED: 'Application submitted',
            APPLICATION_STATUS: 'Application status',
            APPLICATION_WITHDRAWN: 'Application withdrawn',
            APPLICATION_RECEIVED: 'New application',
            APPLICANT_WITHDRAWN: 'Applicant withdrew',
            INTERVIEW_INVITE: 'Interview invitation',
            INTERVIEW_RESCHEDULED: 'Interview rescheduled',
            INTERVIEW_CANCELLED: 'Interview cancelled',
            JOB_RECOMMENDATION: 'Job recommendation',
            EMPLOYER_VERIFICATION: 'Employer verification'
        };
        return labels[String(type || '').toUpperCase()] || 'Notice';
    }

    function render() {
        if (!state.items.length) {
            list.innerHTML = '';
            empty.hidden = false;
            empty.innerHTML = state.loaded
                ? `<i class="fas fa-bell-slash"></i><strong>No notifications here</strong><p>${state.filter === 'unread' ? 'You have read everything.' : 'Notices about your applications, interviews and matches will appear here.'}</p>`
                : '';
            return;
        }
        empty.hidden = true;

        list.innerHTML = state.items.map(function (item) {
            var unread = !JSMNotify.isRead(item);
            var link = JSMNotify.linkFor(item);
            return `<article class="inbox-item${unread ? ' is-unread' : ''}" data-id="${esc(item.id)}"${link ? ' data-link="' + esc(link) + '"' : ''}>
                <div class="notif-icon"><i class="fas ${JSMNotify.icon(item.type)}"></i></div>
                <div class="inbox-body">
                    <strong>${esc(item.title || typeLabel(item.type))}</strong>
                    <p>${esc(item.message || '')}</p>
                    <time>${esc(when(item.createdAt))}${unread ? ' · Unread' : ''}</time>
                </div>
                <div class="inbox-actions">
                    ${unread ? `<button class="icon-btn" type="button" data-mark="${esc(item.id)}" aria-label="Mark as read" title="Mark as read"><i class="fas fa-check"></i></button>` : ''}
                    ${link ? `<button class="icon-btn" type="button" data-open="${esc(item.id)}" aria-label="Open" title="Open"><i class="fas fa-arrow-up-right-from-square"></i></button>` : ''}
                    <button class="icon-btn" type="button" data-delete="${esc(item.id)}" aria-label="Delete notification" title="Delete"><i class="fas fa-trash"></i></button>
                </div>
            </article>`;
        }).join('');
    }

    async function load() {
        if (!window.ApiClient || !ApiClient.loadAuth()) {
            list.innerHTML = '';
            empty.hidden = false;
            empty.innerHTML = '<i class="fas fa-lock"></i><strong>Sign in to see your notifications</strong><a class="btn-solid btn-sm" href="../../login.html">Sign in</a>';
            return;
        }

        errorBox.hidden = true;
        var query = '?filter=' + encodeURIComponent(state.filter) +
            (state.type ? '&type=' + encodeURIComponent(state.type) : '');

        try {
            var result = await ApiClient.request('/notifications' + query);
            state.items = Array.isArray(result) ? result : [];
            state.loaded = true;
            render();
        } catch (error) {
            errorBox.hidden = false;
            errorText.textContent = error.message || 'Could not load your notifications.';
            list.innerHTML = '';
        }
    }

    async function markAll() {
        try {
            await ApiClient.markAllNotificationsRead();
            state.items.forEach(function (item) { item.isRead = true; });
            render();
        } catch (error) {
            errorText.textContent = error.message || 'Could not mark notifications read.';
            errorBox.hidden = false;
        }
    }

    function wire() {
        list.addEventListener('click', async function (event) {
            var mark = event.target.closest('[data-mark]');
            var remove = event.target.closest('[data-delete]');
            var open = event.target.closest('[data-open]');
            var row = event.target.closest('[data-id]');

            if (mark) {
                event.stopPropagation();
                try {
                    await ApiClient.markNotificationRead(mark.getAttribute('data-mark'));
                    state.items.forEach(function (item) {
                        if (String(item.id) === mark.getAttribute('data-mark')) item.isRead = true;
                    });
                    render();
                } catch (error) { /* leaves the row unread */ }
                return;
            }

            if (remove) {
                event.stopPropagation();
                try {
                    await ApiClient.deleteNotification(remove.getAttribute('data-delete'));
                    state.items = state.items.filter(function (item) {
                        return String(item.id) !== remove.getAttribute('data-delete');
                    });
                    render();
                } catch (error) { /* keep the row */ }
                return;
            }

            if (row) {
                var id = row.getAttribute('data-id');
                var link = row.getAttribute('data-link');
                var item = state.items.filter(function (entry) { return String(entry.id) === id; })[0];
                if (item && !JSMNotify.isRead(item)) {
                    JSMNotify.markRead(id);
                    item.isRead = true;
                    render();
                }
                if (open || link) {
                    var target = link || (item && JSMNotify.linkFor(item));
                    if (target) window.location.href = target;
                }
            }
        });

        document.getElementById('inboxTabs').addEventListener('click', function (event) {
            var tab = event.target.closest('[data-filter]');
            if (!tab) return;
            state.filter = tab.getAttribute('data-filter');
            document.querySelectorAll('#inboxTabs .tab').forEach(function (button) {
                var active = button === tab;
                button.classList.toggle('is-active', active);
                button.setAttribute('aria-selected', active ? 'true' : 'false');
            });
            load();
        });

        document.getElementById('typeFilter').addEventListener('change', function (event) {
            state.type = event.target.value;
            load();
        });

        document.getElementById('markAllBtn').addEventListener('click', markAll);
    }

    document.addEventListener('DOMContentLoaded', function () {
        list = document.getElementById('inboxList');
        empty = document.getElementById('inboxEmpty');
        errorBox = document.getElementById('pageError');
        errorText = document.getElementById('pageErrorText');
        wire();
        load();
        JSMNotify.startPolling(function () { load(); });
    });
})();
