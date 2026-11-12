# Pairshot API and CRM connections (Pro)

Three ways Pairshot talks to a CRM. All three are Pro features, and all of them work through
Zapier or Make, so any CRM those can reach will work: Jobber, Housecall Pro, ServiceTitan, GorillaDesk,
Google Sheets and others.

| Direction | What | How |
|---|---|---|
| CRM → Pairshot | New and changed jobs show up in the app | POST to your **inbound address** |
| Pairshot → CRM | "Link sent", "link updated" and "client opened it" | **Webhooks** you add in the app |
| Your tools → Pairshot | Look up a job's link, list links, add jobs | **API key** (`X-Api-Key`) |

All of these are in the app under **Settings → Cloud & CRM**.

## 1. Jobs in: your inbound address

`POST https://<server>/hooks/in/<secret>` with JSON or a form. Pairshot accepts the field names CRMs
already use, so there is usually nothing to map:

| Pairshot field | Also read from |
|---|---|
| job id (required) | `external_id`, `job_id`, `jobId`, `job_number`, `id`, `number`, `visit_id`, `appointment_id` |
| client name (required for new jobs) | `client_name`, `customer_name`, `full_name`, `name`, or `first_name` + `last_name` |
| phone | `client_phone`, `customer_phone`, `phone`, `mobile`, `cell` |
| email | `client_email`, `customer_email`, `email` |
| address | `address`, `service_address`, `property_address`, `address1`, `street` (+ `city`) |
| service | `service`, `title`, `job_title`, `job_type`, `description`, `summary` |
| when | `scheduled_at`, `start_at`, `start`, `date`, `appointment` (ISO text, seconds or milliseconds) |
| notes | `notes`, `instructions`, `comments` |
| cancelled | `status: "cancelled"`, `cancelled: true`, `deleted: true`, `archived` |

Nested objects work too. For example, `{"client": {"first_name": "Sam"}}` is read as Sam.
Sending the same job id again updates that job. A blank field never erases what the phone has.
A cancelled job is removed from the phone unless photos were already taken.

```
curl -X POST https://<server>/hooks/in/<secret> \
  -H 'content-type: application/json' \
  -d '{"id": "J-1042", "client_name": "Dana Ruiz", "phone": "555 0199",
       "address": "12 Oak St, Springfield", "service": "House wash",
       "start_at": "2026-11-14T09:00:00Z"}'
→ {"ok": true, "externalId": "J-1042", "cancelled": false}
```

Limit: 200 jobs a day. Anyone with the address can add jobs, so if it leaks, tap **New address** in the app.

## 2. Events out: webhooks

Add an https address in the app (for example a Zapier "Catch Hook"). You can have up to 5. Pairshot sends a POST:

```
X-Pairshot-Event: gallery.published | gallery.updated | gallery.viewed | ping
X-Pairshot-Delivery: <delivery id>
X-Pairshot-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<raw body>")>

{
  "id": "<delivery id>",
  "event": "gallery.published",
  "createdAt": "2026-11-12T19:13:50.000Z",
  "data": {
    "gallery": { "id", "jobId", "externalId", "url", "state", "publishedAt", "updatedAt", "expiresAt", "firstViewedAt", "viewCount" },
    "job": { "id", "externalId", "clientName", "clientPhone", "clientEmail", "address", "service" }
  }
}
```

`gallery.viewed` fires once, the first time the client opens the page. Link previews and bots
don't count. If your endpoint doesn't answer 2xx within 10 seconds, Pairshot tries again after
1 minute, 5 minutes, 30 minutes, 2 hours, 6 hours and 15 hours, then gives up. The app shows the
latest deliveries. The signing secret is shown once, when you add the webhook.

To check a signature, compute `HMAC-SHA256(secret, t + "." + body)` in hex and compare it with `v1`.
Reject the request if `t` is more than 5 minutes old.

## 3. The API (for Zapier/Make "lookup" and "poll" steps)

Send `X-Api-Key: ps_…` (or `Authorization: Bearer ps_…`). Limit: 1,000 calls a day.

| Call | What it does |
|---|---|
| `GET /api/v1/me` | Account id and plan |
| `GET /api/v1/galleries?since=<ms>` | Links sent or updated since then, newest first (up to 100), each with its job. Works as a polling trigger. |
| `GET /api/v1/galleries/lookup?external_id=J-1042` | The link for a CRM job, e.g. to paste into an invoice email |
| `POST /api/v1/jobs` | Adds or updates a job. Same fields as the inbound address. |

Errors always look like `{"error": "<code>", "message": "<words for a person>"}`.

## Recipes

**Jobber → Pairshot (Zapier):** Trigger *Jobber: New Visit* → Action *Webhooks by Zapier: POST* to
your inbound address, payload type JSON, data: `id` = Visit ID, `client_name` = Client Name,
`phone`, `address`, `title` = Visit Title, `start_at` = Start At.

**Link sent → note in the CRM (Zapier):** Trigger *Webhooks by Zapier: Catch Hook* (paste its address
into Pairshot) → Filter `event` is `gallery.published` → Action *Jobber: Create Note* on the job
`data.job.externalId` with the text `Before & after sent: {{data.gallery.url}}`.

**Client opened it → ask for a review (Make):** *Custom webhook* (added in Pairshot) → Filter
`event = gallery.viewed` → *Wait 2 hours* → *Twilio: Send SMS* to `data.job.clientPhone`.

**Invoice email with the link:** in the invoice step, call
`GET /api/v1/galleries/lookup?external_id={{job id}}` and use `url`.
