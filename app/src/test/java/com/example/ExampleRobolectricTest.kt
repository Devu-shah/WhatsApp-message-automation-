package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.ReachHubDatabase
import com.example.data.local.db.entity.ContactEntity
import com.example.data.local.db.entity.SenderProfileEntity
import com.example.data.local.db.entity.SuppressionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: ReachHubDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, ReachHubDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `app name matches ReachHub`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ReachHub", appName)
  }

  @Test
  fun `sender profile default switch keeps single active sender`() = runTest {
    val dao = db.senderProfileDao()
    val firstId =
      dao.upsertSenderProfile(
        SenderProfileEntity(
          label = "Primary Desk",
          phoneNumberE164 = "+919876543210",
          cloudRunProfileRef = "desk_1",
          isDefault = true,
        )
      )
    val secondId =
      dao.upsertSenderProfile(
        SenderProfileEntity(
          label = "Support Line",
          phoneNumberE164 = "+919123456789",
          cloudRunProfileRef = "desk_2",
          isDefault = false,
        )
      )

    dao.setDefaultProfile(secondId)
    val active = dao.observeDefaultSenderProfile().first()
    val all = dao.observeAllSenderProfiles().first()

    assertEquals(secondId, active?.id)
    assertFalse(all.first { it.id == firstId }.isDefault)
    assertTrue(all.first { it.id == secondId }.isDefault)
  }

  @Test
  fun `dashboard counts and suppression lookup reflect real room writes`() = runTest {
    val contactDao = db.contactDao()
    val dashboardDao = db.dashboardDao()

    contactDao.upsertContact(
      ContactEntity(
        name = "Aarav Mehta",
        phoneE164 = "+919876543210",
        rawPhone = "9876543210",
        isOptedOut = false,
      )
    )
    contactDao.upsertContact(
      ContactEntity(
        name = "Priya Nair",
        phoneE164 = "+919811122233",
        rawPhone = "+919811122233",
        isOptedOut = true,
      )
    )
    contactDao.addSuppression(
      SuppressionEntity(
        phoneE164 = "+919811122233",
        reason = "STOP reply",
        source = "operator",
      )
    )

    assertEquals(2, dashboardDao.observeTotalContacts().first())
    assertEquals(1, dashboardDao.observeOptedOutContacts().first())
    assertTrue(contactDao.isSuppressed("+919811122233"))
    assertFalse(contactDao.isSuppressed("+919876543210"))
  }
}
