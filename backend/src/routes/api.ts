import crypto from "node:crypto";
import express, { Request, Response } from "express";
import multer from "multer";
import { BackendConfig } from "../config.js";
import {
  computeSpkiFingerprint,
  SlidingWindowRateLimiter,
  verifySignedDeviceRequest,
} from "../security/deviceAuth.js";
import { sendTemplateMessageToMeta, uploadMediaToMeta } from "../services/metaCloudApi.js";
import { BackendStore, CampaignControlAction } from "../services/store.js";

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
});

interface RawBodyRequest extends Request {
  rawBody?: Buffer;
}

function getHeaderString(req: Request, name: string): string | undefined {
  const val = req.header(name);
  return typeof val === "string" ? val : undefined;
}

function constantTimeEquals(a: string, b: string): boolean {
  const bufA = Buffer.from(a, "utf8");
  const bufB = Buffer.from(b, "utf8");
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function resolvePhoneNumberId(config: BackendConfig, senderProfileRef: string): string {
  const map = config.secrets.phoneNumberIdsByRef;
  return (
    map[senderProfileRef] ??
    map.default ??
    Object.values(map)[0] ??
    ""
  );
}

function parsePositiveId(raw: string | undefined): number | null {
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) return null;
  return parsed;
}

function detectVerifiedImageMime(buf: Buffer): "image/jpeg" | "image/png" | null {
  if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) {
    return "image/jpeg";
  }
  if (
    buf.length >= 8 &&
    buf[0] === 0x89 &&
    buf[1] === 0x50 &&
    buf[2] === 0x4e &&
    buf[3] === 0x47 &&
    buf[4] === 0x0d &&
    buf[5] === 0x0a &&
    buf[6] === 0x1a &&
    buf[7] === 0x0a
  ) {
    return "image/png";
  }
  return null;
}

const E164_REGEX = /^\+[1-9]\d{6,14}$/;

