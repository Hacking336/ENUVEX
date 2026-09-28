# Job & Skills Matching System

A localized job marketplace and matching platform for job seekers and employers within the municipalities of Basud, Daet, Vinzons, and Labo in Camarines Norte.

## System Overview

This web-based system connects local job seekers with local employers across various industries, not limited to IT or technology jobs. The platform supports employment opportunities in:

- Retail / Store
- Sales
- Food Service / Restaurant
- Hospitality / Tourism
- Office / Administration
- Accounting / Finance
- Healthcare
- Education / Tutoring
- Construction / Skilled Trades
- Agriculture
- Fisheries
- Manufacturing
- Logistics / Delivery
- Transportation
- Automotive / Mechanical
- Customer Service
- Security
- General Labor
- IT / Technology
- Other

## Key Features

### For Job Seekers:
- Profile creation with skills, education, experience, and preferences
- Job search and filtering by location, category, salary, etc.
- Match percentage calculation based on skills, education, experience, location, availability, etc.
- Application tracking
- Profile completion monitoring
- Job saving and recommendations

### For Employers:
- Business profile creation
- Job posting with detailed requirements
- Applicant management with match percentages
- Shortlisting and interview scheduling
- Application status tracking

### For Administrators:
- User and employer verification
- Job post moderation
- System analytics and reporting
- Category and municipality management

## Technology Stack

- Frontend: HTML5, CSS3, JavaScript (Vanilla)
- Responsive design for mobile and desktop
- Font Awesome for icons
- Local storage for demo data (in a real implementation, this would connect to a backend database)

## Installation

Since this is a frontend-only demo, simply open the `index.html` file in a web browser to view the login page.

## Usage

1. Start at the login page (`index.html`)
2. Choose to create either a Job Seeker or Employer account
3. Complete the registration form specific to your role
4. Log in to access your respective dashboard
5. Job seekers can search for jobs, view match percentages, and apply
6. Employers can post jobs, review applicants, and manage their listings
7. Administrators can manage users, job posts, and system settings

## Matching System

The platform uses a rule-based matching system that compares:
- Skills (25% weight)
- Education/Qualifications (15% weight)
- Work Experience (20% weight)
- Certifications (10% weight)
- Location/Distance (10% weight)
- Availability/Schedule (10% weight)
- Employment Type (5% weight)
- Salary Preference (5% weight)

The match percentage is displayed as a compatibility indicator to help job seekers and employers make informed decisions, but the final hiring decision remains with the employer.

## Geographic Scope

The system is strictly limited to the following municipalities in Camarines Norte:
- Basud
- Daet
- Vinzons
- Labo

Barangays within these municipalities are available for more specific location targeting.

## Notes

This is a frontend demonstration of the user interface. In a production implementation, the system would require:
- Backend server (Node.js, Python, PHP, etc.)
- Database (MySQL, PostgreSQL, MongoDB)
- Authentication system (JWT, sessions)
- API endpoints for data operations
- Additional security measures
- Real-time notifications
- Deployment infrastructure

## License

MIT License