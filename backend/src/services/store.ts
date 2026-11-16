export type CampaignControlAction = "PAUSE" | "RESUME" | "CANCEL";
export type MessageDeliveryStatus = "QUEUED" | "SUBMITTED" | "SENT" | "DELIVERED" | "READ" | "FAILED";

export interface StoredDevice {
  keyFingerprint: string;
  publicKeyBase64: string;
  deviceLabel: string;
  appVersion: string;
  registeredAtMs: number;
  revoked: boolean;
}

export interface StoredMediaMapping {
  sha256Hash: string;
  senderProfileRef: string;
  metaMediaId: string;
  uploadedAtMs: number;
}

export interface IdempotentDispatchRecord {
  idempotencyKey: string;
  campaignId: number;
  localMessageId: number;
  status: MessageDeliveryStatus;
  whatsappMessageId: string | null;
  errorCode: string | null;
  errorDescription: string | null;
  isPermanentError: boolean;
  apiResponseSummary: string | null;
  updatedAtMs: number;
}

export interface CampaignStatusEventRecord {
  campaignId: number;
  whatsappMessageId: string;
  idempotencyKey: string | null;
  status: "SENT" | "DELIVERED" | "READ" | "FAILED";
  eventTimestampMs: number;
  errorCode: string | null;
  errorDescription: string | null;
  isPermanentError: boolean;
  source: string;
}

export const STATUS_PRECEDENCE: Record<MessageDeliveryStatus, number> = {
  QUEUED: 0,
  SUBMITTED: 1,
  SENT: 2,
  DELIVERED: 3,
  READ: 4,
  FAILED: 1,
};

export function canTransitionStatus(
  currentStatus: MessageDeliveryStatus,
  targetStatus: MessageDeliveryStatus
): boolean {
  if (currentStatus === targetStatus) return true;
  if (targetStatus === "FAILED") {
    return currentStatus === "QUEUED" || currentStatus === "SUBMITTED" || currentStatus === "SENT";
  }
  if (currentStatus === "FAILED") {
    return STATUS_PRECEDENCE[targetStatus] >= STATUS_PRECEDENCE.SENT;
  }
  return STATUS_PRECEDENCE[targetStatus] >= STATUS_PRECEDENCE[currentStatus];
}

export interface BackendStore {
  getDevice(keyFingerprint: string): Promise<StoredDevice | null>;
  saveDevice(device: StoredDevice): Promise<void>;
  consumeNonceOnce(keyFingerprint: string, nonce: string, expiresAtMs: number): Promise<boolean>;

  getMediaBySha(sha256Hash: string, senderProfileRef: string): Promise<StoredMediaMapping | null>;
  saveMediaMapping(mapping: StoredMediaMapping): Promise<void>;

  getIdempotentResult(idempotencyKey: string): Promise<IdempotentDispatchRecord | null>;
  saveIdempotentResult(record: IdempotentDispatchRecord): Promise<void>;
  findByWhatsappMessageId(wamid: string): Promise<IdempotentDispatchRecord | null>;

  setCampaignControlState(campaignId: number, action: CampaignControlAction): Promise<void>;
  getCampaignControlState(campaignId: number): Promise<CampaignControlAction | null>;

  recordWebhookEventIfNew(dedupKey: string, event: Omit<CampaignStatusEventRecord, "campaignId">): Promise<boolean>;
  getCampaignEvents(campaignId: number, sinceTimestampMs: number): Promise<CampaignStatusEventRecord[]>;
}

export class InMemoryBackendStore implements BackendStore {
  private readonly devices = new Map<string, StoredDevice>();
  private readonly nonces = new Map<string, number>();
  private readonly media = new Map<string, StoredMediaMapping>();
  private readonly idempotency = new Map<string, IdempotentDispatchRecord>();
  private readonly wamidToKey = new Map<string, string>();
  private readonly campaignControl = new Map<number, CampaignControlAction>();
  private readonly webhookDedup = new Set<string>();
  private readonly events: CampaignStatusEventRecord[] = [];

  async getDevice(keyFingerprint: string): Promise<StoredDevice | null> {
    return this.devices.get(keyFingerprint) ?? null;
  }

  async saveDevice(device: StoredDevice): Promise<void> {
    this.devices.set(device.keyFingerprint, device);
  }

  async consumeNonceOnce(keyFingerprint: string, nonce: string, expiresAtMs: number): Promise<boolean> {
    const now = Date.now();
    for (const [k, exp] of this.nonces.entries()) {
      if (exp < now) this.nonces.delete(k);
    }
    const composite = `${keyFingerprint}:${nonce}`;
    if (this.nonces.has(composite)) {
      return false;
    }
    this.nonces.set(composite, expiresAtMs);
    return true;
  }

  async getMediaBySha(sha256Hash: string, senderProfileRef: string): Promise<StoredMediaMapping | null> {
    return this.media.get(`${senderProfileRef}:${sha256Hash}`) ?? null;
  }

  async saveMediaMapping(mapping: StoredMediaMapping): Promise<void> {
    this.media.set(`${mapping.senderProfileRef}:${mapping.sha256Hash}`, mapping);
  }

  async getIdempotentResult(idempotencyKey: string): Promise<IdempotentDispatchRecord | null> {
    return this.idempotency.get(idempotencyKey) ?? null;
  }

  async saveIdempotentResult(record: IdempotentDispatchRecord): Promise<void> {
    const existing = this.idempotency.get(record.idempotencyKey);
    if (existing && !canTransitionStatus(existing.status, record.status)) {
      return;
    }
    this.idempotency.set(record.idempotencyKey, record);
    if (record.whatsappMessageId) {
      this.wamidToKey.set(record.whatsappMessageId, record.idempotencyKey);
    }
  }

  async findByWhatsappMessageId(wamid: string): Promise<IdempotentDispatchRecord | null> {
    const key = this.wamidToKey.get(wamid);
    if (!key) return null;
    return this.idempotency.get(key) ?? null;
  }

  async setCampaignControlState(campaignId: number, action: CampaignControlAction): Promise<void> {
    this.campaignControl.set(campaignId, action);
  }

  async getCampaignControlState(campaignId: number): Promise<CampaignControlAction | null> {
    return this.campaignControl.get(campaignId) ?? null;
  }

  async recordWebhookEventIfNew(
    dedupKey: string,
    event: Omit<CampaignStatusEventRecord, "campaignId">
  ): Promise<boolean> {
    if (this.webhookDedup.has(dedupKey)) {
      return false;
    }
    this.webhookDedup.add(dedupKey);

    const record = await this.findByWhatsappMessageId(event.whatsappMessageId);
    if (!record) {
      return true;
    }

    if (canTransitionStatus(record.status, event.status)) {
      await this.saveIdempotentResult({
        ...record,
        status: event.status,
        errorCode: event.status === "FAILED" ? event.errorCode : record.errorCode,
        errorDescription: event.status === "FAILED" ? event.errorDescription : record.errorDescription,
        isPermanentError: event.status === "FAILED" ? event.isPermanentError : false,
        updatedAtMs: event.eventTimestampMs,
      });
    }

    this.events.push({
      ...event,
      campaignId: record.campaignId,
      idempotencyKey: record.idempotencyKey,
    });
    return true;
  }

  async getCampaignEvents(
    campaignId: number,
    sinceTimestampMs: number
  ): Promise<CampaignStatusEventRecord[]> {
    return this.events
      .filter((e) => e.campaignId === campaignId && e.eventTimestampMs > sinceTimestampMs)
      .sort((a, b) => a.eventTimestampMs - b.eventTimestampMs);
  }
}
