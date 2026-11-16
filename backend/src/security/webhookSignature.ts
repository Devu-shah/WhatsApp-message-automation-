import crypto from "node:crypto";

export interface ParsedWebhookStatusEvent {
  eventDedupKey: string;
  whatsappMessageId: string;
  status: "SENT" | "DELIVERED" | "READ" | "FAILED";
  eventTimestampMs: number;
  errorCode: string | null;
  errorDescription: string | null;
  isPermanentError: boolean;
  recipientWaId: string | null;
}

const PERMANENT_META_ERROR_CODES = new Set([
  "131026",
  "131030",
  "131047",
  "131051",
  "132000",
  "132001",
  "132005",
  "132012",
  "132015",
  "132016",
  "133010",
  "368",
  "100",
]);

export function verifyMetaWebhookSignature(
  rawBody: Buffer,
  signatureHeader: string | undefined,
  appSecret: string
): boolean {
  if (!signatureHeader || !appSecret) {
    return false;
  }
  const normalized = signatureHeader.trim();
  if (!normalized.startsWith("sha256=")) {
    return false;
  }
  const receivedHex = normalized.slice("sha256=".length).toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(receivedHex)) {
    return false;
  }
  const expectedHex = crypto
    .createHmac("sha256", appSecret)
    .update(rawBody)
    .digest("hex")
    .toLowerCase();

  const receivedBuf = Buffer.from(receivedHex, "hex");
  const expectedBuf = Buffer.from(expectedHex, "hex");
  if (receivedBuf.length !== expectedBuf.length) {
    return false;
  }
  return crypto.timingSafeEqual(receivedBuf, expectedBuf);
}

export function verifyWebhookChallenge(
  mode: string | undefined,
  token: string | undefined,
  challenge: string | undefined,
  expectedVerifyToken: string
): { ok: boolean; statusCode: number; body: string } {
  if (
    mode === "subscribe" &&
    token &&
    expectedVerifyToken &&
    token.length === expectedVerifyToken.length &&
    crypto.timingSafeEqual(Buffer.from(token), Buffer.from(expectedVerifyToken))
  ) {
    return { ok: true, statusCode: 200, body: challenge ?? "" };
  }
  return { ok: false, statusCode: 403, body: "Webhook verify token mismatch" };
}

export function parseMetaWebhookStatusUpdates(payload: unknown): ParsedWebhookStatusEvent[] {
  const events: ParsedWebhookStatusEvent[] = [];
  if (!payload || typeof payload !== "object") {
    return events;
  }
  const root = payload as { entry?: Array<{ changes?: Array<{ value?: any }> }> };
  const entries = Array.isArray(root.entry) ? root.entry : [];

  for (const entry of entries) {
    const changes = Array.isArray(entry?.changes) ? entry.changes : [];
    for (const change of changes) {
      const statuses = Array.isArray(change?.value?.statuses) ? change.value.statuses : [];
      for (const st of statuses) {
        const wamid = typeof st?.id === "string" ? st.id.trim() : "";
        const rawStatus = typeof st?.status === "string" ? st.status.trim().toUpperCase() : "";
        if (!wamid) continue;

        let mappedStatus: "SENT" | "DELIVERED" | "READ" | "FAILED" | null = null;
        if (rawStatus === "SENT") mappedStatus = "SENT";
        else if (rawStatus === "DELIVERED") mappedStatus = "DELIVERED";
        else if (rawStatus === "READ") mappedStatus = "READ";
        else if (rawStatus === "FAILED") mappedStatus = "FAILED";
        if (!mappedStatus) continue;

        const tsSeconds = Number(st?.timestamp ?? 0);
        const eventTimestampMs =
          Number.isFinite(tsSeconds) && tsSeconds > 0
            ? tsSeconds < 10_000_000_000
              ? tsSeconds * 1000
              : tsSeconds
            : Date.now();

        const firstError = Array.isArray(st?.errors) && st.errors.length > 0 ? st.errors[0] : null;
        const errorCode =
          firstError?.code !== undefined && firstError?.code !== null
            ? String(firstError.code)
            : null;
        const errorDescription =
          typeof firstError?.message === "string"
            ? firstError.message
            : typeof firstError?.title === "string"
              ? firstError.title
              : null;

        const isPermanentError =
          mappedStatus === "FAILED" && errorCode !== null && PERMANENT_META_ERROR_CODES.has(errorCode);

        const dedupKey = `${wamid}:${mappedStatus}:${eventTimestampMs}`;
        events.push({
          eventDedupKey: dedupKey,
          whatsappMessageId: wamid,
          status: mappedStatus,
          eventTimestampMs,
          errorCode,
          errorDescription,
          isPermanentError,
          recipientWaId: typeof st?.recipient_id === "string" ? st.recipient_id : null,
        });
      }
    }
  }
  return events;
}
