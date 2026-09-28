#!/usr/bin/env python3
"""
Job & Skills Matching System - Matching Calculator
A stdlib-only Python script that demonstrates the rule-based matching algorithm
"""

import json
import math
from typing import Dict, List, Set, Tuple


def calculate_jaccard_similarity(set1: Set[str], set2: Set[str]) -> float:
    """Calculate Jaccard similarity between two sets"""
    if not set1 and not set2:
        return 1.0
    if not set1 or not set2:
        return 0.0
    intersection = len(set1.intersection(set2))
    union = len(set1.union(set2))
    return intersection / union if union > 0 else 0.0


def normalize_text(text: str) -> Set[str]:
    """Normalize text into a set of lowercase words"""
    if not text:
        return set()
    # Simple normalization: lowercase, split by common separators
    words = text.lower().replace(',', ' ').replace(';', ' ').split()
    # Remove empty strings and very short words
    return {word.strip() for word in words if len(word.strip()) > 1}


def calculate_skills_match(job_seeker_skills: str, job_required_skills: str) -> float:
    """Calculate skills match percentage using Jaccard similarity"""
    seeker_skills = normalize_text(job_seeker_skills)
    required_skills = normalize_text(job_required_skills)

    if not required_skills:
        return 100.0  # If no skills required, it's a perfect match

    similarity = calculate_jaccard_similarity(seeker_skills, required_skills)
    return similarity * 100


def calculate_education_match(job_seeker_education: str, job_education_req: str) -> float:
    """Calculate education match percentage"""
    # Simplified education level hierarchy
    education_levels = [
        'No Formal Education',
        'Elementary Graduate',
        'High School Graduate',
        'Vocational/Technical',
        'College Graduate',
        'Post Graduate'
    ]

    try:
        seeker_level = education_levels.index(job_seeker_education) if job_seeker_education in education_levels else -1
        required_level = education_levels.index(job_education_req) if job_education_req in education_levels else -1
    except ValueError:
        # Handle 'Any' or custom values
        if job_education_req.lower() == 'any':
            return 100.0
        if job_seeker_education.lower() == job_education_req.lower():
            return 100.0
        return 50.0  # Partial match for related fields

    if required_level == -1:  # 'Any' or unrecognized
        return 100.0

    if seeker_level == -1:
        return 50.0  # Unknown education level

    # If seeker has equal or higher education than required, it's a good match
    if seeker_level >= required_level:
        return 100.0
    else:
        # Partial match based on how close they are
        diff = required_level - seeker_level
        return max(0, 100 - (diff * 25))  # 25% penalty per level difference


def calculate_experience_match(job_seeker_years: float, job_years_req: str) -> float:
    """Calculate experience match percentage"""
    if job_years_req.lower() == 'no experience' or job_years_req.lower() == 'any':
        return 100.0

    try:
        if '-' in job_years_req:
            # Range like "3-5 Years"
            min_years, max_years = map(float, job_years_req.replace('Years', '').strip().split('-'))
            required_min = min_years
            required_max = max_years
        else:
            # Single value like "5+ Years" or "3 Years"
            if '+' in job_years_req:
                required_min = float(job_years_req.replace('+', '').replace('Years', '').strip())
                required_max = float('inf')  # Unlimited maximum
            else:
                required_min = float(job_years_req.replace('Years', '').strip())
                required_max = required_min
    except ValueError:
        return 50.0  # Unable to parse, give partial match

    if required_max == float('inf'):
        # Unlimited maximum, just check minimum
        if job_seeker_years >= required_min:
            return 100.0
        else:
            # Partial match based on how close to minimum
            if required_min > 0:
                return max(0, (job_seeker_years / required_min) * 100)
            else:
                return 100.0
    else:
        # Fixed range
        if job_seeker_years < required_min:
            # Below minimum
            if required_min > 0:
                return max(0, (job_seeker_years / required_min) * 100)
            else:
                return 100.0
        elif job_seeker_years > required_max:
            # Above maximum - still good, but maybe overqualified
            return 100.0  # Still consider it a good match
        else:
            # Within range
            return 100.0


