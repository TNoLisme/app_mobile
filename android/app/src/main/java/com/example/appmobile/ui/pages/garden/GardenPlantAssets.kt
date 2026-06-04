package com.example.appmobile.ui.pages.garden

import androidx.annotation.DrawableRes
import com.example.appmobile.R

object GardenPlantAssets {
    @DrawableRes
    fun assetFor(emotionId: String, level: Int): Int {
        val safeLevel = level.coerceIn(0, 5)
        return when (emotionId.lowercase()) {
            "happy" -> sunflower(safeLevel)
            "sad" -> blueWillow(safeLevel)
            "angry" -> chiliPlant(safeLevel)
            "fear" -> shyPlant(safeLevel)
            "surprise" -> surpriseTulip(safeLevel)
            "disgust" -> pitcherPlant(safeLevel)
            else -> sunflower(safeLevel)
        }
    }

    @DrawableRes
    private fun sunflower(level: Int) = when (level) {
        0 -> R.drawable.garden_sunflower_level_0
        1 -> R.drawable.garden_sunflower_level_1
        2 -> R.drawable.garden_sunflower_level_2
        3 -> R.drawable.garden_sunflower_level_3
        4 -> R.drawable.garden_sunflower_level_4
        else -> R.drawable.garden_sunflower_level_5
    }

    @DrawableRes
    private fun blueWillow(level: Int) = when (level) {
        0 -> R.drawable.garden_blue_willow_level_0
        1 -> R.drawable.garden_blue_willow_level_1
        2 -> R.drawable.garden_blue_willow_level_2
        3 -> R.drawable.garden_blue_willow_level_3
        4 -> R.drawable.garden_blue_willow_level_4
        else -> R.drawable.garden_blue_willow_level_5
    }

    @DrawableRes
    private fun chiliPlant(level: Int) = when (level) {
        0 -> R.drawable.garden_chili_plant_level_0
        1 -> R.drawable.garden_chili_plant_level_1
        2 -> R.drawable.garden_chili_plant_level_2
        3 -> R.drawable.garden_chili_plant_level_3
        4 -> R.drawable.garden_chili_plant_level_4
        else -> R.drawable.garden_chili_plant_level_5
    }

    @DrawableRes
    private fun shyPlant(level: Int) = when (level) {
        0 -> R.drawable.garden_shy_plant_level_0
        1 -> R.drawable.garden_shy_plant_level_1
        2 -> R.drawable.garden_shy_plant_level_2
        3 -> R.drawable.garden_shy_plant_level_3
        4 -> R.drawable.garden_shy_plant_level_4
        else -> R.drawable.garden_shy_plant_level_5
    }

    @DrawableRes
    private fun surpriseTulip(level: Int) = when (level) {
        0 -> R.drawable.garden_surprise_tulip_level_0
        1 -> R.drawable.garden_surprise_tulip_level_1
        2 -> R.drawable.garden_surprise_tulip_level_2
        3 -> R.drawable.garden_surprise_tulip_level_3
        4 -> R.drawable.garden_surprise_tulip_level_4
        else -> R.drawable.garden_surprise_tulip_level_5
    }

    @DrawableRes
    private fun pitcherPlant(level: Int) = when (level) {
        0 -> R.drawable.garden_pitcher_plant_level_0
        1 -> R.drawable.garden_pitcher_plant_level_1
        2 -> R.drawable.garden_pitcher_plant_level_2
        3 -> R.drawable.garden_pitcher_plant_level_3
        4 -> R.drawable.garden_pitcher_plant_level_4
        else -> R.drawable.garden_pitcher_plant_level_5
    }
}
