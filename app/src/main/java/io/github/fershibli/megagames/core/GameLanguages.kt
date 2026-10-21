package io.github.fershibli.megagames.core

/**
 * Language decisions for games that cannot be played in every language (see [GameId.languages]).
 * Plain functions over a set of languages, so they work (and are tested) with any set; `null`
 * always means "every language".
 */
object GameLanguages {
    /** True when a game limited to [languages] can be played in [language]. */
    fun supports(languages: Set<AppLanguage>?, language: AppLanguage): Boolean =
        languages == null || language.resolve() in languages

    /**
     * The languages offered by the "not available in your language" dialog, in the order of the
     * Settings list with the one picked last time for this game ([remembered]) first; `null` when
     * the game simply opens in the app language and no dialog is needed.
     */
    fun fallbackChoices(languages: Set<AppLanguage>?, app: AppLanguage, remembered: AppLanguage?): List<AppLanguage>? {
        if (languages == null || supports(languages, app)) return null
        val offered = AppLanguage.entries.filter { it != AppLanguage.SYSTEM && it in languages }
        val first = remembered?.takeIf { it in offered } ?: return offered
        return listOf(first) + (offered - first)
    }

    /**
     * Language a game plays in when it starts from the menu without asking: games with every
     * language follow the app language (`null`), the others are pinned to it so a saved game
     * keeps it even if the app language changes later.
     */
    fun opening(languages: Set<AppLanguage>?, app: AppLanguage): AppLanguage? =
        if (languages == null) null else app.resolve()

    /**
     * Language of a restored game: the one it was [saved] with while the game still supports it;
     * old saves (without a language) of a limited game fall back to the app language when
     * possible, otherwise to its first language.
     */
    fun restored(languages: Set<AppLanguage>?, app: AppLanguage, saved: AppLanguage?): AppLanguage? {
        if (languages == null) return saved
        return saved?.takeIf { it in languages }
            ?: app.resolve().takeIf { it in languages }
            ?: AppLanguage.entries.firstOrNull { it in languages }
    }

    /** Parses the language stored in a save file or in the remembered choices. */
    fun fromName(name: String?): AppLanguage? =
        AppLanguage.entries.firstOrNull { it.name == name && it != AppLanguage.SYSTEM }
}
