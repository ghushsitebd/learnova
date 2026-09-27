package com.learnova.app

data class ChildAvatarProfile(
    val id: String,
    val ageBand: String,
    val outfit: String,
    val headwear: String? = null,
    val animationSet: String = "child_natural",
    val voiceRole: String
)

object ChildAvatarCatalog {
    val defaultProfiles = listOf(
        ChildAvatarProfile("boy_panjabi_cap", "young_child", "panjabi", "cap", voiceRole = "child_a"),
        ChildAvatarProfile("boy_panjabi_cap_02", "young_child", "panjabi", "cap", voiceRole = "child_b"),
        ChildAvatarProfile("girl_modest", "young_child", "modest", voiceRole = "child_c")
    )
}
