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

// Match percentage calculator (client-side reference implementation)
function calculateMatchPercentage(jobSeekerProfile, jobRequirements) {
    const weights = {
        skills: 0.25,
        education: 0.15,
        experience: 0.20,
        certifications: 0.10,
        location: 0.10,
        availability: 0.10,
        employmentType: 0.05,
        salary: 0.05
    };

    let totalScore = 0;
    let totalWeight = 0;

    // Skills match (Jaccard similarity)
    if (jobSeekerProfile.skills && jobRequirements.skills) {
        const seekerSkills = new Set(jobSeekerProfile.skills.map(s => s.toLowerCase()));
        const requiredSkills = new Set(jobRequirements.skills.map(s => s.toLowerCase()));
        let matched = 0;
        requiredSkills.forEach(skill => {
            if (seekerSkills.has(skill)) matched++;
        });
        const skillsMatch = requiredSkills.size > 0 ? matched / requiredSkills.size : 1;
        totalScore += skillsMatch * weights.skills;
        totalWeight += weights.skills;
    }

    // Education match
    if (jobSeekerProfile.education && jobRequirements.education) {
        const educationMatch = jobSeekerProfile.education.toLowerCase() === jobRequirements.education.toLowerCase() ? 1 : 0.5;
        totalScore += educationMatch * weights.education;
        totalWeight += weights.education;
    }

    // Experience match
    if (jobSeekerProfile.experienceYears !== undefined && jobRequirements.experienceYears !== undefined) {
        const expMatch = Math.min(jobSeekerProfile.experienceYears / jobRequirements.experienceYears, 1);
        totalScore += expMatch * weights.experience;
        totalWeight += weights.experience;
    }

    // Certifications match (Jaccard similarity)
    if (jobSeekerProfile.certifications && jobRequirements.certifications) {
        const seekerCerts = new Set(jobSeekerProfile.certifications.map(c => c.toLowerCase()));
        const requiredCerts = new Set(jobRequirements.certifications.map(c => c.toLowerCase()));
        let matched = 0;
        requiredCerts.forEach(cert => {
            if (seekerCerts.has(cert)) matched++;
        });
        const certMatch = requiredCerts.size > 0 ? matched / requiredCerts.size : 1;
        totalScore += certMatch * weights.certifications;
        totalWeight += weights.certifications;
    }

    // Location match (simplified)
    if (jobSeekerProfile.municipality && jobRequirements.municipality) {
        const locationMatch = jobSeekerProfile.municipality.toLowerCase() === jobRequirements.municipality.toLowerCase() ? 1 :
                             jobSeekerProfile.municipality.toLowerCase() === 'daet' && jobRequirements.municipality.toLowerCase() === 'labo' ? 0.7 : 0.3;
        totalScore += locationMatch * weights.location;
        totalWeight += weights.location;
    }

    // Availability match
    if (jobSeekerProfile.availability && jobRequirements.workSchedule) {
        const availabilityMatch = jobSeekerProfile.availability.includes(jobRequirements.workSchedule) ||
                                 jobSeekerProfile.availability.includes('Flexible') ||
                                 jobRequirements.workSchedule.includes('Flexible') ? 1 : 0.5;
        totalScore += availabilityMatch * weights.availability;
        totalWeight += weights.availability;
    }

    // Employment type match
    if (jobSeekerProfile.employmentType && jobRequirements.employmentType) {
        const employmentMatch = jobSeekerProfile.employmentType.toLowerCase() === jobRequirements.employmentType.toLowerCase() ||
                               jobSeekerProfile.employmentType.toLowerCase() === 'any' ||
                               jobRequirements.employmentType.toLowerCase() === 'any' ? 1 : 0.5;
        totalScore += employmentMatch * weights.employmentType;
        totalWeight += weights.employmentType;
    }

    // Salary match
    if (jobSeekerProfile.expectedSalaryMin !== undefined && jobRequirements.salaryRangeMin !== undefined &&
        jobSeekerProfile.expectedSalaryMax !== undefined && jobRequirements.salaryRangeMax !== undefined) {
        const seekerMin = jobSeekerProfile.expectedSalaryMin;
        const seekerMax = jobSeekerProfile.expectedSalaryMax;
        const jobMin = jobRequirements.salaryRangeMin;
        const jobMax = jobRequirements.salaryRangeMax;

        // Check for overlap
        const overlap = Math.max(0, Math.min(seekerMax, jobMax) - Math.max(seekerMin, jobMin));
        const seekerRange = seekerMax - seekerMin;
        const jobRange = jobMax - jobMin;

        const salaryMatch = overlap > 0 ? overlap / Math.max(seekerRange, jobRange) : 0;
        totalScore += salaryMatch * weights.salary;
        totalWeight += weights.salary;
    }

    // Calculate final percentage
    return totalWeight > 0 ? Math.round((totalScore / totalWeight) * 100) : 0;
}

// Export functions for use in other files
window.calculateMatchPercentage = calculateMatchPercentage;
window.calculateDistance = calculateDistance;