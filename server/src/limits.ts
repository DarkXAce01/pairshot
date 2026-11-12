import type { Plan } from "./env";

const DAY = 86_400_000;

/** Every limit in one place (PLAN.md §5–§7). The server is the only thing that enforces them. */
export const LIMITS = {
  free: {
    galleriesPerMonth: 3,
    galleriesPerDay: 3,
    photosPerGallery: 30,
    lifetimeMs: 14 * DAY,
    editWindowMs: 3 * DAY,
    storageBytes: 50 * 1024 * 1024,
    crmJobsPerDay: 0,
    apiCallsPerDay: 0,
    webhooks: 0,
  },
  pro: {
    galleriesPerMonth: Infinity,
    galleriesPerDay: 50,
    photosPerGallery: 100,
    lifetimeMs: Infinity,
    editWindowMs: Infinity,
    storageBytes: 5 * 1024 * 1024 * 1024,
    crmJobsPerDay: 200,
    apiCallsPerDay: 1000,
    webhooks: 5,
  },
  both: {
    maxPhotoBytes: 1024 * 1024,
    updatesPerGalleryPerDay: 20,
    /** Different photos ever put in one gallery, as a multiple of its photo limit. */
    historyFactor: 1.5,
    unreferencedGraceMs: DAY,
  },
} as const;

export const limitsFor = (plan: Plan) => LIMITS[plan];

export const dayKey = (now: number) => new Date(now).toISOString().slice(0, 10);
export const monthKey = (now: number) => new Date(now).toISOString().slice(0, 7);
