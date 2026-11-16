import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import {
  buildCanonicalPayload,
  computeSpkiFingerprint,
  SlidingWindowRateLimiter,
  verifySignedDeviceRequest,
} from "../src/security/deviceAuth.ts";
import {
  parseMetaWebhookStatusUpdates,
  verifyMetaWebhookSignature,
  verifyWebhookChallenge,
} from "../src/security/webhookSignature.ts";
import { canTransitionStatus, InMemoryBackendStore } from "../src/services/store.ts";
import { classifyMetaError } from "../src/services/metaCloudApi.ts";

function createTestEcdsaDevice() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
  const spkiDer = publicKey.export({ format: "der", type: "spki" });
  const publicKeyBase64 = spkiDer.toString("base64");
  const fingerprint = computeSpkiFingerprint(publicKeyBase64);
  return { publicKeyBase64, privateKey, fingerprint };
}

function signCanonical(privateKey, canonical) {
  return crypto
    .sign("sha256", Buffer.from(canonical, "utf8"), {
      key: privateKey,
      dsaEncoding: "der",
    })
    .toString("base64");
}

test("ECDSA P-256 request signing verifies valid request and rejects replay or tampered body", async () => {
  const store = new InMemoryBackendStore();
  const device = createTestEcdsaDevice();
  await store.saveDevice({
    keyFingerprint: device.fingerprint,
    publicKeyBase64: device.publicKeyBase64,
    deviceLabel: "Tablet Test",
    appVersion: "1.0.0",
    registeredAtMs: Date.now(),
    revoked: false,
  });

  const nowMs = Date.now();
  const body = Buffer.from(JSON.stringify({ action: "PAUSE" }), "utf8");
  const nonce = "nonce-uuid-1";
  const canonical = buildCanonicalPayload("POST", "/api/v1/campaigns/1/control", String(nowMs), nonce, body);
  const sig = signCanonical(device.privateKey, canonical);

  const validResult = await verifySignedDeviceRequest({
    method: "POST",
    path: "/api/v1/campaigns/1/control",
    bodyDigestSource: body,
    headers: {
      keyFingerprint: device.fingerprint,
      timestamp: String(nowMs),
      nonce,
      signature: sig,
    },
    store,
    nowMs,
  });
  assert.equal(validResult.ok, true);
  assert.equal(validResult.statusCode, 200);

  // Replay same nonce must fail with 409
  const replayResult = await verifySignedDeviceRequest({
    method: "POST",
    path: "/api/v1/campaigns/1/control",
    bodyDigestSource: body,
    headers: {
      keyFingerprint: device.fingerprint,
      timestamp: String(nowMs),
      nonce,
      signature: sig,
    },
    store,
    nowMs,
  });
  assert.equal(replayResult.ok, false);
  assert.equal(replayResult.statusCode, 409);

  // Tampered body must fail 401 even with new nonce
  const tamperedResult = await verifySignedDeviceRequest({
    method: "POST",
    path: "/api/v1/campaigns/1/control",
    bodyDigestSource: Buffer.from(JSON.stringify({ action: "CANCEL" }), "utf8"),
    headers: {
      keyFingerprint: device.fingerprint,
      timestamp: String(nowMs),
      nonce: "nonce-uuid-2",
      signature: sig,
    },
    store,
    nowMs,
  });
  assert.equal(tamperedResult.ok, false);
  assert.equal(tamperedResult.statusCode, 401);
});

test("Rate limiter caps excessive requests per device window", () => {
  const limiter = new SlidingWindowRateLimiter(3, 60_000);
  const base = 1_700_000_000_000;
  assert.equal(limiter.allow("fp1", base), true);
  assert.equal(limiter.allow("fp1", base + 100), true);
  assert.equal(limiter.allow("fp1", base + 200), true);
  assert.equal(limiter.allow("fp1", base + 300), false);
  assert.equal(limiter.allow("fp1", base + 61_000), true);
});

test("Meta webhook challenge & X-Hub-Signature-256 HMAC verification", () => {
  const challenge = verifyWebhookChallenge("subscribe", "my-verify-tok", "12345", "my-verify-tok");
  assert.equal(challenge.ok, true);
  assert.equal(challenge.body, "12345");

  const badChallenge = verifyWebhookChallenge("subscribe", "wrong", "12345", "my-verify-tok");
  assert.equal(badChallenge.ok, false);
  assert.equal(badChallenge.statusCode, 403);

  const appSecret = "super-secret-meta-app";
  const rawBody = Buffer.from(
    JSON.stringify({
      entry: [
        {
          changes: [
            {
              value: {
                statuses: [
                  { id: "wamid.123", status: "delivered", timestamp: "1700000100" },
                ],
              },
            },
          ],
        },
      ],
    })
  );
  const expectedHmac = crypto.createHmac("sha256", appSecret).update(rawBody).digest("hex");
  assert.equal(verifyMetaWebhookSignature(rawBody, `sha256=${expectedHmac}`, appSecret), true);
  assert.equal(verifyMetaWebhookSignature(rawBody, "sha256=" + "0".repeat(64), appSecret), false);
});

test("Webhook deduplication and monotonic out-of-order status protection", async () => {
  const store = new InMemoryBackendStore();
  await store.saveIdempotentResult({
    idempotencyKey: "idem-1",
    campaignId: 77,
    localMessageId: 12,
    status: "SUBMITTED",
    whatsappMessageId: "wamid.HBgM91",
    errorCode: null,
    errorDescription: null,
    isPermanentError: false,
    apiResponseSummary: "HTTP 200 accepted",
    updatedAtMs: 1000,
  });

  // Read arrives before Sent
  const readPayload = {
    entry: [
      {
        changes: [
          {
            value: {
              statuses: [{ id: "wamid.HBgM91", status: "read", timestamp: "1700000200" }],
            },
          },
        ],
      },
    ],
  };
  const readEvents = parseMetaWebhookStatusUpdates(readPayload);
  assert.equal(readEvents.length, 1);
  assert.equal(await store.recordWebhookEventIfNew(readEvents[0].eventDedupKey, readEvents[0]), true);
  // Duplicate event must return false
  assert.equal(await store.recordWebhookEventIfNew(readEvents[0].eventDedupKey, readEvents[0]), false);

  // Late SENT arrives after READ -> stored status must remain READ
  assert.equal(canTransitionStatus("READ", "SENT"), false);
  const sentPayload = {
    entry: [
      {
        changes: [
          {
            value: {
              statuses: [{ id: "wamid.HBgM91", status: "sent", timestamp: "1700000150" }],
            },
          },
        ],
      },
    ],
  };
  const sentEvents = parseMetaWebhookStatusUpdates(sentPayload);
  await store.recordWebhookEventIfNew(sentEvents[0].eventDedupKey, sentEvents[0]);

  const current = await store.getIdempotentResult("idem-1");
  assert.equal(current?.status, "READ");
});

test("Meta error classifier separates permanent vs temporary errors", () => {
  assert.equal(classifyMetaError("131026", "Unregistered").isPermanent, true);
  assert.equal(classifyMetaError("130429", "Rate limit").isPermanent, false);
});