export function createApiRouter(config: BackendConfig, store: BackendStore): express.Router {
  const router = express.Router();
  const rateLimiter = new SlidingWindowRateLimiter(config.rateLimitMaxPerMinute, 60_000);

  async function requireSignedDevice(
    req: RawBodyRequest,
    res: Response,
    bodyDigestSource?: Buffer | string,
    inlinePublicKeyBase64?: string
  ): Promise<boolean> {
    const fingerprint = getHeaderString(req, "X-ReachHub-Key-Fingerprint")?.trim() ?? "anon";
    if (!rateLimiter.allow(fingerprint)) {
      res.status(429).json({ error: "Rate limit exceeded for this device" });
      return false;
    }

    const result = await verifySignedDeviceRequest({
      method: req.method,
      path: req.originalUrl.split("?")[0] ?? req.path,
      bodyDigestSource: bodyDigestSource ?? req.rawBody ?? Buffer.alloc(0),
      headers: {
        keyFingerprint: getHeaderString(req, "X-ReachHub-Key-Fingerprint"),
        timestamp: getHeaderString(req, "X-ReachHub-Timestamp"),
        nonce: getHeaderString(req, "X-ReachHub-Nonce"),
        signature: getHeaderString(req, "X-ReachHub-Signature"),
      },
      store,
      allowedSkewSeconds: config.allowedSkewSeconds,
      inlinePublicKeyBase64,
    });

    if (!result.ok) {
      res.status(result.statusCode).json({ error: result.error });
      return false;
    }
    return true;
  }

  // 1. Register operator Android Keystore ECDSA P-256 public key
  router.post("/device/register", async (req: RawBodyRequest, res: Response) => {
    const body = req.body as {
      keyFingerprint?: string;
      publicKeyBase64?: string;
      deviceLabel?: string;
      appVersion?: string;
    };

    const bootstrapRequired = config.secrets.bootstrapRegistrationToken;
    if (bootstrapRequired) {
      const providedToken = getHeaderString(req, "X-ReachHub-Bootstrap-Token")?.trim() ?? "";
      if (!providedToken || !constantTimeEquals(providedToken, bootstrapRequired)) {
        res.status(401).json({ error: "Valid bootstrap registration token is required" });
        return;
      }
    }

    if (!body?.publicKeyBase64 || !body?.keyFingerprint) {
      res.status(400).json({ error: "Missing keyFingerprint or publicKeyBase64" });
      return;
    }

    const computed = computeSpkiFingerprint(body.publicKeyBase64);
    if (computed !== body.keyFingerprint.trim()) {
      res.status(400).json({ error: "SPKI SHA-256 fingerprint mismatch" });
      return;
    }

    const verified = await requireSignedDevice(req, res, undefined, body.publicKeyBase64);
    if (!verified) return;

    await store.saveDevice({
      keyFingerprint: computed,
      publicKeyBase64: body.publicKeyBase64.trim(),
      deviceLabel: (body.deviceLabel ?? "ReachHub Android Tablet").slice(0, 80),
      appVersion: (body.appVersion ?? "1.0.0").slice(0, 32),
      registeredAtMs: Date.now(),
      revoked: false,
    });

    res.json({
      registered: true,
      keyFingerprint: computed,
      message: "Device ECDSA P-256 public key registered on Cloud Run",
    });
  });

  // 2. Live Production Readiness Verification
  router.get("/readiness", async (req: RawBodyRequest, res: Response) => {
    if (!(await requireSignedDevice(req, res))) return;

    const metaSecretsConfigured = Boolean(
      config.secrets.metaAccessToken &&
        config.secrets.metaAppSecret &&
        config.secrets.metaWabaId &&
        Object.keys(config.secrets.phoneNumberIdsByRef).length > 0
    );
    const webhookVerifyConfigured = Boolean(
      config.secrets.metaWebhookVerifyToken && config.secrets.metaAppSecret
    );

    res.json({
      ok: metaSecretsConfigured && webhookVerifyConfigured,
      deviceRegistered: true,
      metaSecretsConfigured,
      webhookVerifyConfigured,
      cloudTasksConfigured: config.cloudTasksConfigured,
      senderProfilesConfigured: Object.keys(config.secrets.phoneNumberIdsByRef),
      serverTimestampMs: Date.now(),
      message:
        metaSecretsConfigured && webhookVerifyConfigured
          ? "Cloud Run backend, Secret Manager credentials, and webhook checks ready"
          : "Some Secret Manager secrets are still missing on Cloud Run",
    });
  });

  // 3. Flyer Media Upload (SHA-256 deduplicated + uploaded to Meta Cloud API)
  router.post("/media/upload", upload.single("file"), async (req: RawBodyRequest, res: Response) => {
    const sha256Hash = String(req.body?.sha256Hash ?? "").trim().toLowerCase();
    const senderProfileRef = String(req.body?.senderProfileRef ?? "default").trim() || "default";
    const file = req.file;

    if (!file || !sha256Hash) {
      res.status(400).json({ error: "Missing flyer binary or sha256Hash" });
      return;
    }

    const canonicalDigestSource = `${sha256Hash}:${senderProfileRef}:${file.buffer.length}`;
    if (!(await requireSignedDevice(req, res, canonicalDigestSource))) return;

    const actualSha256 = crypto.createHash("sha256").update(file.buffer).digest("hex").toLowerCase();
    if (actualSha256 !== sha256Hash) {
      res.status(400).json({ error: "Uploaded flyer SHA-256 checksum mismatch" });
      return;
    }

    const verifiedMime = detectVerifiedImageMime(file.buffer);
    if (!verifiedMime) {
      res.status(400).json({ error: "Uploaded flyer failed JPEG/PNG magic-byte verification" });
      return;
    }

    const cached = await store.getMediaBySha(sha256Hash, senderProfileRef);
    if (cached) {
      res.json({
        metaMediaId: cached.metaMediaId,
        sha256Hash,
        deduplicated: true,
      });
      return;
    }

    const phoneNumberId = resolvePhoneNumberId(config, senderProfileRef);
    if (!phoneNumberId || !config.secrets.metaAccessToken) {
      res.status(503).json({
        error: "Meta Access Token or Phone Number ID mapping missing in Secret Manager",
      });
      return;
    }

    try {
      const metaMediaId = await uploadMediaToMeta({
        graphVersion: config.metaGraphVersion,
        phoneNumberId,
        accessToken: config.secrets.metaAccessToken,
        fileBytes: file.buffer,
        mimeType: verifiedMime,
        filename: String(req.body?.displayName ?? file.originalname ?? "flyer.jpg"),
      });

      await store.saveMediaMapping({
        sha256Hash,
        senderProfileRef,
        metaMediaId,
        uploadedAtMs: Date.now(),
      });

      res.json({
        metaMediaId,
        sha256Hash,
        deduplicated: false,
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Meta media upload failed";
      res.status(502).json({ error: message });
    }
  });

  // 4. Idempotent Batch Dispatch to Meta Cloud API
  router.post("/campaigns/:campaignId/dispatch", async (req: RawBodyRequest, res: Response) => {
    if (!(await requireSignedDevice(req, res))) return;

    const campaignId = parsePositiveId(req.params.campaignId);
    if (campaignId === null) {
      res.status(400).json({ error: "Invalid campaignId" });
      return;
    }
    const body = req.body as {
      senderProfileRef?: string;
      metaTemplateName?: string;
      languageCode?: string;
      metaMediaId?: string | null;
      recipients?: Array<{
        localMessageId: number;
        recipientPhoneE164: string;
        idempotencyKey: string;
        positionalParameters: string[];
      }>;
    };

    if (!body?.metaTemplateName?.trim()) {
      res.status(400).json({ error: "metaTemplateName is required" });
      return;
    }

    const controlState = await store.getCampaignControlState(campaignId);
    if (controlState === "PAUSE" || controlState === "CANCEL") {
      res.status(409).json({
        error: `Campaign ${campaignId} is currently ${controlState} on Cloud Run`,
      });
      return;
    }

    const senderProfileRef = (body.senderProfileRef ?? "default").trim() || "default";
    const phoneNumberId = resolvePhoneNumberId(config, senderProfileRef);
    if (!phoneNumberId || !config.secrets.metaAccessToken) {
      res.status(503).json({
        error: "Meta credentials missing in Secret Manager for sender profile",
      });
      return;
    }

    const recipients = Array.isArray(body.recipients) ? body.recipients : [];
    if (recipients.length === 0 || recipients.length > 200) {
      res.status(400).json({ error: "Batch must contain between 1 and 200 recipients" });
      return;
    }
    for (const r of recipients) {
      if (
        !r ||
        typeof r.idempotencyKey !== "string" ||
        r.idempotencyKey.trim().length < 8 ||
        typeof r.recipientPhoneE164 !== "string" ||
        !E164_REGEX.test(r.recipientPhoneE164.trim())
      ) {
        res.status(400).json({ error: "Invalid recipient phone E.164 or idempotencyKey in batch" });
        return;
      }
    }
    const results = [];
    let acceptedCount = 0;

    for (const item of recipients) {
      const existing = await store.getIdempotentResult(item.idempotencyKey);
      if (existing && existing.status !== "FAILED") {
        results.push({
          localMessageId: item.localMessageId,
          idempotencyKey: item.idempotencyKey,
          status: existing.status,
          whatsappMessageId: existing.whatsappMessageId,
          errorCode: existing.errorCode,
          errorDescription: existing.errorDescription,
          isPermanentError: existing.isPermanentError,
          apiResponseSummary: "IDEMPOTENT_CACHE_HIT",
        });
        if (existing.status === "SUBMITTED") acceptedCount++;
        continue;
      }

      const sendOutcome = await sendTemplateMessageToMeta({
        graphVersion: config.metaGraphVersion,
        phoneNumberId,
        accessToken: config.secrets.metaAccessToken,
        recipientPhoneE164: item.recipientPhoneE164,
        metaTemplateName: body.metaTemplateName ?? "",
        languageCode: body.languageCode ?? "en",
        positionalParameters: Array.isArray(item.positionalParameters)
          ? item.positionalParameters
          : [],
        metaMediaId: body.metaMediaId ?? null,
      });

      await store.saveIdempotentResult({
        idempotencyKey: item.idempotencyKey,
        campaignId,
        localMessageId: item.localMessageId,
        status: sendOutcome.status,
        whatsappMessageId: sendOutcome.whatsappMessageId,
        errorCode: sendOutcome.errorCode,
        errorDescription: sendOutcome.errorDescription,
        isPermanentError: sendOutcome.isPermanentError,
        apiResponseSummary: sendOutcome.apiResponseSummary,
        updatedAtMs: Date.now(),
      });

      if (sendOutcome.status === "SUBMITTED") acceptedCount++;
      results.push({
        localMessageId: item.localMessageId,
        idempotencyKey: item.idempotencyKey,
        status: sendOutcome.status,
        whatsappMessageId: sendOutcome.whatsappMessageId,
        errorCode: sendOutcome.errorCode,
        errorDescription: sendOutcome.errorDescription,
        isPermanentError: sendOutcome.isPermanentError,
        apiResponseSummary: sendOutcome.apiResponseSummary,
      });
    }

    res.json({
      campaignId,
      acceptedCount,
      results,
    });
  });

  // 5. Campaign Pause / Resume / Cancel synchronization
  router.post("/campaigns/:campaignId/control", async (req: RawBodyRequest, res: Response) => {
    if (!(await requireSignedDevice(req, res))) return;
    const campaignId = parsePositiveId(req.params.campaignId);
    if (campaignId === null) {
      res.status(400).json({ error: "Invalid campaignId" });
      return;
    }
    const action = String(req.body?.action ?? "").toUpperCase() as CampaignControlAction;
    if (!["PAUSE", "RESUME", "CANCEL"].includes(action)) {
      res.status(400).json({ error: "Action must be PAUSE, RESUME, or CANCEL" });
      return;
    }
    await store.setCampaignControlState(campaignId, action);
    res.json({ campaignId, action, acknowledged: true });
  });

  // 6. Webhook Event Status Sync Polling for Android Tablet
  router.get("/campaigns/:campaignId/status", async (req: RawBodyRequest, res: Response) => {
    if (!(await requireSignedDevice(req, res))) return;
    const campaignId = parsePositiveId(req.params.campaignId);
    if (campaignId === null) {
      res.status(400).json({ error: "Invalid campaignId" });
      return;
    }
    const rawSince = Number(req.query.since ?? "0");
    const sinceMs = Number.isFinite(rawSince) && rawSince >= 0 ? rawSince : 0;
    const events = await store.getCampaignEvents(campaignId, sinceMs);
    const nextCursorMs =
      events.length > 0 ? events[events.length - 1]!.eventTimestampMs : sinceMs;
    res.json({
      campaignId,
      events,
      nextCursorMs,
    });
  });

  return router;
}
