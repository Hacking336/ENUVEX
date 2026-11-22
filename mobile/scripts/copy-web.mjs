#!/usr/bin/env node
/**
 * Assembles mobile/www from the same files the Spring Boot build serves.
 *
 * The web app lives at the repository root (root *.html, assets/,
 * job-seeker/, employer/, admin/) and backend/pom.xml copies exactly those
 * directories into the jar's static folder. Capacitor needs the same layout in
 * one directory it can package, so this script rebuilds it on every sync
 * instead of keeping a hand-maintained duplicate that can drift.
 *
 *   node scripts/copy-web.mjs
 */
import { cp, mkdir, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url)); // mobile/scripts
const mobileDir = path.resolve(here, '..');                  // mobile
const repoDir = path.resolve(mobileDir, '..');               // repository root
const wwwDir = path.join(mobileDir, 'www');

// Directories the web frontend is made of. They mirror the POM resources.
const directories = ['assets', 'job-seeker', 'employer', 'admin'];

// job-seeker/ also holds the shared design system CSS/JS; the HTML pages link
// it, so those files must travel with them. employer/ and admin/ hold HTML only.
const allowedExtensions = new Set(['.html', '.css', '.js', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.webp', '.ico', '.woff', '.woff2', '.ttf']);

async function copyTree(from, to) {
  await cp(from, to, {
    recursive: true,
    filter: (source) => {
      const base = path.basename(source);
      if (base.startsWith('.')) return false;            // .DS_Store, .git, ...
      const ext = path.extname(source).toLowerCase();
      // Directories have no extension and must be kept so their children copy.
      return ext === '' || allowedExtensions.has(ext);
    }
  });
}

async function main() {
  await rm(wwwDir, { recursive: true, force: true });
  await mkdir(wwwDir, { recursive: true });

  // Root pages: index.html, login.html, register.html (layout.html is a
  // template fragment, harmless to ship and matches what the server serves).
  const rootEntries = await readdir(repoDir, { withFileTypes: true });
  for (const entry of rootEntries) {
    if (entry.isFile() && entry.name.endsWith('.html')) {
      await cp(path.join(repoDir, entry.name), path.join(wwwDir, entry.name));
    }
  }

  for (const dir of directories) {
    await copyTree(path.join(repoDir, dir), path.join(wwwDir, dir));
  }

  // Capacitor fails with a blank screen when webDir is missing an entry page.
  await cp(path.join(repoDir, 'index.html'), path.join(wwwDir, 'index.html'));

  console.log(`Copied the ENUVEX web frontend into ${path.relative(process.cwd(), wwwDir)}`);
}

main().catch((error) => {
  console.error('Could not assemble the mobile web assets:', error);
  process.exitCode = 1;
});
