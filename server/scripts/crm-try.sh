#!/usr/bin/env bash
# Sends sample jobs shaped like real CRMs to your Pairshot inbound address, so you can check the
# whole path on your own phone for free. Usage:  scripts/crm-try.sh <inbound address from the app>
# Then in Pairshot: Settings → Account & CRM → Sync now. You should see 6 jobs, and the "Ivy Chen"
# one should disappear again (the last post cancels it).
set -euo pipefail
url="${1:?Paste your inbound address (Settings → Account & CRM → Copy address)}"
post() {
  local label="$1" body="$2" type="${3:-application/json}"
  printf '%-28s ' "$label"
  curl -sS -X POST "$url" -H "content-type: $type" --data "$body" -w '  [%{http_code}]\n'
}
post "Plain Zapier POST"     '{"id":"try-1","client_name":"Dana Ruiz","phone":"555-0100","address":"12 Oak St, Springfield","service":"House wash","start_at":"2026-12-01T09:00:00Z","notes":"Dog in yard"}'
post "Jobber via Zapier"     '{"visit_id":"try-2","job_title":"Soft wash - house + driveway","client_name":"Maria Gomez","client_phone_number":"(813) 555-0142","property_street":"88 Palm Ave","property_city":"Tampa","visit_start_at":"2026-12-02T14:00:00-05:00","instructions":"Gate code 4411"}'
post "Housecall Pro webhook" '{"event":"job.scheduled","job":{"id":"try-3","description":"Pressure wash driveway","customer":{"first_name":"Tom","last_name":"Reyes","mobile_number":"555-0199"},"address":{"street":"12 Elm St","city":"Austin"},"schedule":{"scheduled_start":"2026-12-03T09:00:00Z"}}}'
post "Google Calendar"       '{"id":"try-4","summary":"Window cleaning - Pat Kim","location":"301 Hill Rd, Denver","start":{"dateTime":"2026-12-04T08:00:00-07:00"}}'
post "Calendly"              '{"event":"invitee.created","payload":{"uri":"try-5","name":"Lee Park","email":"lee@example.com","scheduled_event":{"name":"Car detailing - full","start_time":"2026-12-05T16:00:00Z"}}}'
post "Form post (old CRMs)"  'job_id=try-6&customer_name=Bo+Diaz&phone=555-0123&address=9+Rd&service=Gutter+clean' application/x-www-form-urlencoded
post "Square booking"        '{"booking":{"id":"try-7","status":"ACCEPTED","start_at":"2026-12-06T13:30:00Z","customer":{"given_name":"Ivy","family_name":"Chen","phone_number":"+15550101"}}}'
post "Square cancels it"     '{"booking":{"id":"try-7","status":"CANCELLED_BY_CUSTOMER"}}'
post "Missing name (refused)" '{"id":"try-8","phone":"555"}'
echo "Done. Expect 200 for each, and 422 for the last one. Now tap Sync now in the app."
