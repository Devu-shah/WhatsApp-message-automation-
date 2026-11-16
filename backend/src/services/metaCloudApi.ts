export interface MetaSendResult {
  status: "SUBMITTED" | "FAILED";
  whatsappMessageId: string | null;
  errorCode: string | null;
  errorDescription: string | null;
  isPermanentError: boolean;
  apiResponseSummary: string;
}

const PERMANENT_ERROR_CODES = new Set([
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

export function classifyMetaError(code: string | null, message: string | null): {
  code: string;
  isPermanent: boolean;
  summary: string;
} {
  const cleanCode = (code ?? "UNKNOWN").trim();
  const isPermanent = PERMANENT_ERROR_CODES.has(cleanCode);
  return {
    code: cleanCode,
    isPermanent,
    summary: (message ?? (isPermanent ? "Permanent Meta Cloud API error" : "Temporary delivery failure")).slice(0, 240),
  };
}

export async function uploadMediaToMeta(params: {
  graphVersion: string;
  phoneNumberId: string;
  accessToken: string;
  fileBytes: Buffer;
  mimeType: string;
  filename: string;
}): Promise<string> {
  const endpoint = `https://graph.facebook.com/${params.graphVersion}/${params.phoneNumberId}/media`;
  const form = new FormData();
  form.append("messaging_product", "whatsapp");
  form.append("type", params.mimeType);
  const blob = new Blob([new Uint8Array(params.fileBytes)], { type: params.mimeType });
  form.append("file", blob, params.filename);

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${params.accessToken}`,
    },
    body: form,
  });

  const payload = (await response.json()) as { id?: string; error?: { message?: string; code?: number } };
  if (!response.ok || !payload.id) {
    throw new Error(
      `Meta media upload failed (${response.status}): ${payload.error?.message ?? "Missing media ID"}`
    );
  }
  return payload.id;
}

export async function sendTemplateMessageToMeta(params: {
  graphVersion: string;
  phoneNumberId: string;
  accessToken: string;
  recipientPhoneE164: string;
  metaTemplateName: string;
  languageCode: string;
  positionalParameters: string[];
  metaMediaId?: string | null;
}): Promise<MetaSendResult> {
  const endpoint = `https://graph.facebook.com/${params.graphVersion}/${params.phoneNumberId}/messages`;
  const components: Array<Record<string, unknown>> = [];

  if (params.metaMediaId) {
    components.push({
      type: "header",
      parameters: [
        {
          type: "image",
          image: { id: params.metaMediaId },
        },
      ],
    });
  }

  if (params.positionalParameters.length > 0) {
    components.push({
      type: "body",
      parameters: params.positionalParameters.map((text) => ({
        type: "text",
        text,
      })),
    });
  }

  const body = {
    messaging_product: "whatsapp",
    recipient_type: "individual",
    to: params.recipientPhoneE164.replace(/^\+/, ""),
    type: "template",
    template: {
      name: params.metaTemplateName,
      language: { code: params.languageCode },
      ...(components.length > 0 ? { components } : {}),
    },
  };

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${params.accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });

    const data = (await response.json()) as {
      messages?: Array<{ id?: string }>;
      error?: { code?: number | string; message?: string; error_data?: { details?: string } };
    };

    const wamid = data.messages?.[0]?.id ?? null;
    if (response.ok && wamid) {
      return {
        status: "SUBMITTED",
        whatsappMessageId: wamid,
        errorCode: null,
        errorDescription: null,
        isPermanentError: false,
        apiResponseSummary: `HTTP ${response.status} accepted`,
      };
    }

    const rawCode =
      data.error?.code !== undefined ? String(data.error.code) : String(response.status);
    const rawMsg = data.error?.error_data?.details ?? data.error?.message ?? `HTTP ${response.status}`;
    const classified = classifyMetaError(rawCode, rawMsg);

    return {
      status: "FAILED",
      whatsappMessageId: null,
      errorCode: classified.code,
      errorDescription: classified.summary,
      isPermanentError: classified.isPermanent,
      apiResponseSummary: `HTTP ${response.status} error ${classified.code}`,
    };
  } catch (err) {
    const msg = err instanceof Error ? err.message : "Network dispatch failure";
    return {
      status: "FAILED",
      whatsappMessageId: null,
      errorCode: "NETWORK_IO",
      errorDescription: msg.slice(0, 240),
      isPermanentError: false,
      apiResponseSummary: "NETWORK_IO_RETRYABLE",
    };
  }
}
