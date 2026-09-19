package com.example.data.model

data class Voice(
    val id: String,
    val name: String,
    val description: String,
    val gender: String,
    val accent: String,
    val isCloned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        val PRESETS = listOf(
            Voice(
                id = "db6b0ed5-d5d3-463d-ae85-518a07d3c2b4",
                name = "Skylar",
                description = "Warm, natural & friendly conversational voice",
                gender = "Female",
                accent = "en-US"
            ),
            Voice(
                id = "47c38ca4-5f35-497b-b1a3-415245fb35e1",
                name = "Daniel",
                description = "Deep, articulate & cinematic narration",
                gender = "Male",
                accent = "en-US"
            ),
            Voice(
                id = "9626c31c-bec5-4cca-baa8-f8ba9e84c8bc",
                name = "Jacqueline",
                description = "Polished, professional & engaging host",
                gender = "Female",
                accent = "en-US"
            ),
            Voice(
                id = "62ae83ad-4f6a-430b-af41-a9bede9286ca",
                name = "Gemma",
                description = "Sophisticated British female voice",
                gender = "Female",
                accent = "en-GB"
            ),
            Voice(
                id = "ef191366-f52f-447a-a398-ed8c0f2943a1",
                name = "Archie",
                description = "Expressive, confident British male narrator",
                gender = "Male",
                accent = "en-GB"
            )
        )

        val DEFAULT = PRESETS[0]
    }
}
