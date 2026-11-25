/* ==========================================================================
   JSM — shared match presentation
   Renders the score, its classification, the matched/missing skills and the
   per-criterion explanation exactly as the backend calculated them. No score is
   ever computed here: the page only formats the numbers it received.
   ========================================================================== */

(function () {
    'use strict';

    var BANDS = [
        { min: 90, label: 'Excellent Match', short: 'Excellent', cls: 'excellent' },
        { min: 75, label: 'Strong Match', short: 'Strong', cls: 'strong' },
        { min: 60, label: 'Good Match', short: 'Good', cls: 'good' },
        { min: 40, label: 'Partial Match', short: 'Partial', cls: 'partial' },
        { min: 0, label: 'Low Match', short: 'Low', cls: 'low' }
    ];

    var CRITERION_ICONS = {
        skills: 'fa-star',
        location: 'fa-location-dot',
        employmentType: 'fa-briefcase',
        experience: 'fa-briefcase-clock',
        salary: 'fa-peso-sign'
    };

    var STATUS_META = {
        MATCH: { icon: 'fa-circle-check', cls: 'is-match', label: 'Match' },
        PARTIAL: { icon: 'fa-circle-half-stroke', cls: 'is-partial', label: 'Partial' },
        NONE: { icon: 'fa-circle-xmark', cls: 'is-none', label: 'No match' },
        UNAVAILABLE: { icon: 'fa-circle-minus', cls: 'is-na', label: 'Not enough data' }
    };

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function band(score) {
        var value = Number(score);
        if (!isFinite(value)) return null;
        for (var i = 0; i < BANDS.length; i += 1) {
            if (value >= BANDS[i].min) return BANDS[i];
        }
        return BANDS[BANDS.length - 1];
    }

    function pill(score) {
        var value = Number(score);
        if (!isFinite(value)) return '';
        var info = band(value);
        return '<span class="match-pill ' + info.cls + '">'
            + '<strong>' + Math.round(value) + '%</strong> ' + esc(info.short)
            + '</span>';
    }

    /** The full "Matched skills / Skills you may need" block, from the server's lists. */
    function skillsBlock(job) {
        var matched = Array.isArray(job.matchedSkills) ? job.matchedSkills : [];
        var missing = Array.isArray(job.missingSkills) ? job.missingSkills : [];
        if (!matched.length && !missing.length) return '';

        return '<div class="match-skills">'
            + '<div class="match-skill-col">'
            + '<span class="match-skill-title"><i class="fas fa-circle-check"></i> Matched skills</span>'
            + (matched.length
                ? '<div class="chips">' + matched.map(function (skill) {
                    return '<span class="chip chip-match">' + esc(skill) + '</span>';
                }).join('') + '</div>'
                : '<p class="match-skill-empty">None of the required skills are on your profile yet.</p>')
            + '</div>'
            + '<div class="match-skill-col">'
            + '<span class="match-skill-title"><i class="fas fa-circle-plus"></i> Skills you may need</span>'
            + (missing.length
                ? '<div class="chips">' + missing.map(function (skill) {
                    return '<span class="chip chip-missing">' + esc(skill) + '</span>';
                }).join('') + '</div>'
                : '<p class="match-skill-empty">You have every required skill.</p>')
            + '</div>'
            + '</div>';
    }

    function criteriaRows(criteria) {
        if (!Array.isArray(criteria) || !criteria.length) return '';
        return criteria.map(function (item) {
            var meta = STATUS_META[String(item.status || '').toUpperCase()] || STATUS_META.UNAVAILABLE;
            var points = item.applicable
                ? '<span class="match-points">' + round(item.earnedPoints) + ' / ' + round(item.weight) + '</span>'
                : '<span class="match-points is-na">not counted</span>';
            return '<li class="match-row ' + meta.cls + '">'
                + '<span class="match-row-icon"><i class="fas ' + meta.icon + '"></i></span>'
                + '<span class="match-row-body">'
                + '<strong>' + esc(item.label || item.key) + '</strong>'
                + '<span>' + esc(item.detail || '') + '</span>'
                + '</span>'
                + points
                + '</li>';
        }).join('');
    }

    /** Expandable "Why this job matches you" built from the server's criteria. */
    function explanation(job, options) {
        var criteria = job.matchCriteria;
        if (!Array.isArray(criteria) || !criteria.length) return '';
        var opts = options || {};
        var applicable = criteria.filter(function (item) { return item.applicable; });
        var totalWeight = applicable.reduce(function (sum, item) { return sum + Number(item.weight || 0); }, 0);
        var earned = applicable.reduce(function (sum, item) { return sum + Number(item.earnedPoints || 0); }, 0);
        var skipped = criteria.length - applicable.length;

        return '<details class="match-why">'
            + '<summary><i class="fas fa-circle-question"></i> Why this job matches you</summary>'
            + '<ul class="match-rows">' + criteriaRows(criteria) + '</ul>'
            + '<p class="match-formula">'
            + 'Score = ' + round(earned) + ' points earned ÷ ' + round(totalWeight)
            + ' available points × 100 = <strong>' + round(job.matchPercentage) + '%</strong>.'
            + (skipped ? ' ' + skipped + ' criteria ' + (skipped === 1 ? 'was' : 'were')
                + ' left out because the data is missing on one side.' : '')
            + '</p>'
            + (opts.employer ? '' : '<p class="match-note">This score helps you prioritise, it never blocks an application.</p>')
            + '</details>';
    }

    function summaryLine(job) {
        var value = Number(job.matchPercentage);
        if (!isFinite(value)) return '';
        var info = band(value);
        return '<div class="match-summary ' + info.cls + '">'
            + '<span class="match-summary-score">' + Math.round(value) + '%</span>'
            + '<span class="match-summary-text"><strong>' + esc(info.label) + '</strong>'
            + '<small>Based on ' + (job.matchedSkills || []).length + ' matched and '
            + (job.missingSkills || []).length + ' missing required skills</small></span>'
            + '</div>';
    }

    function round(value) {
        var n = Number(value);
        if (!isFinite(n)) return '0';
        return Math.round(n * 10) / 10;
    }

    window.JSMMatch = {
        bands: BANDS,
        band: band,
        pill: pill,
        skillsBlock: skillsBlock,
        explanation: explanation,
        summaryLine: summaryLine
    };
})();
