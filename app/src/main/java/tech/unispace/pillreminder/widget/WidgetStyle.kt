package tech.unispace.pillreminder.widget

/**
 * Цвет и прозрачность виджета — чистая логика без Android, чтобы проверять тестом.
 * Фон виджета — `ImageView` со скруглённым shape: цвет задаётся `setColorFilter`, прозрачность —
 * `setImageAlpha`; оба метода доступны из RemoteViews, а углы при этом не теряются.
 */
object WidgetStyle {
    /** Бирюзовый из палитры приложения — фон по умолчанию. */
    val DEFAULT_COLOR: Int = 0xFF2E7D6F.toInt()

    /** Пресеты фона; подписи — `S.widgetColorNames` по тому же индексу. */
    val presets: List<Int> = listOf(0xFF2E7D6F, 0xFF1A2422, 0xFFF4F8F6, 0xFFFFFFFF, 0xFF000000).map { it.toInt() }

    const val TEXT_AUTO = "auto"
    const val TEXT_LIGHT = "light"
    const val TEXT_DARK = "dark"

    /** Относительная яркость 0…1 (sRGB без гамма-коррекции — для выбора цвета текста хватает). */
    fun luminance(argb: Int): Double {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Нужен ли светлый текст: явный режим, иначе — по яркости фона. */
    fun lightText(background: Int, mode: String): Boolean = when (mode) {
        TEXT_LIGHT -> true
        TEXT_DARK -> false
        else -> luminance(background) < 0.5
    }

    /** Цвет текста: основной или приглушённый, светлый или тёмный. */
    fun textColor(primary: Boolean, light: Boolean): Int = when {
        light && primary -> 0xFFFFFFFF.toInt()
        light -> 0xE6FFFFFF.toInt()
        primary -> 0xFF1A201E.toInt()
        else -> 0xCC1A201E.toInt()
    }

    /** Непрозрачность 0–100 % → альфа 0–255 для `ImageView.setImageAlpha`. */
    fun alpha(opacityPercent: Int): Int = opacityPercent.coerceIn(0, 100) * 255 / 100
}
