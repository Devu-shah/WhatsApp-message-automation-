package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.db.ReachHubDatabase
import com.example.data.local.db.entity.CampaignEntity
import com.example.data.repository.ContactRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.TemplateRepository
import com.example.domain.model.ConsentStatus
import com.example.domain.model.ContactImportStatus
import com.example.domain.model.TemplateParameterMapping
import com.example.domain.model.TemplateValidationStatus
import com.example.domain.util.CsvParserAndSecurity
import com.example.domain.util.TemplateEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
class TemplateAndMediaRepositoryTest {

  private lateinit var context: Context
  private lateinit var db: ReachHubDatabase
  private lateinit var templateRepository: TemplateRepository
  private lateinit var mediaRepository: MediaRepository
  private lateinit var contactRepository: ContactRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, ReachHubDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    templateRepository = TemplateRepository(db.templateDao())
    mediaRepository = MediaRepository(db.mediaDao())
    contactRepository = ContactRepository(db.contactDao())
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `saves duplicates and prevents deleting campaign-referenced template`() = runTest {
    val fields = TemplateEngine.standardContactFieldKeys
    val body = "Hi {Name}, join us in {City}!"
    val mappings = TemplateEngine.syncMappings(body, emptyList(), fields)

    val id =
      templateRepository
        .saveTemplate(
          id = 0,
          name = "City Roadshow",
          metaTemplateName = "city_roadshow_invite",
          languageCode = "en",
          category = "MARKETING",
          bodyText = body,
          mappings = mappings,
          headerMediaRequired = true,
          availableFields = fields,
        )
        .getOrThrow()

    val saved = templateRepository.templates.first().first { it.id == id }
    assertEquals(TemplateValidationStatus.VALID, saved.validationStatus)
    assertEquals(listOf("Name", "City"), saved.placeholders)

    val copy1 = templateRepository.duplicateTemplate(saved).getOrThrow()
    val copy2 = templateRepository.duplicateTemplate(saved).getOrThrow()
    val allNames = templateRepository.templates.first().map { it.name }.toSet()
    assertTrue(allNames.contains("City Roadshow (Copy)"))
    assertTrue(allNames.contains("City Roadshow (Copy 2)"))

    // Reference first template in a campaign -> cannot delete
    db.campaignDao()
      .upsertCampaign(
        CampaignEntity(
          name = "Roadshow Wave 1",
          templateId = id,
        )
      )
    assertTrue(templateRepository.deleteTemplate(id).isFailure)

    // Copies are unreferenced -> can delete cleanly
    assertTrue(templateRepository.deleteTemplate(copy1).isSuccess)
    assertTrue(templateRepository.deleteTemplate(copy2).isSuccess)
  }

  @Test
  fun `imports verified PNG flyer, deduplicates SHA-256, rejects invalid bytes, and cleans up on delete`() =
    runTest {
      val filesDir = File(context.cacheDir, "test_flyers_${System.nanoTime()}").apply { mkdirs() }
      try {
        val validPngBytes = ByteArrayOutputStream().use { out ->
          val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
          bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
          bmp.recycle()
          out.toByteArray()
        }
        val validFile = File(filesDir, "source_flyer.png").apply { writeBytes(validPngBytes) }

        val (firstAsset, firstDedup) =
          mediaRepository
            .importFlyerFromUri(
              contentResolver = context.contentResolver,
              filesDir = filesDir,
              sourceUri = Uri.fromFile(validFile),
              customDisplayName = "Roadshow Flyer.png",
              captionNote = "Main event banner",
            )
            .getOrThrow()

        assertFalse(firstDedup)
        assertEquals("image/png", firstAsset.mimeType)
        assertEquals(16, firstAsset.widthPx)
        assertEquals(16, firstAsset.heightPx)

        // Importing identical content from another file deduplicates to the same DB asset
        val duplicateFile = File(filesDir, "second_copy.png").apply { writeBytes(validPngBytes) }
        val (secondAsset, secondDedup) =
          mediaRepository
            .importFlyerFromUri(
              contentResolver = context.contentResolver,
              filesDir = filesDir,
              sourceUri = Uri.fromFile(duplicateFile),
            )
            .getOrThrow()

        assertTrue(secondDedup)
        assertEquals(firstAsset.id, secondAsset.id)

        // Spoofed file disguised with .jpg extension must fail magic-byte validation
        val spoofedFile =
          File(filesDir, "malicious.jpg").apply {
            writeText("Not really a jpeg image payload")
          }
        val badImport =
          mediaRepository.importFlyerFromUri(
            contentResolver = context.contentResolver,
            filesDir = filesDir,
            sourceUri = Uri.fromFile(spoofedFile),
          )
        assertTrue(badImport.isFailure)

        // Metadata update & deletion
        mediaRepository.updateFlyerMetadata(firstAsset.id, "Updated Flyer.png", "Updated note")
        val updated = mediaRepository.mediaAssets.first().first { it.id == firstAsset.id }
        assertEquals("Updated Flyer.png", updated.displayName)
        assertEquals("Updated note", updated.captionNote)

        assertTrue(mediaRepository.deleteFlyer(updated).isSuccess)
        assertTrue(mediaRepository.mediaAssets.first().isEmpty())
      } finally {
        filesDir.deleteRecursively()
      }
    }

