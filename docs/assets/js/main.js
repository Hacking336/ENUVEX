// Job & Skills Matching System - Main JavaScript (Shared Utilities)

document.addEventListener('DOMContentLoaded', function() {
    // Initialize accordion functionality
    const accordionHeaders = document.querySelectorAll('.accordion-header');

    accordionHeaders.forEach(header => {
        header.addEventListener('click', function() {
            const accordionItem = this.parentElement;
            accordionItem.classList.toggle('active');

            const accordionContent = accordionItem.querySelector('.accordion-content');
            if (accordionItem.classList.contains('active')) {
                accordionContent.style.maxHeight = accordionContent.scrollHeight + 'px';
            } else {
                accordionContent.style.maxHeight = null;
            }
        });
    });

    // Chart initialization (if Chart.js is available)
    if (typeof Chart !== 'undefined') {
        const ctx = document.getElementById('matchChart');
        if (ctx) {
            new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: ['Skills Match', 'Education Match', 'Experience Match', 'Location Match'],
                    datasets: [{
                        data: [85, 90, 75, 80],
                        backgroundColor: [
                            'rgba(67, 97, 238, 0.8)',
                            'rgba(76, 201, 240, 0.8)',
                            'rgba(248, 150, 30, 0.8)',
                            'rgba(249, 65, 68, 0.8)'
                        ],
                        borderWidth: 0
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            position: 'bottom',
                            labels: {
                                usePointStyle: true,
                                padding: 20
                            }
                        }
                    }
                }
            });
        }
    }
});

/* ===== SHARED UTILITY FUNCTIONS ===== */

// Match percentages are calculated by the backend (MatchingEngine) and sent with
// each job. There is deliberately no second copy of that rule here: two
// implementations of the same weights always drift apart.

// Helper function to calculate distance (simplified)
function calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371; // Radius of the earth in km
    const dLat = deg2rad(lat2 - lat1);
    const dLon = deg2rad(lon2 - lon1);
    const a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const d = R * c; // Distance in km
    return d;
}

function deg2rad(deg) {
    return deg * (Math.PI / 180);
}

// Export functions for use in other files
window.calculateDistance = calculateDistance;