def calculate_location_match(job_seeker_municipality: str, job_municipality: str) -> float:
    """Calculate location match percentage"""
    if job_seeker_municipality == job_municipality:
        return 100.0

    # Define nearby municipalities for partial credit
    nearby_municipalities = {
        'Basud': ['Daet'],
        'Daet': ['Basud', 'Vinzons'],
        'Vinzons': ['Daet', 'Labo'],
        'Labo': ['Vinzons']
    }

    if job_municipality in nearby_municipalities.get(job_seeker_municipality, []):
        return 70.0  # Nearby municipality gets 70% match

    return 30.0  # Different municipality gets minimal match


def calculate_availability_match(job_seeker_availability: str, job_work_schedule: str) -> float:
    """Calculate availability match percentage"""
    # Simplified matching - in reality this would be more complex
    availability_mapping = {
        'Immediately': ['Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible'],
        'Within 1 week': ['Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible'],
        'Within 2 weeks': ['Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible'],
        'Within 1 month': ['Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible'],
        'Within 3 months': ['Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible'],
        'More than 3 months': ['Flexible']  # Only flexible work makes sense for distant availability
    }

    if job_seeker_availability in availability_mapping:
        if job_work_schedule in availability_mapping[job_seeker_availability]:
            return 100.0
        elif job_work_schedule == 'Flexible':
            return 80.0  # Flexible schedule works with most availabilities
        else:
            return 40.0  # Limited match

    return 50.0  # Default partial match


def calculate_employment_type_match(job_seeker_preference: str, job_employment_type: str) -> float:
    """Calculate employment type match percentage"""
    if job_seeker_preference.lower() == 'any' or job_employment_type.lower() == 'any':
        return 100.0

    if job_seeker_preference.lower() == job_employment_type.lower():
        return 100.0

    # Partial matches for related types
    compatible_types = {
        'Part-time': ['Contract', 'Temporary'],
        'Full-time': ['Contract'],
        'Contract': ['Part-time', 'Full-time', 'Temporary'],
        'Temporary': ['Part-time', 'Contract']
    }

    if job_employment_type in compatible_types.get(job_seeker_preference, []):
        return 70.0

    return 40.0


def calculate_salary_match(job_seeker_min: float, job_seeker_max: float,
                          job_min: float, job_max: float) -> float:
    """Calculate salary match percentage based on range overlap"""
    # Handle cases where values might be zero or not provided
    if job_seeker_min == 0 and job_seeker_max == 0:
        return 80.0  # No preference specified

    if job_min == 0 and job_max == 0:
        return 80.0  # No salary specified in job

    # Calculate overlap between ranges
    seeker_range = (job_seeker_min, job_seeker_max) if job_seeker_max > 0 else (job_seeker_min, float('inf'))
    job_range = (job_min, job_max) if job_max > 0 else (job_min, float('inf'))

    # Find overlap
    overlap_start = max(seeker_range[0], job_range[0])
    overlap_end = min(seeker_range[1], job_range[1])

    if overlap_start >= overlap_end:
        # No overlap
        return 0.0

    overlap = overlap_end - overlap_start

    # Calculate what percentage of job range is covered by seeker range
    job_range_size = job_range[1] - job_range[0] if job_range[1] != float('inf') else seeker_range[1] - seeker_range[0]
    seeker_range_size = seeker_range[1] - seeker_range[0] if seeker_range[1] != float('inf') else job_range[1] - job_range[0]

    if job_range_size == float('inf') and seeker_range_size == float('inf'):
        return 80.0  # Both ranges unlimited

    if job_range_size == float('inf'):
        # Job has unlimited max, check if seeker range fits
        return min(100, (seeker_range_size / (overlap + seeker_range_size)) * 100) if overlap > 0 else 50

    if seeker_range_size == float('inf'):
        # Seeker has unlimited max, check if job range fits
        return min(100, (job_range_size / (overlap + job_range_size)) * 100) if overlap > 0 else 50

    # Normal case: both ranges finite
    match_percentage = (overlap / job_range_size) * 100 if job_range_size > 0 else 0
    return min(100, match_percentage)