  @Test
  fun `tracks CSV_IMPORTED CSV_UPDATED and MANUAL status and preserves CSV status on edit`() =
    runTest {
      val csv1 = "Name,Phone,City\nRohan Verma,9876543210,Pune\n"
      val table1 =
        CsvParserAndSecurity.parseCsvStream(
            ByteArrayInputStream(csv1.toByteArray(StandardCharsets.UTF_8))
          )
          .getOrThrow()
      val rows1 =
        CsvParserAndSecurity.evaluateRows(
          table = table1,
          mapping = CsvParserAndSecurity.autoDetectMapping(table1.headers),
          defaultCountryCode = "+91",
          autoApplyDefaultCode = true,
          existingPhonesInDb = emptySet(),
          suppressedPhones = emptySet(),
        )
      contactRepository.commitCsvImport(rows1, updateExistingDuplicates = false)

      val importedContact = contactRepository.contactsWithFields.first().single()
      assertEquals(ContactImportStatus.CSV_IMPORTED, importedContact.importStatus)

      // Manual edit of an imported contact must preserve CSV_IMPORTED status
      contactRepository
        .saveContact(
          id = importedContact.id,
          name = "Rohan K. Verma",
          rawPhone = importedContact.rawPhone,
          email = "rohan@example.com",
          college = importedContact.college,
          city = "Mumbai",
          state = importedContact.state,
          consentStatus = ConsentStatus.GRANTED,
          isOptedOut = false,
          customFields = importedContact.customFields,
          defaultCountryCode = "+91",
          autoApplyDefaultCode = true,
        )
        .getOrThrow()

      val afterEdit = contactRepository.contactsWithFields.first().single()
      assertEquals("Rohan K. Verma", afterEdit.name)
      assertEquals(ContactImportStatus.CSV_IMPORTED, afterEdit.importStatus)

      // Re-importing same phone with updateExistingDuplicates = true updates status to CSV_UPDATED
      val (existingPhones, suppressedPhones) = contactRepository.getExistingAndSuppressedPhones()
      val rows2 =
        CsvParserAndSecurity.evaluateRows(
          table = table1,
          mapping = CsvParserAndSecurity.autoDetectMapping(table1.headers),
          defaultCountryCode = "+91",
          autoApplyDefaultCode = true,
          existingPhonesInDb = existingPhones,
          suppressedPhones = suppressedPhones,
        )
      contactRepository.commitCsvImport(rows2, updateExistingDuplicates = true)

      val afterReimport = contactRepository.contactsWithFields.first().single()
      assertEquals(ContactImportStatus.CSV_UPDATED, afterReimport.importStatus)

      // New manual contact has MANUAL status
      val manualId =
        contactRepository
          .saveContact(
            id = 0,
            name = "Manual Entry",
            rawPhone = "9123456789",
            email = null,
            college = null,
            city = null,
            state = null,
            consentStatus = ConsentStatus.GRANTED,
            isOptedOut = false,
            customFields = emptyMap(),
            defaultCountryCode = "+91",
            autoApplyDefaultCode = true,
          )
          .getOrThrow()
      val manualContact = contactRepository.contactsWithFields.first().first { it.id == manualId }
      assertEquals(ContactImportStatus.MANUAL, manualContact.importStatus)
    }

  @Test
  fun `migration 1 to 2 adds new columns with safe defaults`() {
    val dbFile = File(context.cacheDir, "migration_test_${System.nanoTime()}.db")
    try {
      // Create v1 schema file directly via SQLiteDatabase
      val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
      v1Db.execSQL(
        """
        CREATE TABLE contacts (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL,
          phoneE164 TEXT NOT NULL,
          rawPhone TEXT NOT NULL,
          email TEXT,
          college TEXT,
          city TEXT,
          state TEXT,
          consentStatus TEXT NOT NULL,
          isOptedOut INTEGER NOT NULL,
          importedAt INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL(
        """
        CREATE TABLE media_assets (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          displayName TEXT NOT NULL,
          localUri TEXT NOT NULL,
          mimeType TEXT NOT NULL,
          byteSize INTEGER NOT NULL,
          sha256Hash TEXT NOT NULL,
          metaMediaId TEXT,
          uploadStatus TEXT NOT NULL,
          uploadedAt INTEGER,
          createdAt INTEGER NOT NULL
        )
        """.trimIndent()
      )
      v1Db.execSQL(
        "INSERT INTO contacts (name, phoneE164, rawPhone, consentStatus, isOptedOut, importedAt) VALUES ('Old Contact', '+919800000001', '9800000001', 'GRANTED', 0, 1000)"
      )
      v1Db.execSQL(
        "INSERT INTO media_assets (displayName, localUri, mimeType, byteSize, sha256Hash, uploadStatus, createdAt) VALUES ('old.png', 'file:///old.png', 'image/png', 100, 'abc123', 'LOCAL_ONLY', 1000)"
      )
      v1Db.version = 1
      v1Db.close()

      val openHelper =
        FrameworkSQLiteOpenHelperFactory()
          .create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
              .name(dbFile.absolutePath)
              .callback(
                object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                  override fun onCreate(db: SupportSQLiteDatabase) {}

                  override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                  ) {
                    ReachHubDatabase.MIGRATION_1_2.migrate(db)
                  }
                }
              )
              .build()
          )

      val migrated = openHelper.writableDatabase
      migrated.query("SELECT importStatus FROM contacts WHERE name = 'Old Contact'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("MANUAL", cursor.getString(0))
      }
      migrated.query("SELECT widthPx, heightPx, captionNote FROM media_assets WHERE displayName = 'old.png'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        assertEquals(0, cursor.getInt(1))
        assertTrue(cursor.isNull(2))
      }
      openHelper.close()
    } finally {
      dbFile.delete()
    }
  }
}
