import express, { Request, Response } from "express";
import { loadBackendConfig } from "./config.js";
import { createApiRouter } from "./routes/api.js";
import { createWebhookRouter } from "./routes/webhooks.js";
import { FirestoreBackendStore } from "./services/firestoreStore.js";
import { BackendStore, InMemoryBackendStore } from "./services/store.js";

interface RawBodyRequest extends Request {
  rawBody?: Buffer;
}

async function main() {
  const config = await loadBackendConfig();
  const store: BackendStore = config.useInMemoryStore
    ? new InMemoryBackendStore()
    : new FirestoreBackendStore(config.firestorePrefix);

  const app = express();
  app.disable("x-powered-by");

  app.use(
    express.json({
      limit: "2mb",
      verify: (req: RawBodyRequest, _res, buf) => {
        req.rawBody = buf;
      },
    })
  );

  app.get("/health", (_req: Request, res: Response) => {
    res.json({
      status: "ok",
      service: "reachhub-cloudrun-backend",
      store: config.useInMemoryStore ? "in_memory" : "firestore",
      metaGraphVersion: config.metaGraphVersion,
    });
  });

  app.use("/api/v1", createApiRouter(config, store));
  app.use("/webhooks", createWebhookRouter(config, store));

  app.listen(config.port, () => {
    console.log(
      JSON.stringify({
        msg: "ReachHub Cloud Run backend listening",
        port: config.port,
        store: config.useInMemoryStore ? "in_memory" : "firestore",
      })
    );
  });
}

main().catch((err) => {
  console.error("Failed to start ReachHub backend:", err);
  process.exit(1);
});
