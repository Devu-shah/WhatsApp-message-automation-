import { SecretManagerServiceClient } from "@google-cloud/secret-manager";

export interface RuntimeSecrets {
  metaAccessToken: string;
  metaAppSecret: string;
  metaWebhookVerifyToken: string;
  metaWabaId: string;
  phoneNumberIdsByRef: Record<string, string>;
  bootstrapRegistrationToken: string;
}

export interface BackendConfig {
  port: number;
  gcpProjectId: string;
  metaGraphVersion: string;
  cloudTasksConfigured: boolean;
  allowedSkewSeconds: number;
  rateLimitMaxPerMinute: number;
  firestorePrefix: string;
  useInMemoryStore: boolean;
  secrets: RuntimeSecrets;
}

async function fetchSecretValue(
  client: SecretManagerServiceClient | null,
  projectId: string,
  secretName: string,
  envFallbackKey: string
): Promise<string> {
  const direct = process.env[envFallbackKey]?.trim();
  if (direct) return direct;
  if (!client || !projectId || !secretName) return "";

  try {
    const [version] = await client.accessSecretVersion({
      name: `projects/${projectId}/secrets/${secretName}/versions/latest`,
    });
    return version.payload?.data?.toString("utf8")?.trim() ?? "";
  } catch {
    return "";
  }
}

export async function loadBackendConfig(): Promise<BackendConfig> {
  const gcpProjectId = (process.env.GCP_PROJECT_ID ?? process.env.GOOGLE_CLOUD_PROJECT ?? "").trim();
  const useInMemoryStore = process.env.FIRESTORE_DISABLED === "true" || !gcpProjectId;
  const secretClient = gcpProjectId ? new SecretManagerServiceClient() : null;

  const [
    metaAccessToken,
    metaAppSecret,
    metaWebhookVerifyToken,
    metaWabaId,
    phoneMapJson,
    bootstrapRegistrationToken,
  ] = await Promise.all([
    fetchSecretValue(
      secretClient,
      gcpProjectId,
      process.env.SECRET_META_ACCESS_TOKEN ?? "reachhub-meta-access-token",
      "META_ACCESS_TOKEN"
    ),
    fetchSecretValue(
      secretClient,
      gcpProjectId,
      process.env.SECRET_META_APP_SECRET ?? "reachhub-meta-app-secret",
      "META_APP_SECRET"
    ),
    fetchSecretValue(
      secretClient,
      gcpProjectId,
      process.env.SECRET_META_WEBHOOK_VERIFY_TOKEN ?? "reachhub-meta-webhook-verify-token",
      "META_WEBHOOK_VERIFY_TOKEN"
    ),
    fetchSecretValue(
      secretClient,
      gcpProjectId,
      process.env.SECRET_META_WABA_ID ?? "reachhub-meta-waba-id",
      "META_WABA_ID"
    ),
    fetchSecretValue(
      secretClient,
      gcpProjectId,
      process.env.SECRET_META_PHONE_MAP_JSON ?? "reachhub-meta-phone-map-json",
      "META_PHONE_MAP_JSON"
    ),
    fetchSecretValue(
      secretClient,
      gcpProjectId,
      process.env.SECRET_BOOTSTRAP_REGISTRATION_TOKEN ?? "reachhub-bootstrap-registration-token",
      "REACHHUB_BOOTSTRAP_TOKEN"
    ),
  ]);

  let phoneNumberIdsByRef: Record<string, string> = {};
  if (phoneMapJson) {
    try {
      const parsed = JSON.parse(phoneMapJson) as Record<string, unknown>;
      for (const [k, v] of Object.entries(parsed)) {
        if (typeof v === "string" && v.trim()) {
          phoneNumberIdsByRef[k.trim()] = v.trim();
        }
      }
    } catch {
      phoneNumberIdsByRef = {};
    }
  }

  return {
    port: Number(process.env.PORT ?? "8080"),
    gcpProjectId,
    metaGraphVersion: (process.env.META_GRAPH_VERSION ?? "v21.0").trim(),
    cloudTasksConfigured: process.env.CLOUD_TASKS_CONFIGURED !== "false",
    allowedSkewSeconds: Number(process.env.ALLOWED_TIMESTAMP_SKEW_SECONDS ?? "300"),
    rateLimitMaxPerMinute: Number(process.env.RATE_LIMIT_MAX_REQUESTS_PER_MINUTE ?? "240"),
    firestorePrefix: (process.env.FIRESTORE_COLLECTION_PREFIX ?? "reachhub").trim(),
    useInMemoryStore,
    secrets: {
      metaAccessToken,
      metaAppSecret,
      metaWebhookVerifyToken,
      metaWabaId,
      phoneNumberIdsByRef,
      bootstrapRegistrationToken,
    },
  };
}
