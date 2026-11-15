import { describe, expect, it } from "vitest";
import { normalise } from "../src/crm";

/**
 * Payloads shaped like what popular field-service tools send through Zapier / Make / their own
 * webhooks. Nobody has to own these accounts: if these read right, a real one will too.
 */
describe("real-world CRM shapes", () => {
  it("Jobber via Zapier (New Visit)", () => {
    const j = normalise({
      visit_id: "Z2lkOi8vSm9iYmVyL1Zpc2l0LzEyMw==", job_number: 1042, job_title: "Soft wash - house + driveway",
      client_name: "Maria Gomez", client_phone_number: "(813) 555-0142", client_email: "maria@example.com",
      property_street: "88 Palm Ave", property_city: "Tampa", property_province: "FL",
      visit_start_at: "2026-11-20T14:00:00-05:00", visit_status: "upcoming", instructions: "Gate code 4411",
    });
    expect(j).toMatchObject({ clientName: "Maria Gomez", clientPhone: "(813) 555-0142", clientEmail: "maria@example.com", service: "Soft wash - house + driveway", notes: "Gate code 4411", cancelled: false });
    expect(j.externalId).toBeTruthy();
    expect(j.address).toBe("88 Palm Ave, Tampa");
    expect(j.scheduledAt).toBe(Date.parse("2026-11-20T14:00:00-05:00"));
  });

  it("Housecall Pro webhook (job.scheduled)", () => {
    const j = normalise({
      event: "job.scheduled",
      job: {
        id: "job_8f3a", invoice_number: "1188", description: "Pressure wash driveway", work_status: "scheduled",
        customer: { first_name: "Tom", last_name: "Reyes", mobile_number: "555-0199", email: "tom@example.com" },
        address: { street: "12 Elm St", city: "Austin", state: "TX", zip: "78701" },
        schedule: { scheduled_start: "2026-11-21T09:00:00Z", scheduled_end: "2026-11-21T11:00:00Z" },
      },
    });
    expect(j).toMatchObject({ externalId: "job_8f3a", clientName: "Tom Reyes", clientPhone: "555-0199", clientEmail: "tom@example.com", service: "Pressure wash driveway", cancelled: false });
    expect(j.address).toBe("12 Elm St, Austin");
    expect(j.scheduledAt).toBe(Date.parse("2026-11-21T09:00:00Z"));
  });

  it("Housecall Pro cancelled", () => {
    expect(normalise({ event: "job.canceled", job: { id: "job_8f3a", work_status: "user canceled" } }).cancelled).toBe(true);
  });

  it("Google Calendar event via Zapier", () => {
    const j = normalise({
      id: "4kq7v0abc_20261122T150000Z", summary: "Window cleaning - Pat Kim", location: "301 Hill Rd, Denver, CO",
      start: { dateTime: "2026-11-22T08:00:00-07:00" }, end: { dateTime: "2026-11-22T10:00:00-07:00" },
      description: "2 storey, 24 windows", status: "confirmed",
    });
    expect(j).toMatchObject({ externalId: "4kq7v0abc_20261122T150000Z", address: "301 Hill Rd, Denver, CO", cancelled: false });
    expect(j.service).toBe("Window cleaning - Pat Kim");
    expect(j.clientName).toBe("Window cleaning - Pat Kim");
    expect(j.scheduledAt).toBe(Date.parse("2026-11-22T08:00:00-07:00"));
  });

  it("Calendly invitee.created", () => {
    const j = normalise({
      event: "invitee.created",
      payload: {
        uri: "https://api.calendly.com/scheduled_events/AAA/invitees/BBB", name: "Lee Park", email: "lee@example.com",
        scheduled_event: { name: "Car detailing - full", start_time: "2026-11-23T16:00:00.000000Z" },
        questions_and_answers: [{ question: "Address", answer: "5 Lake Dr" }],
      },
    });
    expect(j.clientName).toBe("Lee Park");
    expect(j.clientEmail).toBe("lee@example.com");
    expect(j.externalId).toBeTruthy();
    expect(j.service).toBe("Car detailing - full");
    expect(j.scheduledAt).toBe(Date.parse("2026-11-23T16:00:00Z"));
  });

  it("Square Appointments via Make", () => {
    const j = normalise({
      booking: {
        id: "bk_9x2", status: "ACCEPTED", start_at: "2026-11-24T13:30:00Z", customer_note: "Back patio too",
        customer: { given_name: "Ivy", family_name: "Chen", phone_number: "+15550101", email_address: "ivy@example.com" },
        appointment_segments: [{ service_variation_name: "Patio clean" }],
      },
    });
    expect(j).toMatchObject({ externalId: "bk_9x2", clientName: "Ivy Chen", clientPhone: "+15550101", clientEmail: "ivy@example.com", notes: "Back patio too" });
    expect(j.scheduledAt).toBe(Date.parse("2026-11-24T13:30:00Z"));
  });

  it("Square cancelled booking", () => {
    expect(normalise({ booking: { id: "bk_9x2", status: "CANCELLED_BY_CUSTOMER" } }).cancelled).toBe(true);
  });

  it("a hand-built Zap with spaced labels", () => {
    const j = normalise({ "Job ID": "77", "Customer Name": "Bo Diaz", "Phone Number": "555", "Job Address": "9 Rd", "Service Type": "Gutter clean", "Start Date": "2026-11-25" });
    expect(j).toMatchObject({ externalId: "77", clientName: "Bo Diaz", clientPhone: "555", address: "9 Rd", service: "Gutter clean" });
    expect(j.scheduledAt).toBe(Date.parse("2026-11-25"));
  });
});
