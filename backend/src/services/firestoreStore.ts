import { Firestore } from "@google-cloud/firestore";
import {
  BackendStore,
  CampaignControlAction,
  CampaignStatusEventRecord,
  canTransitionStatus,
  IdempotentDispatchRecord,
  StoredDevice,
  StoredMediaMapping,
} from "./store.js";

function safeDocId(input: string): string {
  return Buffer.from(input, "utf8").toString("base64url");
}

export class FirestoreBackendStore implements BackendStore {
  private readonly db: Firestore;
  private readonly prefix: string;

  constructor(prefix = "reachhub") {
    this.db = new Firestore();
    this.prefix = prefix;
  }

  private col(name: string) {
    return this.db.collection(`${this.prefix}_${name}`);
  }

  async getDevice(keyFingerprint: string): Promise<StoredDevice | null> {
    const snap = await this.col("devices").doc(safeDocId(keyFingerprint)).get();
    return snap.exists ? (snap.data() as StoredDevice) : null;
  }

  async saveDevice(device: StoredDevice): Promise<void> {
    await this.col("devices").doc(safeDocId(device.keyFingerprint)).set(device);
  }

  async consumeNonceOnce(
    keyFingerprint: string,
    nonce: string,
    expiresAtMs: number
  ): Promise<boolean> {
    const ref = this.col("nonces").doc(safeDocId(`${keyFingerprint}:${nonce}`));
    try {
      await ref.create({ keyFingerprint, nonce, expiresAtMs, createdAtMs: Date.now() });
      return true;
    } catch {
      return false;
    }
  }

  async getMediaBySha(
    sha256Hash: string,
    senderProfileRef: string
  ): Promise<StoredMediaMapping | null> {
    const snap = await this.col("media").doc(safeDocId(`${senderProfileRef}:${sha256Hash}`)).get();
    return snap.exists ? (snap.data() as StoredMediaMapping) : null;
  }

  async saveMediaMapping(mapping: StoredMediaMapping): Promise<void> {
    await this.col("media")
      .doc(safeDocId(`${mapping.senderProfileRef}:${mapping.sha256Hash}`))
      .set(mapping);
  }

  async getIdempotentResult(idempotencyKey: string): Promise<IdempotentDispatchRecord | null> {
    const snap = await this.col("idempotency").doc(safeDocId(idempotencyKey)).get();
    return snap.exists ? (snap.data() as IdempotentDispatchRecord) : null;
  }

  async saveIdempotentResult(record: IdempotentDispatchRecord): Promise<void> {
    const ref = this.col("idempotency").doc(safeDocId(record.idempotencyKey));
    await this.db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (snap.exists) {
        const existing = snap.data() as IdempotentDispatchRecord;
        if (!canTransitionStatus(existing.status, record.status)) {
          return;
        }
      }
      tx.set(ref, record);
      if (record.whatsappMessageId) {
        tx.set(this.col("wamid_index").doc(safeDocId(record.whatsappMessageId)), {
          idempotencyKey: record.idempotencyKey,
        });
      }
    });
  }

  async findByWhatsappMessageId(wamid: string): Promise<IdempotentDispatchRecord | null> {
    const idx = await this.col("wamid_index").doc(safeDocId(wamid)).get();
    if (!idx.exists) return null;
    const key = (idx.data() as { idempotencyKey?: string })?.idempotencyKey;
    if (!key) return null;
    return this.getIdempotentResult(key);
  }

  async setCampaignControlState(campaignId: number, action: CampaignControlAction): Promise<void> {
    await this.col("campaign_controls").doc(String(campaignId)).set({
      campaignId,
      action,
      updatedAtMs: Date.now(),
    });
  }

  async getCampaignControlState(campaignId: number): Promise<CampaignControlAction | null> {
    const snap = await this.col("campaign_controls").doc(String(campaignId)).get();
    if (!snap.exists) return null;
    return ((snap.data() as { action?: CampaignControlAction })?.action ?? null);
  }

  async recordWebhookEventIfNew(
    dedupKey: string,
    event: Omit<CampaignStatusEventRecord, "campaignId">
  ): Promise<boolean> {
    const dedupRef = this.col("webhook_dedup").doc(safeDocId(dedupKey));
    try {
      await dedupRef.create({ dedupKey, createdAtMs: Date.now() });
    } catch {
      return false;
    }

    const record = await this.findByWhatsappMessageId(event.whatsappMessageId);
    if (!record) {
      return true;
    }

    if (canTransitionStatus(record.status, event.status)) {
      await this.saveIdempotentResult({
        ...record,
        status: event.status,
        errorCode: event.status === "FAILED" ? event.errorCode : record.errorCode,
        errorDescription:
          event.status === "FAILED" ? event.errorDescription : record.errorDescription,
        isPermanentError: event.status === "FAILED" ? event.isPermanentError : false,
        updatedAtMs: event.eventTimestampMs,
      });
    }

    const fullEvent: CampaignStatusEventRecord = {
      ...event,
      campaignId: record.campaignId,
      idempotencyKey: record.idempotencyKey,
    };
    await this.col("status_events").add(fullEvent);
    return true;
  }

  async getCampaignEvents(
    campaignId: number,
    sinceTimestampMs: number
  ): Promise<CampaignStatusEventRecord[]> {
    const snap = await this.col("status_events")
      .where("campaignId", "==", campaignId)
      .where("eventTimestampMs", ">", sinceTimestampMs)
      .orderBy("eventTimestampMs", "asc")
      .get();

    return snap.docs.map((doc) => doc.data() as CampaignStatusEventRecord);
  }
}
