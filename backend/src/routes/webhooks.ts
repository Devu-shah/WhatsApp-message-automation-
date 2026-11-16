import express, { Request, Response } from "express";
import { BackendConfig } from "../config.js";
import {
  parseMetaWebhookStatusUpdates,
  verifyMetaWebhookSignature,
  verifyWebhookChallenge,
} from "../security/webhookSignature.js";
import { BackendStore } from "../services/store.js";

interface RawBodyRequest extends Request {
  rawBody?: Buffer;
}

export function createWebhookRouter(config: BackendConfig, store: BackendStore): express.Router {
  const router = express.Router();

  // Meta Webhook Verification Handshake (GET /webhooks/whatsapp)
  router.get("/whatsapp", (req: Request, res: Response) => {
    const mode = typeof req.query["hub.mode"] === "string" ? req.query["hub.mode"] : undefined;
    const token =
      typeof req.query["hub.verify_token"] === "string" ? req.query["hub.verify_token"] : undefined;
    const challenge =
      typeof req.query["hub.challenge"] === "string" ? req.query["hub.challenge"] : undefined;

    const check = verifyWebhookChallenge(
      mode,
      token,
      challenge,
      config.secrets.metaWebhookVerifyToken
    );
    res.status(check.statusCode).send(check.body);
  });

  // Meta Webhook Delivery & Read Receipt Receiver (POST /webhooks/whatsapp)
  router.post("/whatsapp", async (req: RawBodyRequest, res: Response) => {
    const rawBody = req.rawBody ?? Buffer.from(JSON.stringify(req.body ?? {}));
    const sigHeader = req.header("X-Hub-Signature-256") ?? undefined;

    if (!verifyMetaWebhookSignature(rawBody, sigHeader, config.secrets.metaAppSecret)) {
      res.status(401).json({ error: "Invalid X-Hub-Signature-256 webhook signature" });
      return;
    }

    const updates = parseMetaWebhookStatusUpdates(req.body);
    let applied = 0;
    let duplicates = 0;

    for (const update of updates) {
      const inserted = await store.recordWebhookEventIfNew(update.eventDedupKey, {
        whatsappMessageId: update.whatsappMessageId,
        idempotencyKey: null,
        status: update.status,
        eventTimestampMs: update.eventTimestampMs,
        errorCode: update.errorCode,
        errorDescription: update.errorDescription,
        isPermanentError: update.isPermanentError,
        source: "meta_webhook",
      });
      if (inserted) applied++;
      else duplicates++;
    }

    res.status(200).json({ received: true, applied, duplicates });
  });

  return router;
}