def calculate_overall_match(job_seeker_profile: Dict, job_post: Dict) -> Dict[str, float]:
    """
    Calculate overall match percentage between a job seeker and a job post

    Returns a dictionary with individual match scores and overall weighted score
    """
    # Define weights for each factor (must sum to 1.0)
    weights = {
        'skills': 0.25,
        'education': 0.15,
        'experience': 0.20,
        'certifications': 0.10,
        'location': 0.10,
        'availability': 0.10,
        'employment_type': 0.05,
        'salary': 0.05
    }

    # Calculate individual match scores
    skills_match = calculate_skills_match(
        job_seeker_profile.get('skills', ''),
        job_post.get('required_skills', '')
    )

    education_match = calculate_education_match(
        job_seeker_profile.get('education_level', 'No Formal Education'),
        job_post.get('education_requirement', 'No Formal Education')
    )

    experience_match = calculate_experience_match(
        job_seeker_profile.get('experience_years', 0),
        job_post.get('experience_requirement', 'No Experience')
    )

    certifications_match = calculate_skills_match(
        job_seeker_profile.get('certifications', ''),
        job_post.get('certifications', '')
    )

    location_match = calculate_location_match(
        job_seeker_profile.get('municipality', ''),
        job_post.get('municipality', '')
    )

    availability_match = calculate_availability_match(
        job_seeker_profile.get('availability', 'Immediately'),
        job_post.get('work_schedule', 'Flexible')
    )

    employment_type_match = calculate_employment_type_match(
        job_seeker_profile.get('employment_type_preference', 'Any'),
        job_post.get('employment_type', 'Full-time')
    )

    salary_match = calculate_salary_match(
        job_seeker_profile.get('expected_salary_min', 0),
        job_seeker_profile.get('expected_salary_max', 0),
        job_post.get('salary_min', 0),
        job_post.get('salary_max', 0)
    )

    # Calculate weighted overall score
    overall_score = (
        skills_match * weights['skills'] +
        education_match * weights['education'] +
        experience_match * weights['experience'] +
        certifications_match * weights['certifications'] +
        location_match * weights['location'] +
        availability_match * weights['availability'] +
        employment_type_match * weights['employment_type'] +
        salary_match * weights['salary']
    )

    return {
        'skills_match': round(skills_match, 2),
        'education_match': round(education_match, 2),
        'experience_match': round(experience_match, 2),
        'certifications_match': round(certifications_match, 2),
        'location_match': round(location_match, 2),
        'availability_match': round(availability_match, 2),
        'employment_type_match': round(employment_type_match, 2),
        'salary_match': round(salary_match, 2),
        'overall_match': round(overall_score, 2)
    }


def demo_matching():
    """Demonstrate the matching algorithm with sample data"""
    print("Job & Skills Matching System - Matching Algorithm Demo")
    print("=" * 60)

    # Sample job seeker profile
    job_seeker = {
        'skills': 'Customer Service, Food Preparation, Cashier, Teamwork, Communication',
        'education_level': 'High School Graduate',
        'experience_years': 2,
        'certifications': 'Food Handler\\'s Certificate',
        'municipality': 'Daet',
        'availability': 'Immediately',
        'employment_type_preference': 'Part-time',
        'expected_salary_min': 12000,
        'expected_salary_max': 15000
    }

    # Sample job post
    job_post = {
        'required_skills': 'Customer Service, Food Preparation, Cashier, POS System',
        'education_requirement': 'High School Graduate',
        'experience_requirement': '1-2 Years',
        'certifications': 'Food Handler\\'s Certificate',
        'municipality': 'Daet',
        'work_schedule': 'Flexible',
        'employment_type': 'Part-time',
        'salary_min': 12000,
        'salary_max': 15000
    }

    print("\\nJob Seeker Profile:")
    for key, value in job_seeker.items():
        print(f"  {key.replace('_', ' ').title()}: {value}")

    print("\\nJob Post Requirements:")
    for key, value in job_post.items():
        print(f"  {key.replace('_', ' ').title()}: {value}")

    print("\\nMatch Results:")
    print("-" * 40)

    results = calculate_overall_match(job_seeker, job_post)

    for match_type, score in results.items():
        print(f"{match_type.replace('_', ' ').title():<20}: {score}%")

    print("-" * 40)
    print(f"{'Overall Match':<20}: {results['overall_match']}%")

    # Interpretation
    overall = results['overall_match']
    if overall >= 90:
        interpretation = "Excellent Match"
    elif overall >= 80:
        interpretation = "Good Match"
    elif overall >= 70:
        interpretation = "Fair Match"
    elif overall >= 60:
        interpretation = "Moderate Match"
    else:
        interpretation = "Low Match"

    print(f"Interpretation: {interpretation}")

    return results


if __name__ == "__main__":
    demo_matching()