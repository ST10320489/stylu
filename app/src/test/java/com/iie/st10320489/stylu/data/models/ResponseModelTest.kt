package com.iie.st10320489.stylu.data.models

import com.google.common.truth.Truth.assertThat
import com.iie.st10320489.stylu.data.models.response.*
import org.junit.Test

class ResponseModelTest {

    @Test
    fun backgroundRemovalResponse_success_createsCorrectly() {
        // Given & When
        val response = BackgroundRemovalResponse(
            success = true,
            imageUrl = "https://example.com/image.jpg",
            error = null
        )

        // Then
        assertThat(response.success).isTrue()
        assertThat(response.imageUrl).isNotNull()
        assertThat(response.error).isNull()
    }

    @Test
    fun backgroundRemovalResponse_failure_createsCorrectly() {
        // Given & When
        val response = BackgroundRemovalResponse(
            success = false,
            imageUrl = null,
            error = "Failed to remove background"
        )

        // Then
        assertThat(response.success).isFalse()
        assertThat(response.imageUrl).isNull()
        assertThat(response.error).isNotNull()
    }

    @Test
    fun imageUploadResponse_success_createsCorrectly() {
        // Given & When
        val response = ImageUploadResponse(
            success = true,
            imageUrl = "https://example.com/uploaded.jpg",
            error = null
        )

        // Then
        assertThat(response.success).isTrue()
        assertThat(response.imageUrl).isEqualTo("https://example.com/uploaded.jpg")
        assertThat(response.error).isNull()
    }

    @Test
    fun imageUploadResponse_failure_createsCorrectly() {
        // Given & When
        val response = ImageUploadResponse(
            success = false,
            imageUrl = null,
            error = "Upload failed"
        )

        // Then
        assertThat(response.success).isFalse()
        assertThat(response.imageUrl).isNull()
        assertThat(response.error).isEqualTo("Upload failed")
    }

    @Test
    fun categoryResponse_withData_createsCorrectly() {
        // Given & When
        val response = CategoryResponse(
            success = true,
            data = emptyList()
        )

        // Then
        assertThat(response.success).isTrue()
        assertThat(response.data).isNotNull()
        assertThat(response.data).isEmpty()
    }

    @Test
    fun itemResponse_success_createsCorrectly() {
        // Given & When
        val response = ItemResponse(
            success = true,
            message = "Item created successfully",
            data = null
        )

        // Then
        assertThat(response.success).isTrue()
        assertThat(response.message).isEqualTo("Item created successfully")
    }
}