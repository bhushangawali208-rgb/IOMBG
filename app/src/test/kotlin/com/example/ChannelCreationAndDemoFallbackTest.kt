package com.example

import com.example.data.model.Channel
import com.example.data.remote.FirebaseService
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration tests for Channel Creation, Ownership, and Demo Fallback Prevention.
 *
 * Verifies:
 * 1. Authenticated user with no channel starts with null channel (no demo fallback).
 * 2. Calling setDemoChannel() for an authenticated user is blocked.
 * 3. Real channel creation assigns the authenticated user's UID as ownerUid.
 * 4. Newly created real channel is loaded into ChannelRepository / Creator Studio.
 * 5. Existing Firestore channel is loaded correctly without overwrite or demo injection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelCreationAndDemoFallbackTest {

    private lateinit var fakeFirebaseService: FakeFirebaseService
    private lateinit var channelRepository: ChannelRepository

    class FakeFirebaseService : FirebaseService() {
        val channelsInFirestore = mutableMapOf<String, Channel>()

        override suspend fun isHandleAvailable(handle: String): Boolean {
            val clean = if (handle.startsWith("@")) handle.lowercase() else "@${handle.lowercase()}"
            return channelsInFirestore.values.none { it.handle.lowercase() == clean }
        }

        override suspend fun getChannelByOwnerUid(ownerUid: String): Channel? {
            return channelsInFirestore.values.firstOrNull { it.ownerUid == ownerUid }
        }

        override suspend fun createChannel(channel: Channel): Boolean {
            channelsInFirestore[channel.channelId] = channel
            return true
        }
    }

    @Before
    fun setup() {
        fakeFirebaseService = FakeFirebaseService()
        channelRepository = ChannelRepository(fakeFirebaseService)
    }

    @Test
    fun testAuthenticatedUserWithNoChannel_hasNullChannel_andNoDemoAssigned() = runBlocking {
        val realUserUid = "real_firebase_user_999"

        val loaded = channelRepository.loadChannelForUser(realUserUid)
        assertNull("Authenticated user without a Firestore channel must return null", loaded)
        assertNull("currentChannel must be null when user has no channel", channelRepository.currentChannel.value)
        assertNotEquals("Nexus Media Studios", channelRepository.currentChannel.value?.channelName)
        assertNotEquals("ch_01", channelRepository.currentChannel.value?.channelId)
    }

    @Test
    fun testSetDemoChannel_blockedForAuthenticatedUser() {
        val realUserUid = "real_firebase_user_999"

        channelRepository.setDemoChannel(callerUid = realUserUid)

        assertNull("setDemoChannel must be rejected for real authenticated user", channelRepository.currentChannel.value)
        assertNotEquals("Nexus Media Studios", channelRepository.currentChannel.value?.channelName)
    }

    @Test
    fun testRealChannelCreation_setsOwnerUidAndLoadsIntoCurrentChannel() = runBlocking {
        val realUserUid = "real_firebase_user_999"
        val channelName = "Aura Studio"
        val handle = "@aurastudio"
        val description = "High quality tech content"

        val created = channelRepository.createChannel(
            ownerUid = realUserUid,
            name = channelName,
            handle = handle,
            description = description,
            category = "Technology",
            profileImageUrl = "https://example.com/avatar.jpg",
            bannerImageUrl = "https://example.com/banner.jpg"
        )

        assertNotNull("Channel creation must succeed", created)
        assertEquals("ownerUid must match authenticated user's UID", realUserUid, created!!.ownerUid)
        assertEquals("Channel name must match input", channelName, created.channelName)
        assertEquals("Handle must match input", handle, created.handle)
        assertEquals("Description must match input", description, created.description)

        // Verify Firestore stored the real channel document
        val inFirestore = fakeFirebaseService.channelsInFirestore[created.channelId]
        assertNotNull("Channel document must exist in Firestore", inFirestore)
        assertEquals("Firestore document ownerUid must match user UID", realUserUid, inFirestore!!.ownerUid)

        // Verify currentChannel is updated to the newly created real channel
        assertEquals("currentChannel must reflect the new real channel", created.channelId, channelRepository.currentChannel.value?.channelId)
        assertNotEquals("Nexus Media Studios", channelRepository.currentChannel.value?.channelName)
        assertNotEquals("ch_01", channelRepository.currentChannel.value?.channelId)
    }

    @Test
    fun testExistingChannelLoading_preservesRealChannelWithoutDemoFallback() = runBlocking {
        val realUserUid = "real_firebase_user_777"
        val existingChannel = Channel(
            channelId = "ch_real_777",
            ownerUid = realUserUid,
            channelName = "Solaris Productions",
            handle = "@solaris",
            description = "Cinema & documentaries",
            subscriberCount = 1200
        )
        fakeFirebaseService.channelsInFirestore[existingChannel.channelId] = existingChannel

        val loaded = channelRepository.loadChannelForUser(realUserUid)

        assertNotNull("Existing channel must load", loaded)
        assertEquals("Loaded channel ID must match existing channel", "ch_real_777", loaded!!.channelId)
        assertEquals("ownerUid must match authenticated user UID", realUserUid, loaded.ownerUid)
        assertEquals("Current channel must match existing channel", existingChannel, channelRepository.currentChannel.value)
        assertNotEquals("Nexus Media Studios", channelRepository.currentChannel.value?.channelName)
        assertNotEquals("ch_01", channelRepository.currentChannel.value?.channelId)
    }
}
