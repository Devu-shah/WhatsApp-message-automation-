package com.example.domain.model

data class DashboardStats(
  val totalContacts: Int = 0,
  val optedOutContacts: Int = 0,
  val totalCampaigns: Int = 0,
  val activeCampaigns: Int = 0,
  val completedCampaigns: Int = 0,
  val totalMessagesSubmitted: Int = 0,
  val messagesDelivered: Int = 0,
  val messagesRead: Int = 0,
  val failedMessages: Int = 0,
  val totalTemplates: Int = 0,
  val totalMediaAssets: Int = 0,
)

data class RecentCampaignSummary(
  val id: Long,
  val name: String,
  val status: CampaignStatus,
  val mode: AppMode,
  val senderPhoneNumber: String,
  val createdAt: Long,
)

data class RecentMessageActivity(
  val id: Long,
  val campaignId: Long,
  val recipientName: String,
  val recipientPhoneMasked: String,
  val status: MessageStatus,
  val updatedAt: Long,
)
