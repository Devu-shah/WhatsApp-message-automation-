import crypto from "node:crypto";

export interface RegisteredDeviceRecord {
  keyFingerprint: string;
  publicKeyBase64: string;
  deviceLabel: string;
  appVersion: string;
  registeredAtMs: number;
  revoked: boolean;
}

export interface SignedRequestHeaders {
  keyFingerprint?: string;
  timestamp?: string;
  nonce?: string;
  signature?: string;
}

export interface VerificationStoreAdapter {
  getDevice(keyFingerprint: string): Promise<RegisteredDeviceRecord | null>;
  consumeNonceOnce(keyFingerprint: string, nonce: string, expiresAtMs: number): Promise<boolean>;
}

export interface RequestVerificationResult {
  ok: boolean;
  statusCode: number;
  error?: string;
  device?: RegisteredDeviceRecord;
}

export function sha256Hex(bytes: Buffer | Uint8Array | string): string {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

export function computeSpkiFingerprint(publicKeyBase64: string): string {
  const der = Buffer.from(publicKeyBase64.trim(), "base64");
  const digest = crypto.createHash("sha256").update(der).digest();
  return Array.from(digest)
    .map((b) => b.toString(16).padStart(2, "0").toUpperCase())
    .join(":");
}

export function buildCanonicalPayload(
  method: string,
  path: string,
  timestampMs: string,
  nonce: string,
  bodyDigestSource: Buffer | Uint8Array | string
): string {
  const bodySha256 = sha256Hex(bodyDigestSource);
  return `${method.toUpperCase()}\n${path}\n${timestampMs}\n${nonce}\n${bodySha256}`;
}

export function verifyEcdsaP256Signature(
  publicKeyBase64: string,
  canonicalPayload: string,
  signatureBase64: string
): boolean {
  try {
    const spkiDer = Buffer.from(publicKeyBase64.trim(), "base64");
    const sigDer = Buffer.from(signatureBase64.trim(), "base64");
    if (spkiDer.length === 0 || sigDer.length === 0) {
      return false;
    }
    const keyObject = crypto.createPublicKey({
      key: spkiDer,
      format: "der",
      type: "spki",
    });
    return crypto.verify(
      "sha256",
      Buffer.from(canonicalPayload, "utf8"),
      {
        key: keyObject,
        dsaEncoding: "der",
      },
      sigDer
    );
  } catch {
    return false;
  }
}

export async function verifySignedDeviceRequest(params: {
  method: string;
  path: string;
  bodyDigestSource: Buffer | Uint8Array | string;
  headers: SignedRequestHeaders;
  store: VerificationStoreAdapter;
  nowMs?: number;
  allowedSkewSeconds?: number;
  inlinePublicKeyBase64?: string;
}): Promise<RequestVerificationResult> {
  const nowMs = params.nowMs ?? Date.now();
  const allowedSkewMs = (params.allowedSkewSeconds ?? 300) * 1000;
  const fingerprint = params.headers.keyFingerprint?.trim() ?? "";
  const timestampStr = params.headers.timestamp?.trim() ?? "";
  const nonce = params.headers.nonce?.trim() ?? "";
  const signature = params.headers.signature?.trim() ?? "";

  if (!fingerprint || !timestampStr || !nonce || !signature) {
    return { ok: false, statusCode: 401, error: "Missing required ReachHub authentication headers" };
  }

  const timestampMs = Number(timestampStr);
  if (!Number.isFinite(timestampMs) || timestampMs <= 0) {
    return { ok: false, statusCode: 401, error: "Invalid request timestamp" };
  }

  if (Math.abs(nowMs - timestampMs) > allowedSkewMs) {
    return { ok: false, statusCode: 401, error: "Request timestamp outside allowed window" };
  }

  let device: RegisteredDeviceRecord | null = await params.store.getDevice(fingerprint);
  let verificationPublicKey = device?.publicKeyBase64 ?? "";

  if (!device && params.inlinePublicKeyBase64) {
    const computed = computeSpkiFingerprint(params.inlinePublicKeyBase64);
    if (computed !== fingerprint) {
      return { ok: false, statusCode: 401, error: "Public key fingerprint mismatch" };
    }
    verificationPublicKey = params.inlinePublicKeyBase64;
  }

  if (!verificationPublicKey) {
    return { ok: false, statusCode: 401, error: "Device public key is not registered" };
  }

  if (device?.revoked) {
    return { ok: false, statusCode: 403, error: "Device signing key has been revoked" };
  }

  const canonical = buildCanonicalPayload(
    params.method,
    params.path,
    timestampStr,
    nonce,
    params.bodyDigestSource
  );
  const signatureValid = verifyEcdsaP256Signature(verificationPublicKey, canonical, signature);
  if (!signatureValid) {
    return { ok: false, statusCode: 401, error: "Invalid ECDSA P-256 request signature" };
  }

  const nonceAccepted = await params.store.consumeNonceOnce(
    fingerprint,
    nonce,
    timestampMs + allowedSkewMs
  );
  if (!nonceAccepted) {
    return { ok: false, statusCode: 409, error: "Replay detected: nonce already used" };
  }

  return { ok: true, statusCode: 200, device: device ?? undefined };
}

export class SlidingWindowRateLimiter {
  private readonly hits = new Map<string, number[]>();
  private readonly maxRequests: number;
  private readonly windowMs: number;

  constructor(maxRequests = 240, windowMs = 60_000) {
    this.maxRequests = maxRequests;
    this.windowMs = windowMs;
  }

  allow(key: string, nowMs = Date.now()): boolean {
    const cutoff = nowMs - this.windowMs;
    const prev = (this.hits.get(key) ?? []).filter((ts) => ts > cutoff);
    if (prev.length >= this.maxRequests) {
      this.hits.set(key, prev);
      return false;
    }
    prev.push(nowMs);
    this.hits.set(key, prev);
    return true;
  }
}
