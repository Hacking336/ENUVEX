/* ==========================================================================
   JSM — shared Leaflet map module
   One implementation of the map used by the Job Map, the Map View in Search
   Jobs, the employer workplace picker and the admin geographic page.

   Tiles come from the backend's /api/geo/map-config, so no page names a tile
   provider and the provider can be changed with environment variables. Only
   jobs with confirmed coordinates are ever plotted; nothing here invents a
   location.
   ========================================================================== */

(function () {
    'use strict';

    var API_BASE = (window.API_BASE_URL || '/api').replace(/\/$/, '');
    var LEAFLET_VERSION = '1.9.4';
    var LEAFLET_CSS = 'https://unpkg.com/leaflet@' + LEAFLET_VERSION + '/dist/leaflet.css';
    var LEAFLET_JS = 'https://unpkg.com/leaflet@' + LEAFLET_VERSION + '/dist/leaflet.js';
    var CLUSTER_CSS = 'https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css';
    var CLUSTER_DEFAULT_CSS = 'https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css';
    var CLUSTER_JS = 'https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js';

    var configPromise = null;
    var leafletPromise = null;
    var boundaryCache = new Map();
    var jobCountPromise = null;

    var MUNICIPALITY_STYLE = {
        color: '#102B4C',
        weight: 1.6,
        opacity: 0.75,
        fillColor: '#2FA247',
        fillOpacity: 0.12
    };
    var MUNICIPALITY_ACTIVE_STYLE = {
        color: '#25853A',
        weight: 2.4,
        opacity: 1,
        fillColor: '#2FA247',
        fillOpacity: 0.26
    };
    var BARANGAY_STYLE = {
        color: '#102B4C',
        weight: 1,
        opacity: 0.4,
        dashArray: '4 4',
        fillColor: '#EAF5EC',
        fillOpacity: 0.14
    };

    /* ---------- loading --------------------------------------------------- */

    function loadStyle(href) {
        return new Promise(function (resolve) {
            var existing = document.querySelector('link[data-jsm-map="' + href + '"]');
            if (existing) {
                if (existing.sheet) return resolve();
                existing.addEventListener('load', function () { resolve(); });
                existing.addEventListener('error', function () { resolve(); });
                return;
            }
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = href;
            link.setAttribute('data-jsm-map', href);
            link.onload = function () { resolve(); };
            link.onerror = function () { resolve(); };
            document.head.appendChild(link);
        });
    }

    function loadScript(src) {
        return new Promise(function (resolve, reject) {
            var existing = document.querySelector('script[data-jsm-map="' + src + '"]');
            if (existing) {
                if (existing.dataset.loaded === '1') return resolve();
                existing.addEventListener('load', function () { resolve(); });
                existing.addEventListener('error', function () { reject(new Error('Could not load ' + src)); });
                return;
            }
            var script = document.createElement('script');
            script.src = src;
            script.async = true;
            script.setAttribute('data-jsm-map', src);
            script.onload = function () {
                script.dataset.loaded = '1';
                resolve();
            };
            script.onerror = function () {
                reject(new Error('Could not load the map library. Check your connection and try again.'));
            };
            document.head.appendChild(script);
        });
    }

    /** Loads Leaflet (and clustering when available) once per page. */
    function ensureLeaflet() {
        if (leafletPromise) return leafletPromise;
        if (window.L && window.L.map) {
            leafletPromise = Promise.resolve(window.L);
            return leafletPromise;
        }
        leafletPromise = Promise.all([
            loadStyle(LEAFLET_CSS),
            loadScript(LEAFLET_JS)
        ]).then(function () {
            if (!window.L || !window.L.map) {
                throw new Error('The map library did not start.');
            }
            // Clustering is an enhancement: if the plugin cannot be fetched,
            // markers still render individually rather than the map breaking.
            return Promise.all([
                loadStyle(CLUSTER_CSS),
                loadStyle(CLUSTER_DEFAULT_CSS),
                loadScript(CLUSTER_JS)
            ]).catch(function () { return null; }).then(function () {
                return window.L;
            });
        });
        return leafletPromise;
    }

    /* ---------- data ------------------------------------------------------ */

    async function fetchJson(path) {
        var response;
        try {
            response = await fetch(API_BASE + path, {
                headers: { Accept: 'application/json' },
                credentials: 'same-origin'
            });
        } catch (error) {
            throw new Error('Cannot reach the JSM server. Check your connection and try again.');
        }
        if (!response.ok) {
            var message = 'The map data could not be loaded (' + response.status + ').';
            try {
                var body = await response.json();
                if (body && (body.message || body.error)) message = body.message || body.error;
            } catch (e) { /* not JSON */ }
            throw new Error(message);
        }
        return response.json();
    }

    function config() {
        if (!configPromise) {
            configPromise = fetchJson('/geo/map-config').catch(function (error) {
                configPromise = null;
                throw error;
            });
        }
        return configPromise;
    }

    function jobCounts() {
        if (!jobCountPromise) {
            jobCountPromise = fetchJson('/geo/job-counts').catch(function (error) {
                jobCountPromise = null;
                throw error;
            });
        }
        return jobCountPromise;
    }

    /** Boundary layer, cached for the life of the page. */
    function boundaries(level, municipality) {
        var key = level + ':' + (municipality || 'all');
        if (!boundaryCache.has(key)) {
            var query = '/geo/boundaries?level=' + encodeURIComponent(level)
                + (municipality ? '&municipality=' + encodeURIComponent(municipality) : '');
            var promise = fetchJson(query).catch(function (error) {
                boundaryCache.delete(key);
                throw error;
            });
            boundaryCache.set(key, promise);
        }
        return boundaryCache.get(key);
    }

    /* ---------- helpers --------------------------------------------------- */

    function esc(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function money(value) {
        var number = Number(value);
        if (!isFinite(number) || number <= 0) return '';
        return '₱' + number.toLocaleString('en-PH', { maximumFractionDigits: 0 });
    }

    function salaryLabel(job) {
        var min = money(job.salaryMin);
        var max = money(job.salaryMax);
        if (min && max) return min + ' – ' + max;
        if (min) return 'From ' + min;
        if (max) return 'Up to ' + max;
        return '';
    }

    function typeLabel(value) {
        if (!value) return '';
        return String(value).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function (letter) {
            return letter.toUpperCase();
        });
    }

    function municipalityLabel(value) {
        if (!value) return '';
        var raw = String(value).replace(/_/g, ' ').toLowerCase();
        return raw.charAt(0).toUpperCase() + raw.slice(1);
    }

    function dateLabel(value) {
        if (!value) return '';
        var date = new Date(value);
        if (isNaN(date.getTime())) return '';
        return date.toLocaleDateString('en-PH', { month: 'short', day: 'numeric', year: 'numeric' });
    }

    function distanceLabel(job) {
        var distance = Number(job.distanceKm);
        if (!isFinite(distance)) return '';
        if (distance < 1) return Math.round(distance * 1000) + ' m away';
        return distance.toFixed(distance < 10 ? 1 : 0) + ' km away';
    }

    function haversineKm(lat1, lng1, lat2, lng2) {
        var radius = 6371.0088;
        var toRad = Math.PI / 180;
        var dLat = (lat2 - lat1) * toRad;
        var dLng = (lng2 - lng1) * toRad;
        var a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad)
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /* ---------- map shell -------------------------------------------------- */

    async function createMap(container, options) {
        var opts = options || {};
        var L = await ensureLeaflet();
        var settings = await config();
        var element = typeof container === 'string' ? document.getElementById(container) : container;
        if (!element) throw new Error('The map container is missing.');

        var map = L.map(element, {
            zoomControl: opts.zoomControl !== false,
            scrollWheelZoom: opts.scrollWheelZoom !== false,
            minZoom: settings.minZoom,
            maxZoom: settings.maxZoom,
            attributionControl: true
        }).setView(
            [opts.center ? opts.center[0] : settings.center.lat,
             opts.center ? opts.center[1] : settings.center.lng],
            opts.zoom || settings.zoom
        );

        var tileLayer = L.tileLayer(settings.tileUrl, {
            attribution: settings.attribution,
            minZoom: settings.minZoom,
            maxZoom: settings.maxZoom,
            maxNativeZoom: Math.min(settings.maxZoom, 19)
        });

        var tileFailed = false;
        tileLayer.on('tileerror', function () {
            if (!tileFailed && typeof opts.onTileError === 'function') {
                tileFailed = true;
                opts.onTileError();
            }
        });
        tileLayer.addTo(map);

        var legend = L.control({ position: 'bottomleft' });
        legend.onAdd = function () {
            var div = L.DomUtil.create('div', 'jsm-legend');
            div.innerHTML = '<strong>Legend</strong>'
                + '<span><i class="swatch swatch--job"></i> Job with a confirmed location</span>'
                + '<span><i class="swatch swatch--muni"></i> Project municipality</span>'
                + '<span><i class="swatch swatch--barangay"></i> Barangay boundary</span>';
            return div;
        };
        legend.addTo(map);

        return {
            map: map,
            L: L,
            settings: settings,
            tileLayer: tileLayer,
            resetView: function () {
                map.setView([settings.center.lat, settings.center.lng], settings.zoom);
            }
        };
    }

    function baseStyle(feature) {
        return feature && feature.properties && feature.properties.level === 'barangay'
            ? BARANGAY_STYLE
            : MUNICIPALITY_STYLE;
    }

    /**
     * Adds municipality boundaries. Clicking one calls options.onSelect with the
     * feature properties. The selected polygon keeps the active style.
     */
    function addMunicipalities(map, data, options) {
        var opts = options || {};
        var L = window.L;
        var selectedCode = null;
        var layer = L.geoJSON(data, {
            style: function (feature) {
                var code = feature.properties && feature.properties.code;
                return code && code === selectedCode ? MUNICIPALITY_ACTIVE_STYLE : MUNICIPALITY_STYLE;
            },
            onEachFeature: function (feature, featureLayer) {
                var props = feature.properties || {};
                featureLayer.bindTooltip(esc(props.name || 'Municipality'), {
                    sticky: true,
                    direction: 'auto',
                    className: 'jsm-tooltip'
                });
                featureLayer.on('mouseover', function () {
                    if (props.code !== selectedCode) featureLayer.setStyle({ fillOpacity: 0.2 });
                });
                featureLayer.on('mouseout', function () {
                    layer.resetStyle(featureLayer);
                });
                featureLayer.on('click', function () {
                    if (typeof opts.onSelect === 'function') opts.onSelect(props, featureLayer);
                });
            }
        }).addTo(map);

        return {
            layer: layer,
            select: function (code) {
                selectedCode = code || null;
                layer.eachLayer(function (featureLayer) {
                    var props = featureLayer.feature && featureLayer.feature.properties;
                    featureLayer.setStyle(props && props.code === selectedCode
                        ? MUNICIPALITY_ACTIVE_STYLE
                        : MUNICIPALITY_STYLE);
                });
            },
            styleFor: function (code, extra) {
                var matched = false;
                layer.eachLayer(function (featureLayer) {
                    var props = featureLayer.feature && featureLayer.feature.properties;
                    if (props && props.code === code) {
                        matched = true;
                        featureLayer.setStyle(Object.assign({}, MUNICIPALITY_ACTIVE_STYLE, extra || {}));
                    }
                });
                return matched;
            },
            bounds: function (code) {
                var bounds = null;
                layer.eachLayer(function (featureLayer) {
                    var props = featureLayer.feature && featureLayer.feature.properties;
                    if (code && (!props || props.code !== code)) return;
                    var layerBounds = featureLayer.getBounds();
                    bounds = bounds ? bounds.extend(layerBounds) : layerBounds;
                });
                return bounds;
            }
        };
    }

    function addBarangays(map, data, options) {
        var opts = options || {};
        var L = window.L;
        var selectedCode = null;
        var layer = L.geoJSON(data, {
            style: function (feature) {
                var code = feature.properties && feature.properties.code;
                return code && code === selectedCode
                    ? Object.assign({}, BARANGAY_STYLE, { color: '#25853A', weight: 2, fillOpacity: 0.28 })
                    : BARANGAY_STYLE;
            },
            onEachFeature: function (feature, featureLayer) {
                var props = feature.properties || {};
                featureLayer.bindTooltip(esc(props.name || 'Barangay'), {
                    sticky: true,
                    direction: 'auto'
                });
                featureLayer.on('click', function () {
                    if (typeof opts.onSelect === 'function') opts.onSelect(props, featureLayer);
                });
            }
        }).addTo(map);

        return {
            layer: layer,
            select: function (code) {
                selectedCode = code || null;
                layer.eachLayer(function (featureLayer) {
                    var props = featureLayer.feature && featureLayer.feature.properties;
                    featureLayer.setStyle(props && props.code === selectedCode
                        ? Object.assign({}, BARANGAY_STYLE, { color: '#25853A', weight: 2, fillOpacity: 0.28 })
                        : BARANGAY_STYLE);
                });
            },
            bounds: function (code) {
                var bounds = null;
                layer.eachLayer(function (featureLayer) {
                    var props = featureLayer.feature && featureLayer.feature.properties;
                    if (code && (!props || props.code !== code)) return;
                    var layerBounds = featureLayer.getBounds();
                    bounds = bounds ? bounds.extend(layerBounds) : layerBounds;
                });
                return bounds;
            }
        };
    }

    function jobIcon(selected) {
        var L = window.L;
        return L.divIcon({
            className: 'jsm-pin' + (selected ? ' jsm-pin--selected' : ''),
            html: '<span><i class="fas fa-briefcase"></i></span>',
            iconSize: [32, 32],
            iconAnchor: [16, 30],
            popupAnchor: [0, -28]
        });
    }

    function workplaceIcon() {
        var L = window.L;
        return L.divIcon({
            className: 'jsm-pin jsm-pin--workplace',
            html: '<span><i class="fas fa-location-dot"></i></span>',
            iconSize: [32, 32],
            iconAnchor: [16, 30],
            popupAnchor: [0, -28]
        });
    }

    function jobPopup(job) {
        var location = [municipalityLabel(job.municipality), job.barangay].filter(Boolean).join(', ');
        var salary = salaryLabel(job);
        var distance = distanceLabel(job);
        var match = Number(job.matchPercentage);
        var tags = [];
        if (job.employmentType) tags.push(typeLabel(job.employmentType));
        if (isFinite(match) && match > 0) tags.push(Math.round(match) + '% match');

        return '<div class="jsm-popup">'
            + '<h4>' + esc(job.jobTitle || 'Untitled role') + '</h4>'
            + '<div class="jsm-popup-company">' + esc(job.companyName || 'Employer') + '</div>'
            + '<div class="jsm-popup-meta">'
            + (location ? '<span><i class="fas fa-location-dot"></i>' + esc(location) + '</span>' : '')
            + (salary ? '<span><i class="fas fa-peso-sign"></i>' + esc(salary) + '</span>' : '')
            + (distance ? '<span><i class="fas fa-route"></i>' + esc(distance) + '</span>' : '')
            + (job.createdAt ? '<span><i class="far fa-calendar"></i>Posted ' + esc(dateLabel(job.createdAt)) + '</span>' : '')
            + '</div>'
            + (tags.length ? '<div class="jsm-popup-tags">' + tags.map(function (tag) {
                return '<span>' + esc(tag) + '</span>';
            }).join('') + '</div>' : '')
            + '<div class="jsm-popup-actions">'
            + '<a href="job-details.html?id=' + encodeURIComponent(job.id) + '">View details</a>'
            + '<button type="button" class="is-primary" data-apply="' + esc(job.id) + '"><i class="fas fa-paper-plane"></i> Apply</button>'
            + '</div>'
            + '</div>';
    }

    /**
     * Adds job markers, clustered when the plugin loaded.
     * options.onSelect(job, marker) fires on click.
     */
    function addJobs(map, jobs, options) {
        var opts = options || {};
        var L = window.L;
        var group = (typeof L.markerClusterGroup === 'function')
            ? L.markerClusterGroup({
                showCoverageOnHover: false,
                maxClusterRadius: 46,
                spiderfyDistanceMultiplier: 1.2,
                iconCreateFunction: function (cluster) {
                    return L.divIcon({
                        html: '<div class="jsm-cluster">' + cluster.getChildCount() + '</div>',
                        className: '',
                        iconSize: [40, 40]
                    });
                }
            })
            : L.layerGroup();

        var markers = [];

        (jobs || []).forEach(function (job) {
            if (job.latitude == null || job.longitude == null) return;
            var marker = L.marker([Number(job.latitude), Number(job.longitude)], {
                icon: jobIcon(false),
                title: job.jobTitle || 'Job',
                keyboard: true,
                riseOnHover: true
            });
            marker.bindPopup(jobPopup(job), { maxWidth: 300, minWidth: 260, className: 'jsm-popup-wrap' });
            marker.__job = job;
            marker.on('click', function () {
                if (typeof opts.onSelect === 'function') opts.onSelect(job, marker);
            });
            group.addLayer(marker);
            markers.push(marker);
        });

        group.addTo(map);

        return {
            group: group,
            markers: markers,
            highlight: function (jobId) {
                markers.forEach(function (marker) {
                    var job = marker.__job;
                    marker.setIcon(jobIcon(String(job && job.id) === String(jobId)));
                });
            },
            open: function (jobId) {
                var found = null;
                markers.forEach(function (marker) {
                    if (marker.__job && String(marker.__job.id) === String(jobId)) found = marker;
                });
                if (found) {
                    if (group.zoomToShowLayer) group.zoomToShowLayer(found, function () { found.openPopup(); });
                    else { map.setView(found.getLatLng(), Math.max(map.getZoom(), 14)); found.openPopup(); }
                }
                return found;
            },
            bounds: function () {
                if (!markers.length) return null;
                var bounds = L.latLngBounds(markers.map(function (marker) { return marker.getLatLng(); }));
                return bounds;
            },
            count: markers.length
        };
    }

    /** Compact job card for a side panel. */
    function jobPreview(job, options) {
        var opts = options || {};
        var location = [municipalityLabel(job.municipality), job.barangay].filter(Boolean).join(', ');
        var salary = salaryLabel(job);
        var distance = distanceLabel(job);
        return '<div class="map-job-card">'
            + '<h4>' + esc(job.jobTitle || 'Untitled role') + '</h4>'
            + '<div class="map-job-company">' + esc(job.companyName || 'Employer') + '</div>'
            + '<div class="map-job-meta">'
            + (location ? '<span><i class="fas fa-location-dot"></i>' + esc(location) + '</span>' : '')
            + (job.employmentType ? '<span><i class="fas fa-clock"></i>' + esc(typeLabel(job.employmentType)) + '</span>' : '')
            + (salary ? '<span><i class="fas fa-peso-sign"></i>' + esc(salary) + '</span>' : '')
            + (distance ? '<span><i class="fas fa-route"></i>' + esc(distance) + '</span>' : '')
            + (job.createdAt ? '<span><i class="far fa-calendar"></i>' + esc(dateLabel(job.createdAt)) + '</span>' : '')
            + '</div>'
            + '<div class="map-job-actions">'
            + '<a class="btn-soft btn-sm" href="job-details.html?id=' + encodeURIComponent(job.id) + '">View details</a>'
            + (opts.apply === false ? '' : '<button class="btn-solid btn-sm" type="button" data-apply="' + esc(job.id) + '"><i class="fas fa-paper-plane"></i> Apply Now</button>')
            + '</div>'
            + '</div>';
    }

    /** Municipality markers/bounds helpers used by the picker and admin map. */
    function focusMunicipality(map, config, code, options) {
        var opts = options || {};
        var target = (config.municipalities || []).filter(function (item) { return item.code === code; })[0];
        if (!target) return;
        if (opts.maxZoom) map.setView([target.center.lat, target.center.lng], opts.maxZoom);
        else map.setView([target.center.lat, target.center.lng], Math.max(map.getZoom(), 12));
    }

    var JSMMap = {
        config: config,
        createMap: createMap,
        boundaries: boundaries,
        jobCounts: jobCounts,
        addMunicipalities: addMunicipalities,
        addBarangays: addBarangays,
        addJobs: addJobs,
        jobPopup: jobPopup,
        jobPreview: jobPreview,
        jobIcon: jobIcon,
        workplaceIcon: workplaceIcon,
        focusMunicipality: focusMunicipality,
        haversineKm: haversineKm,
        municipalityLabel: municipalityLabel,
        salaryLabel: salaryLabel,
        typeLabel: typeLabel,
        dateLabel: dateLabel,
        distanceLabel: distanceLabel,
        esc: esc
    };

    window.JSMMap = JSMMap;
})();
