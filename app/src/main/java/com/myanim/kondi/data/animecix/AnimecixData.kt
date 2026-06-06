package com.myanim.kondi.data.animecix

import com.google.gson.annotations.SerializedName

data class AnimecixAnime(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val title: String,
    @SerializedName("poster") val poster: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("year") val year: Int?,
    @SerializedName("mal_vote_average") val rating: String?,
    @SerializedName("genres") val genres: List<AnimecixGenre>?,
    @SerializedName("season_count") val seasonCount: Int,
    @SerializedName("seasons") val seasons: List<AnimecixSeason>?,
    @SerializedName("videos") val videos: List<AnimecixVideo>?,
    @SerializedName("trailer") val trailerUrl: String?,
    @SerializedName("actors") val credits: List<AnimecixCredit>?
)

data class AnimecixCredit(
    @SerializedName("name") val name: String,
    @SerializedName("poster") val poster: String?
)

data class AnimecixGenre(
    @SerializedName("name") val name: String
)

data class AnimecixSeason(
    @SerializedName("name") val name: String,
    @SerializedName("season_number") val seasonNumber: Int
)

data class AnimecixVideo(
    @SerializedName("episode_number", alternate = ["episode_num"]) val episodeNumber: Int?,
    @SerializedName("season_number", alternate = ["season_num"]) val seasonNumber: Int?,
    @SerializedName("title_poster", alternate = ["poster", "thumbnail"]) val poster: String?,
    @SerializedName("title_name", alternate = ["name"]) val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("url") val directUrl: String?,
    @SerializedName("episode_id") val directEpisodeId: Int?,
    @SerializedName("title_id", alternate = ["anime_id"]) val animeId: Int?,
    @SerializedName("language") val language: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("videos") val videos: List<AnimecixVideoDetail>? = null
) {
    val url: String? get() = directUrl ?: videos?.firstOrNull()?.url
    val episodeId: Int? get() = directEpisodeId ?: videos?.firstOrNull()?.episodeId
}

data class AnimecixVideoDetail(
    @SerializedName("url") val url: String?,
    @SerializedName("episode_id") val episodeId: Int?,
    @SerializedName("name") val name: String? = null
)

data class AnimecixTitle(
    @SerializedName("title_object") val titleObject: AnimecixAnime?,
    @SerializedName("id", alternate = ["title_id", "titleId", "anime_id", "animeId"]) val directId: Int?,
    @SerializedName("name", alternate = ["title_name", "title", "anime_name"]) val directName: String?,
    @SerializedName("poster", alternate = ["title_poster", "anime_poster", "thumbnail"]) val directPoster: String?
) {
    val id: Int? get() = directId ?: titleObject?.id
    val name: String? get() = directName ?: titleObject?.title
    val poster: String? get() = directPoster ?: titleObject?.poster
}

data class AnimecixSource(
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String?,
    @SerializedName("extra") val extra: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("quality") val quality: String? = null
)

data class AnimecixVideoResponse(
    @SerializedName("videos") val videos: List<AnimecixSource>
)
