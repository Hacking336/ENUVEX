# Deploying ENUVEX

ENUVEX is a Spring Boot application: the same process serves the API at `/api`
and the static pages (`index.html`, `job-seeker/`, `employer/`, `admin/`). It
therefore needs a host that can run a JVM, and a Postgres database.

GitHub Pages **cannot** host this. Pages serves static files only, so the pages
would render but every call to `/api` would return 404 and no one could log in.

## 1. Database

The app talks to Supabase Postgres and creates its own tables on first start
(Flyway, schema `enuv`). Nothing needs to be run by hand.

Connection values live in the Supabase dashboard under **Project Settings → Database**:

| Variable | Value |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require&currentSchema=enuv` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | your database password |

If your host cannot reach IPv6 or port 5432 is blocked, use the connection
pooler instead: `...@aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require&currentSchema=enuv`
with username `postgres.<project-ref>`.

## 2. Required environment variables

Set these in your host's dashboard. Never commit them.

| Variable | Required | Notes |
| --- | --- | --- |
| `DB_URL` | yes | as above |
| `DB_USERNAME` | yes | as above |
| `DB_PASSWORD` | yes | as above |
| `JWT_SECRET` | yes | at least 32 random characters; the app refuses to start without it |
| `PORT` | no | defaults to `8080`; most platforms set it for you |
| `JWT_EXPIRATION_MS` | no | defaults to `3600000` (1 hour) |

Generate a secret with:

```bash
openssl rand -base64 48
```

## 3. Build and run locally

The Docker build context must be the **repository root**, not `backend/`,
because `pom.xml` copies the HTML/CSS/JS sitting next to `backend/` into the jar.

```bash
docker build -t enuvex .

docker run --rm -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require&currentSchema=enuv" \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD="..." \
  -e JWT_SECRET="..." \
  enuvex
```

Then open <http://localhost:8080>. `GET /api/health` returns `{"status":"UP"}`.

Without Docker, from the `backend/` directory: `mvn spring-boot:run` (with the
variables above in the environment or in `backend/.env`).

## 4. Deploy from GitHub

1. Create a service on a host that runs containers from a Git repository
   (any platform that builds a `Dockerfile` works - no code changes needed).
2. Point it at `Hacking336/ENUVEX`, branch `main`, **root directory** (`.`).
3. Add the environment variables from step 2.
4. Health check path: `/api/health`.

The image compiles the jar in a Maven stage and ships only the JRE plus the jar,
so the host does not need Java or Maven installed.

## 5. Custom domain

Add the domain in **your host's** dashboard, not GitHub's - the platform
terminates TLS and returns the certificate and target it expects:

1. Add the domain to the service (e.g. `enuv.example.com`).
2. Create the DNS record it shows you at your registrar:
   - subdomain: `CNAME` → the hostname the platform gives you
   - apex domain: usually `A`/`ALIAS` records to the platform's IPs
3. Wait for propagation, then enable HTTPS on the platform.

Do not point the domain at GitHub Pages: the DNS records are different and the
site would stop working as soon as someone tried to log in.

## 6. After the first deploy

- **Admin password.** The seeded admin `admin@jobskillsmatching.com` was created
  with a placeholder hash that is public in this repository. It has been replaced
  with a random password kept in `backend/.env` as `ADMIN_PASSWORD` (git-ignored
  and never deployed, so move it to a password manager). If it is lost, generate
  a new hash with `new BCryptPasswordEncoder().encode("...")` and update
  `enuv.users.password_hash` for that email.
- **Uploads.** Profile pictures are written to `uploads/` inside the container.
  On hosts with an ephemeral filesystem they are lost on every redeploy; mount a
  persistent volume at `/app/uploads` if that matters.
- **Old credentials.** A MySQL password and an old JWT key were used during
  early development and still appear in the git history. Rotate anything you
  reused from those.
