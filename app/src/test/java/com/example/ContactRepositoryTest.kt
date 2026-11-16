package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.ReachHubDatabase
import com.example.data.repository.ContactRepository
import com.example.domain.model.ConsentStatus
import com.example.domain.model.CsvRowStatus
import com.example.domain.util.CsvParserAndSecurity
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
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
class ContactRepositoryTest {

  private lateinit var db: ReachHubDatabase
  private lateinit var repository: ContactRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, ReachHubDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repository = ContactRepository(db.contactDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `commits selective CSV rows with custom fields and blocks suppressed numbers`() = runTest {
    repository
      .addSuppressionNumber(
        rawPhone = "9999988888",
        reason = "Opted out",
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
      )
      .getOrThrow()

    val csv =
      "Name,Phone,College,City,Department\n" +
        "Ananya Rao,9876543210,IIT Bombay,Mumbai,Computer Science\n" +
        "Blocked User,9999988888,IIT Delhi,Delhi,Electrical\n"

    val table =
      CsvParserAndSecurity.parseCsvStream(
          ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        )
        .getOrThrow()
    val mapping = CsvParserAndSecurity.autoDetectMapping(table.headers)
    val (existing, suppressed) = repository.getExistingAndSuppressedPhones()
    val rows =
      CsvParserAndSecurity.evaluateRows(
        table = table,
        mapping = mapping,
        defaultCountryCode = "+91",
        autoApplyDefaultCode = true,
        existingPhonesInDb = existing,
        suppressedPhones = suppressed,
      )

    val commitResult = repository.commitCsvImport(rows.filter { it.selectedForImport }, false)
    assertEquals(1, commitResult.insertedCount)

    val saved = repository.contactsWithFields.first()
    assertEquals(1, saved.size)
    assertEquals("Ananya Rao", saved.first().name)
    assertEquals("+919876543210", saved.first().phoneE164)
    assertEquals("Computer Science", saved.first().customFields["Department"])
  }

  @Test
  fun `toggles opt out and removes suppression cleanly`() = runTest {
    val savedId =
      repository
        .saveContact(
          id = 0,
          name = "Kabir Patel",
          rawPhone = "9820012345",
          email = "kabir@example.com",
          college = "DAIICT",
          city = "Gandhinagar",
          state = "Gujarat",
          consentStatus = ConsentStatus.GRANTED,
          isOptedOut = false,
          customFields = mapOf("Cohort" to "2026"),
          defaultCountryCode = "+91",
          autoApplyDefaultCode = true,
        )
        .getOrThrow()

    val contact = repository.contactsWithFields.first().first { it.id == savedId }
    repository.setOptOut(contact.id, contact.phoneE164, optedOut = true)

    assertTrue(repository.contactsWithFields.first().first().isOptedOut)
    assertEquals(1, repository.suppressionList.first().size)

    repository.removeSuppression(contact.phoneE164)
    assertFalse(repository.contactsWithFields.first().first().isOptedOut)
    assertEquals(0, repository.suppressionList.first().size)
  }
}
